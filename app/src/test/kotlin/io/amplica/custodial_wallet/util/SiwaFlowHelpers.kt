package io.amplica.custodial_wallet.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.playwright.Page
import com.microsoft.playwright.Response
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.amplica.custodial_wallet.client.redis.CustodialWalletRedisClient
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.container.FrequencyTestContainer
import io.amplica.custodial_wallet.container.FrequencyTestProvider
import io.amplica.custodial_wallet.dto.SiwaPayloadResponse
import io.amplica.custodial_wallet.dto.UserPayloadsAcceptanceAndDataCommand
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.extension.BrowserEngine
import io.amplica.custodial_wallet.orchestration.siwa.LOCALE
import io.amplica.custodial_wallet.orchestration.siwa.SiwaOrchestrationService
import io.amplica.custodial_wallet.orchestration.siwa.ViewResponse
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import io.amplica.custodial_wallet.web.AUTHORIZATION_CODE_PARAMETER_NAME
import io.amplica.custodial_wallet.web.SIGNED_REQUEST_PARAMETER_NAME
import io.amplica.custodial_wallet.web.USER_KEY_TYPE
import io.amplica.frequency.client.FrequencyClient
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.springframework.http.HttpHeaders
import org.springframework.util.MultiValueMap
import org.springframework.web.util.UriComponentsBuilder

data class UserRegistrationData(
  val userIdentifier: UserIdentifier,
  val userHandle: String,
  val userIdentifierAdminUrl: String,
)

val DEFAULT_PERMISSIONS: List<Int> = PROFILE_SCHEMA_IDS + GRAPH_SCHEMA_IDS + BROADCAST_SCHEMA_IDS + TOMBSTONE_SCHEMA_IDS + REPLY_SCHEMA_IDS + REACTION_SCHEMA_IDS + UPDATE_SCHEMA_IDS

/**
 * Registers a new user through siwa using the siwa orchestration layer. This is not E2E
 * testing a siwa sign up. This is a shortcut for testing non-playwright/non-siwa tests
 */
suspend fun siwaRegisterNewUserOrchestration(
  siwaOrchestrationService: SiwaOrchestrationService,
  frequencyTestContainer: FrequencyTestContainer,
  redisClient: CustodialWalletRedisClient,
  userRegistrationData: UserRegistrationData
): SiwaPayloadResponse {
  val providerRequest = createProviderSiwaRequest(
    frequencyTestContainer.wsAddress,
    SubstrateOrAccountKeyPair.SubstrateKeyPairWrapper(frequencyTestContainer.aliceKeyPair),
    DEFAULT_PERMISSIONS,
    "http://localhost",
    userRegistrationData.userIdentifierAdminUrl
  )
  val startResponse = siwaOrchestrationService.acceptSiwaRequest(providerRequest, null, null)
  val unauthenticatedSessionId = startResponse.sessionId!!
  val identifier = SiwaIdentifierAndCaptchaToken(
    userRegistrationData.userIdentifier.value,
    userRegistrationData.userIdentifier.type,
    null
  )
  siwaOrchestrationService.acceptUserIdentifier(unauthenticatedSessionId, identifier, null, null, LOCALE, null)
  val unauthenticatedSiwaSession =
    redisClient.findSiwaSessionBySessionId(unauthenticatedSessionId)?.fold({ it }, { it }) as UnauthenticatedSiwaSession
  val authenticationCode = unauthenticatedSiwaSession.authentication?.currentCode
  val authenticationCodeResponse = siwaOrchestrationService.acceptAuthenticationCode(authenticationCode, unauthenticatedSessionId) as ViewResponse
  val authenticatedSessionId = authenticationCodeResponse.sessionId!!
  siwaOrchestrationService.acceptAcceptanceAndData(
    authenticatedSessionId,
    UserPayloadsAcceptanceAndDataCommand(userRegistrationData.userHandle)
  )
  val authenticatedSiwaSession =
    redisClient.findSiwaSessionBySessionId(authenticatedSessionId)?.fold({ it }, { it }) as AuthenticatedSiwaSession
  return siwaOrchestrationService.retrieveSiwaPayload(authenticatedSiwaSession.authorizationCode!!)
}

/**
 * Registers a new user through siwa using Playwright E2E for thorough testing of the sign-up process
 * Mostly used in the Siwa E2E tests. Other tests just needing registered accounts or not
 * using playwright can use the siwaRegisterNewUserOrchestration as an easier alternative
 */
fun siwaRegisterNewUserE2E(
  page: Page,
  sesUtil: SesUtil,
  host: String,
  apiUtil: ApiUtil,
  objectMapper: ObjectMapper,
  mockNotificationService: MockNotificationService,
  frequencyTestProvider: FrequencyTestProvider,
  providerRequest: SiwaRequest,
  isPasskeyEnabled: Boolean,
  userRegistrationData: UserRegistrationData,
  userKeyPairType: KeyPairType,
  ) {
  setUpSiwaStartPage(page, providerRequest, objectMapper, host, userKeyPairType)

  when (userRegistrationData.userIdentifier.type) {
    UserIdentifierType.EMAIL -> {
      // Fill in user's email
      val emailInput = page.getByTestId("email-identifier-input")
      emailInput.fill(userRegistrationData.userIdentifier.value)

      // Submit user identifier form
      page.waitForResponse("**/siwa/verify") {
        page.getByTestId("email-identifier-submit").click()
      }

      // Retrieve and open email link
      val message = sesUtil.getLatestMessage(userRegistrationData.userIdentifier.value)
      val (token, sessionId, _) = retrieveEmailParams(message)

      page.navigate("$host/siwa/payloads?sessionId=$sessionId&token=$token")
    }

    UserIdentifierType.PHONE_NUMBER -> {
      val sessionId = fillUserPhoneNumberAndSubmit(
        userRegistrationData.userIdentifier.value,
        page,
        userRegistrationData.userIdentifierAdminUrl
      )

      val smsCode = getSmsCodeFromMockNotificationService(
        isLogin = false,
        userRegistrationData.userIdentifier.value,
        mockNotificationService
      )
      Assertions.assertThat(apiUtil.getSiwaTokenForSessionId(sessionId).body!!.token).isEqualTo(smsCode)
      fillAuthenticationCodeAndSubmit(page, smsCode)
    }
  }

  val acceptedResponse = fillPayloadInputsAndSubmit(page, userRegistrationData.userHandle)

  // Should be redirected to provider callback if passkey isn't enabled, else should go to passkey created page
  if(!isPasskeyEnabled) {
    Assertions.assertThat(acceptedResponse.status()).isEqualTo(303)

    // Use the callback URL parameters to fetch the SIWA payload response
    val callback = acceptedResponse.headerValue(HttpHeaders.LOCATION)
    val siwaPayloadResponse = fetchSiwaPayloadResponseForCallback(callback, apiUtil)

    assertNewUserSiwaPayloads(
      userRegistrationData.userHandle,
      providerRequest.signatureRequest.payload.permissions,
      siwaPayloadResponse.payloads
    )

    submitSiwaSignedPayloads(frequencyTestProvider.client, siwaPayloadResponse)
  }
}

fun fillAuthenticationCodeAndSubmit(page: Page, smsCode: String) {
  smsCode.forEachIndexed { index, digit ->
    page.getByTestId("code-input-${index}").fill(digit.toString())
  }

  page.waitForResponse("**/siwa/payloads") {
    page.getByTestId("sms-code-submit").click()
  }
}

fun fetchSiwaPayloadResponseForCallback(callbackUrl: String, apiUtil: ApiUtil): SiwaPayloadResponse {
  val callbackUriComponents = UriComponentsBuilder.fromUriString(callbackUrl).build()
  val signedPayloadResponse = apiUtil.getSiwaPayloadRequest(
    callbackUriComponents.queryParams.getFirst(AUTHORIZATION_CODE_PARAMETER_NAME)!!
  )

  return signedPayloadResponse.body!!
}


fun fillUserPhoneNumberAndSubmit(userPhone: String, page: Page, userIdentifierAdminUrl: String): String {
  // Fill in user's phone number
  if(!page.getByTestId("phone-form").isVisible){
    page.getByTestId("switch-button").click()
  }
  page.getByTestId("phone-form").fill(userPhone)

  val response = page.waitForResponse("**/siwa/verify") {
    page.getByTestId("sms-identifier-submit").click()
  }

  Assertions.assertThat(response.status()).isEqualTo(200)
  Assertions.assertThat(page.getByTestId("user-identifier-admin-url").getAttribute("href")).isEqualTo(userIdentifierAdminUrl)

  return findSessionIdCookie(page.context().cookies()).value
}

fun getSmsCodeFromMockNotificationService(
  isLogin: Boolean = false,
  destinationPhoneNumber: String,
  mockNotificationService: MockNotificationService
): String {
  val request = mockNotificationService.getLastSmsSendRequest()
  Assertions.assertThat(request.destinationPhoneNumber).isEqualTo(destinationPhoneNumber)

  val descriptor = if (isLogin) "login" else "verification"
  Assertions.assertThat(request.messageBody).contains("Your Frequency Access $descriptor code is:")

  return request.messageBody.substringAfter(":").trimStart()
}

fun createProviderSiwaRequest(
  frequencyAddress: String,
  providerKeyPair: SubstrateOrAccountKeyPair,
  permissions: List<Int>,
  callbackUrl: String,
  userIdentifierAdminUrl: String,
  siwaEmailHandling: SiwaEmailHandling? = null,
  applicationContext: ApplicationContext? = ApplicationContext(DbUtil.ALICE_PROVIDER_APPLICATION.verifiedCredentialUrl),
): SiwaRequest {
  return siwaRequest(
    providerKeyPair,
    callbackUrl,
    permissions,
    userIdentifierAdminUrl,
    siwaEmailHandling,
    applicationContext
  )
}

fun setUpSiwaStartPage(
  page: Page,
  providerRequest: SiwaRequest,
  objectMapper: ObjectMapper,
  host: String,
  userKeyPairType: KeyPairType,
  queryParams: Map<String, String> = emptyMap() // E.g., `userHandle`
) {
  val siwaRequestString = base64UrlEncode(objectMapper.writeValueAsString(providerRequest).toByteArray(Charsets.UTF_8))
  val location = UriComponentsBuilder.fromPath("$host/siwa/start")
    .queryParam(SIGNED_REQUEST_PARAMETER_NAME, siwaRequestString)
    .queryParam(USER_KEY_TYPE, userKeyPairType.type)
    .queryParams(MultiValueMap.fromSingleValue(queryParams))
    .toUriString()

  page.navigate(location)
}

fun fillPayloadInputsAndSubmit(page: Page, handle: String? = null): Response {
  val claimHandleInput = page.getByTestId("claim-handle-input")

  // If handle is prefilled, check to see if the value is correct
  if (claimHandleInput.isVisible && claimHandleInput.inputValue().isNotEmpty()) {
    Assertions.assertThat(claimHandleInput.inputValue()).isEqualTo(handle)
  }

  val submitButton = page.getByTestId("payloads-submit")
  // Expect to be on the 'payloads' page
  assertThat(submitButton).isVisible()

  // Fill handle input if it's supposed to be filled
  if(handle != null) {
    claimHandleInput.fill(handle)
  }

  // Submit form to accept new permission(s)
  return page.waitForResponse("**/siwa/accepted") {
    submitButton.click()
  }
}

fun assertNewUserSiwaPayloads(
  baseHandle: String,
  schemaIds: List<Int>,
  payloads: List<TypedPayloadResponseWithSignature<*>>,
  graphKeyPayloadExpected: Boolean = true,
) {
  val underlyingPayloads = payloads.map {
    it.payload
  }

  if (graphKeyPayloadExpected) {
    Assertions.assertThat(underlyingPayloads).hasExactlyElementsOfTypes(
      AddProviderPayloadResponse::class.java,
      HandlePayloadResponse::class.java,
      ItemizedSignaturePayloadResponse::class.java,
    )
  } else {
    Assertions.assertThat(underlyingPayloads).hasExactlyElementsOfTypes(
      AddProviderPayloadResponse::class.java,
      HandlePayloadResponse::class.java,
    )
  }

  for (underlyingPayload in underlyingPayloads) {
    when (underlyingPayload) {
      is HandlePayloadResponse -> {
        Assertions.assertThat(underlyingPayload.baseHandle).isEqualTo(baseHandle)
      }
      is AddProviderPayloadResponse -> {
        Assertions.assertThat(underlyingPayload.schemaIds).containsAll(schemaIds)
      }
      is ItemizedSignaturePayloadResponse -> {
        Assertions.assertThat(underlyingPayload.schemaId).isEqualTo(7)
        Assertions.assertThat(underlyingPayload.actions).hasSize(1)

        val addItemAction = underlyingPayload.actions.first() as AddItemAction
        Assertions.assertThat(addItemAction.payloadHex).hasSize(68)
        Assertions.assertThat(addItemAction.payloadHex).startsWith("0x40")
      }
      else -> Assertions.fail("Invalid payload type: ${underlyingPayload::class}")
    }
  }
}

fun assertMigrationSiwaPayloads(schemaIds: List<Int>, payloads: List<TypedPayloadResponseWithSignature<*>>) {
  val innerPayloads = payloads.map { it.payload }
  Assertions.assertThat(innerPayloads).hasExactlyElementsOfTypes(
    AddProviderPayloadResponse::class.java
  )

  for (payload in payloads) {
    when (val innerPayload = payload.payload) {
      is AddProviderPayloadResponse -> {
        Assertions.assertThat(innerPayload.schemaIds).containsAll(schemaIds)
        Assertions.assertThat(payload.endpoint).isEqualTo(FrequencyEndpoint.Msa.grantDelegation)
      }
      else -> Assertions.fail("Invalid payload type: ${innerPayload::class}")
    }
  }
}

fun fillUserPhoneNumberAndSubmitContinuedSessionExpected(page: Page, phoneNumber: String): String {
  // Fill in user's phone number
  if(!page.getByTestId("phone-form").isVisible){
    page.getByTestId("switch-button").click()
  }
  page.getByTestId("phone-form").fill(phoneNumber)

  val response = page.waitForResponse("**/siwa/verify") {
    page.getByTestId("sms-identifier-submit").click()
  }

  Assertions.assertThat(response.status()).isEqualTo(303)
  return response.headerValue(HttpHeaders.LOCATION)
}

fun assertForSiwaStart(engine: BrowserEngine, page: Page) {
  val errors = collectJavaScriptErrors(engine, page)

  assertThat(page.getByTestId("email-identifier-submit")).isVisible()
  val sessionIdCookie = findSessionIdCookie(page.context().cookies())
  Assertions.assertThat(sessionIdCookie.value).isNotNull()

  // Check no JS errors were thrown or logged
  Assertions.assertThat(errors).isEmpty()
}

fun getValidAuthenticatedSiwaSession(page: Page, redisClient: CustodialWalletRedisClient): AuthenticatedSiwaSession {
  val foundSessionId = findSessionIdCookie(page.context().cookies()).value
  var validSiwaSession: AuthenticatedSiwaSession
  runBlocking {
    val siwaSession = redisClient.findSiwaSessionBySessionId(foundSessionId)
      ?: throw ApiException(ApiError.SIWA_SESSION_NOT_FOUND, "Siwa session $foundSessionId not found")
    when (siwaSession) {
      is UnauthenticatedSiwaSession -> throw ApiException(ApiError.SIWA_INVALID_STATE, "Siwa session should be authenticated")
      is AuthenticatedSiwaSession -> {
        validSiwaSession = siwaSession
      }
    }
  }

  return validSiwaSession
}

fun validateRedirectAndSignedSiwaPayloads(page: Page, validSiwaSession: AuthenticatedSiwaSession, providerRequest: SiwaRequest, apiUtil: ApiUtil, userHandle: String, client: FrequencyClient) {
  val pageUrlComponents = UriComponentsBuilder.fromUriString(page.url()).build()

  // Sanity check that the callback URL is correct
  val expectedCallbackComponents = UriComponentsBuilder.fromUriString(validSiwaSession.fullCallbackUrl)
    .queryParam(AUTHORIZATION_CODE_PARAMETER_NAME, validSiwaSession.authorizationCode)
    .build()
  Assertions.assertThat(pageUrlComponents.host).isEqualTo(expectedCallbackComponents.host)
  Assertions.assertThat(pageUrlComponents.queryParams).usingRecursiveComparison().isEqualTo(expectedCallbackComponents.queryParams)

  val siwaPayloadResponse = fetchSiwaPayloadResponseForCallback(page.url(), apiUtil)

  assertNewUserSiwaPayloads(
    userHandle,
    providerRequest.signatureRequest.payload.permissions,
    siwaPayloadResponse.payloads
  )

  submitSiwaSignedPayloads(client, siwaPayloadResponse)
}