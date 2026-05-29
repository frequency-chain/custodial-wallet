package io.amplica.custodial_wallet

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.conf.BeanNames
import io.amplica.custodial_wallet.conf.PropertyNames
import io.amplica.custodial_wallet.container.CustodialWalletE2ETestStack
import io.amplica.custodial_wallet.container.FrequencyTestProvider
import io.amplica.custodial_wallet.db.repository.KeyUsageType
import io.amplica.custodial_wallet.dto.SiwaPayloadResponse
import io.amplica.custodial_wallet.extension.BrowserEngine
import io.amplica.custodial_wallet.extension.BrowserTest
import io.amplica.custodial_wallet.extension.PlaywrightExtension
import io.amplica.custodial_wallet.orchestration.util.mapUserIdentifierToUserDetail
import io.amplica.custodial_wallet.service.organization.OrganizationData
import io.amplica.custodial_wallet.service.organization.OriginDescriptor
import io.amplica.custodial_wallet.service.organization.ProviderApplicationData
import io.amplica.custodial_wallet.util.*
import io.amplica.custodial_wallet.util.DbUtil.Companion.ALICE_ORGANIZATION
import io.amplica.custodial_wallet.util.DbUtil.Companion.ALICE_PROVIDER_APPLICATION
import io.amplica.custodial_wallet.util.DbUtil.Companion.BOB_ORGANIZATION
import io.amplica.custodial_wallet.util.DbUtil.Companion.BOB_PROVIDER_APPLICATION
import io.amplica.custodial_wallet.verifiablecredentials.VerifiableCredentialAuthenticator
import io.amplica.custodial_wallet.verifiablecredentials.crypto.Ed25519SignatureManager
import io.amplica.custodial_wallet.verifiablecredentials.dto.*
import io.amplica.custodial_wallet.web.AUTHORIZATION_CODE_PARAMETER_NAME
import io.amplica.custodial_wallet.web.PREFILL_PHONE_NUMBER_PARAMETER_NAME
import io.amplica.custodial_wallet.web.PREFILL_USER_HANDLE_PARAMETER_NAME
import io.amplica.frequency.client.DelegationItemResponse
import io.amplica.frequency.client.FrequencyClient
import io.amplica.frequency.client.SchemaGrantResponse
import io.amplica.frequency.crypto.AccountKeyPair
import io.amplica.frequency.crypto.provider.CryptoProvider
import io.amplica.frequency.crypto.provider.Secp256K1CryptoProvider
import io.amplica.frequency.crypto.provider.Sr25519CryptoProvider
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.Parameter
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.MethodSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.util.UriComponentsBuilder
import org.testcontainers.junit.jupiter.Container
import java.net.URI
import java.time.Duration
import java.time.ZonedDateTime
import java.util.regex.Pattern

@CustodialWalletE2ESpringTestConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ParameterizedClass
@MethodSource("io.amplica.custodial_wallet.parameters.CryptoProviderParameterSource#srAndEthereum")
class SiwaPlaywrightE2ETests(
  @Qualifier(BeanNames.SES_UTIL) private val sesUtil: SesUtil,
  @Qualifier(BeanNames.API_UTIL) private val apiUtil: ApiUtil,
  @Qualifier(BeanNames.DB_UTIL) private val dbUtil: DbUtil,
  @Value("\${unfinished.custodial-wallet.hostname}") private val hostnamePropertyValue: String,
  @Value("\${${PropertyNames.SIWA_EMAIL_HANDLING_DEFAULT}}") private val siwaEmailHandling: SiwaEmailHandling,
) {

  @Parameter
  lateinit var providerCryptoProvider: CryptoProvider

  @Qualifier("objectMapper")
  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @LocalServerPort
  private lateinit var serverPort: String

  @Autowired
  lateinit var verifiableCredentialAuthenticator: VerifiableCredentialAuthenticator

  @Autowired
  lateinit var databaseService: CustodialWalletDatabaseService

  @Value("\${unfinished.custodial-wallet.keypair.ed25519.seed}")
  lateinit var ed25519SeedHex: String

  @Value("\${unfinished.custodial-wallet.verifiable-credentials.valid-from-delay}")
  lateinit var validFromDelay: Duration

  private val host: String
    get() = "$HOST_NAME:$serverPort"

  private val callbackUrl: String
    // NOTE: Must be insecure, otherwise browsers throw an SSL error
    get() = "http://$host"

  private val userIdentifierAdminUrl = "https://www.mewe.com"
  lateinit var userEmail: String
  lateinit var userPhone: String
  lateinit var userHandle: String
  lateinit var emailIdentifier: UserIdentifier
  lateinit var phoneIdentifier: UserIdentifier
  lateinit var userRegistrationDataEmail: UserRegistrationData
  lateinit var userRegistrationDataPhone: UserRegistrationData

  private val srProviderOrganization = OrganizationData(
    setOf(srProvider.msaId),
    "SRTest",
    "srpt",
    listOf(OriginDescriptor("https", "charlie.com")),
    emptyMap()
  )

  private val ethProviderOrganization = OrganizationData(
    setOf(ethProvider.msaId),
    "EthTest",
    "etht",
    listOf(OriginDescriptor("https", "delta.com")),
    emptyMap()
  )

  private val srProviderApplicationData = ProviderApplicationData(
    URI("application.charlie.com"),
    "SrProviderApp",
    "srpapp",
    listOf(OriginDescriptor("app", "charlie.com")),
    emptyMap()
  )

  private val ethProviderApplicationData = ProviderApplicationData(
    URI("application.delta.com"),
    "EthProviderApp",
    "ethpapp",
    listOf(OriginDescriptor("app", "delta.com")),
    emptyMap()
  )

  private lateinit var keyPair: SubstrateOrAccountKeyPair
  private lateinit var provider: FrequencyTestProvider
  private lateinit var providerApplicationData: ProviderApplicationData
  private lateinit var providerOrganizationData: OrganizationData

  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(SiwaPlaywrightE2ETests::class.java)

    @RegisterExtension
    @JvmField
    val pwe = PlaywrightExtension()

    @Container
    val containers = CustodialWalletE2ETestStack()

    private val mockNotificationService = MockNotificationService()
    private val mockHCaptchaClient = MockHCaptchaClient()
    private val mockClaimService = MockClaimService()

    @DynamicPropertySource
    @JvmStatic
    fun registerContainerProperties(registry: DynamicPropertyRegistry) {
      containers.registerDynamicProperties(registry)

      registry.add("unfinished.custodial-wallet.notification-service.service_endpoint") {
        "http://localhost:${mockNotificationService.port()}"
      }
      registry.add("unfinished.custodial-wallet.hcaptcha.service_endpoint") {
        "http://localhost:${mockHCaptchaClient.port()}"
      }
      registry.add("unfinished.custodial-wallet.hcaptcha.status_endpoint") {
        "http://localhost:${mockHCaptchaClient.port()}"
      }
      registry.add("unfinished.custodial-wallet.claim-service.service_endpoint") {
        "http://localhost:${mockClaimService.port()}"
      }
      // We disable passkey here, so we can test it more directly in its own file
      registry.add("unfinished.custodial-wallet.siwa.passkey-wallet.enabled") {
        false
      }
    }

    const val BLOCKED_USER_PHONE = "8484448888"

    private lateinit var srKeyPair: AccountKeyPair
    private lateinit var ethKeyPair: AccountKeyPair

    private lateinit var srProvider: FrequencyTestProvider
    private lateinit var ethProvider: FrequencyTestProvider


    @JvmStatic
    @BeforeAll
    fun setUpAll() {
      srKeyPair = Sr25519CryptoProvider.createKeyPair()
      ethKeyPair = Secp256K1CryptoProvider.createKeyPair()

      srProvider = containers.frequency.createProvider(
        srKeyPair,
        "SR Provider"
      )
      ethProvider = containers.frequency.createProvider(
        ethKeyPair,
        "ETH Provider"
      )
    }
  }

  @BeforeEach
  fun before() {
    userEmail = generateUniqueEmail()
    userPhone = generateUniquePhone(false)
    userHandle = generateHandle()
    emailIdentifier = UserIdentifier(userEmail, UserIdentifierType.EMAIL)
    phoneIdentifier = UserIdentifier(userPhone, UserIdentifierType.PHONE_NUMBER)
    userRegistrationDataEmail = UserRegistrationData(
      emailIdentifier,
      userHandle,
      userIdentifierAdminUrl
    )

    userRegistrationDataPhone = UserRegistrationData(
      phoneIdentifier,
      userHandle,
      userIdentifierAdminUrl
    )

    mockNotificationService.reset()
    mockClaimService.reset()

    dbUtil.saveOrganizationData(listOf(
      srProviderOrganization to srProviderApplicationData,
      ethProviderOrganization to ethProviderApplicationData,
      ALICE_ORGANIZATION to ALICE_PROVIDER_APPLICATION,
      BOB_ORGANIZATION to BOB_PROVIDER_APPLICATION,
    ))

    when (providerCryptoProvider) {
      Sr25519CryptoProvider -> {
        keyPair = SubstrateOrAccountKeyPair.AccountKeyPairWrapper(srKeyPair)
        provider = srProvider
        providerApplicationData = srProviderApplicationData
        providerOrganizationData = srProviderOrganization
      }

      Secp256K1CryptoProvider -> {
        keyPair = SubstrateOrAccountKeyPair.AccountKeyPairWrapper(ethKeyPair)
        provider = ethProvider
        providerApplicationData = ethProviderApplicationData
        providerOrganizationData = ethProviderOrganization
      }
    }
  }

  @AfterEach
  fun tearDown() {
    dbUtil.deleteFromAllTables()
    sesUtil.deleteAllMessages()
  }

  private fun assertCallbackUrlCorrect(actualUrl: String) {
    Assertions.assertThat(actualUrl).startsWith(callbackUrl)
    val uuidRegex = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    val authorizationCode = UriComponentsBuilder.fromUriString(actualUrl).build().queryParams.getFirst(AUTHORIZATION_CODE_PARAMETER_NAME)
    Assertions.assertThat(authorizationCode).matches(Pattern.compile(uuidRegex))
  }

  @Nested
  @ParameterizedClass
  @MethodSource("io.amplica.custodial_wallet.parameters.UserKeyPairTypeParameterSource#sr25519AndEthereum")
  inner class MultiUserKeyPairTypeTests {

    @Parameter
    lateinit var userKeyPairType: io.amplica.custodial_wallet.util.key_creation.KeyPairType

    @BrowserTest
    fun saveAndThenStartSiwaSucceeds(engine: BrowserEngine) {
      val providerRequest = createProviderSiwaRequest(
        containers.frequency.wsAddress,
        keyPair,
        DEFAULT_PERMISSIONS,
        callbackUrl,
        userIdentifierAdminUrl,
        null,
        ApplicationContext(providerApplicationData.verifiedCredentialUrl)
      )
      val saveResponse = apiUtil.postSiwaSaveProviderRequest(providerRequest)

      Assertions.assertThat(saveResponse.statusCode).isEqualTo(HttpStatus.CREATED)
      val location = saveResponse.headers.location ?: Assertions.fail("Location header was null")
      pwe.createContext(engine).use { (_, page) ->
        page.navigate("${host}${location.path}?${location.rawQuery}")
        assertForSiwaStart(engine, page)
      }
    }

    @BrowserTest
    fun startSiwaSucceeds(engine: BrowserEngine) {
      pwe.createContext(engine).use { (_, page) ->
        // The initiation process manipulates the browser in a way that causes some JS errors
        val providerRequest = createProviderSiwaRequest(
          containers.frequency.wsAddress,
          keyPair,
          DEFAULT_PERMISSIONS,
          callbackUrl,
          userIdentifierAdminUrl,
          null,
          ApplicationContext(providerApplicationData.verifiedCredentialUrl)
        )
        setUpSiwaStartPage(page, providerRequest, objectMapper, host, userKeyPairType)
        assertForSiwaStart(engine, page)
      }
    }

    @BrowserTest
    fun sendingEmailVerificationSucceedsEncoded(engine: BrowserEngine) {
      pwe.createContext(engine).use { (_, page) ->
        // The initiation process manipulates the browser in a way that causes some JS errors
        val providerRequest = createProviderSiwaRequest(
          containers.frequency.wsAddress,
          keyPair,
          DEFAULT_PERMISSIONS,
          callbackUrl,
          userIdentifierAdminUrl,
          null,
          ApplicationContext(providerApplicationData.verifiedCredentialUrl)
        )
        val providerRequestString = objectMapper.writeValueAsString(providerRequest)
        val base64UrlEncodedSiwaRequest = base64UrlEncode(providerRequestString.toByteArray(Charsets.UTF_8))
        val requestUrl = "$host/siwa/start?signedRequest=$base64UrlEncodedSiwaRequest"

        page.navigate(requestUrl)

        emailVerificationSucceeds(engine, userEmail, page)
      }
    }

    @BrowserTest
    fun sendingEmailVerificationSucceeds(engine: BrowserEngine) {
      pwe.createContext(engine).use { (_, page) ->
        // The initiation process manipulates the browser in a way that causes some JS errors
        val providerRequest = createProviderSiwaRequest(
          containers.frequency.wsAddress,
          keyPair,
          DEFAULT_PERMISSIONS,
          callbackUrl,
          userIdentifierAdminUrl,
          null,
          ApplicationContext(providerApplicationData.verifiedCredentialUrl)
        )
        setUpSiwaStartPage(page, providerRequest, objectMapper, host, userKeyPairType)

        emailVerificationSucceeds(engine, userEmail, page)
      }
    }

    @BrowserTest
    fun sendingSmsVerificationSucceeds(engine: BrowserEngine) {
      pwe.createContext(engine).use { (_, page) ->
        // The initiation process manipulates the browser in a way that causes some JS errors
        val providerRequest = createProviderSiwaRequest(
          containers.frequency.wsAddress,
          keyPair,
          DEFAULT_PERMISSIONS,
          callbackUrl,
          userIdentifierAdminUrl,
          null,
          ApplicationContext(providerApplicationData.verifiedCredentialUrl)
        )
        setUpSiwaStartPage(page, providerRequest, objectMapper, host, userKeyPairType)

        val errors = collectJavaScriptErrors(engine, page)

        // Fill in user's phone number
        fillUserPhoneNumberAndSubmit(userPhone, page, userIdentifierAdminUrl)

        // Check the notification service received a request to send an sms to this user
        mockNotificationService.verifySmsSendInvokedOnce()
        val request = mockNotificationService.getLastSmsSendRequest()
        Assertions.assertThat(request.destinationPhoneNumber).isEqualTo(userPhone)
        Assertions.assertThat(request.messageBody).contains("Your Frequency Access verification code is:")

        // Check no JS errors were thrown or logged
        Assertions.assertThat(errors).isEmpty()
      }
    }

    @BrowserTest
    fun emailAuthenticationSucceeds(engine: BrowserEngine) {
      pwe.createContext(engine).use { (_, page) ->
        // The initiation process manipulates the browser in a way that causes some JS errors
        val providerRequest = createProviderSiwaRequest(
          containers.frequency.wsAddress,
          keyPair,
          DEFAULT_PERMISSIONS,
          callbackUrl,
          userIdentifierAdminUrl,
          null,
          ApplicationContext(providerApplicationData.verifiedCredentialUrl)
        )
        setUpSiwaStartPage(page, providerRequest, objectMapper, host, userKeyPairType)

        val errors = collectJavaScriptErrors(engine, page)

        // Fill in user's email
        val emailInput = page.getByTestId("email-identifier-input")
        emailInput.fill(userEmail)

        val response = page.waitForResponse("**/siwa/verify") {
          page.getByTestId("email-identifier-submit").click()
        }

        Assertions.assertThat(response.status()).isEqualTo(200)

        val message = sesUtil.getLatestMessage(userEmail)
        val (token, sessionId, _) = retrieveEmailParams(message)

        // Open email link
        page.navigate("$host/siwa/payloads?sessionId=$sessionId&token=$token")

        // Assert page looks right
        assertThat(page.getByTestId("claim-handle-input")).isVisible()
        assertThat(
          page.getByTestId("permissions-list").getByText("Update your handle and profile information")
        ).isVisible()
        assertThat(
          page.getByTestId("permissions-list")
            .getByText("Record, delete and modify the public and private follows and connections from your account to your Social Graph on Frequency")
        ).isVisible()

        // Check no JS errors were thrown or logged
        Assertions.assertThat(errors).isEmpty()
      }
    }

    @BrowserTest
    fun smsAuthenticationSucceeds(engine: BrowserEngine) {
      pwe.createContext(engine).use { (_, page) ->
        // The initiation process manipulates the browser in a way that causes some JS errors
        val providerRequest = createProviderSiwaRequest(
          containers.frequency.wsAddress,
          keyPair,
          DEFAULT_PERMISSIONS,
          callbackUrl,
          userIdentifierAdminUrl,
          null,
          ApplicationContext(providerApplicationData.verifiedCredentialUrl)
        )
        setUpSiwaStartPage(page, providerRequest, objectMapper, host, userKeyPairType)

        val errors = collectJavaScriptErrors(engine, page)

        fillUserPhoneNumberAndSubmit(userPhone, page, userIdentifierAdminUrl)

        val smsCode = getSmsCodeFromMockNotificationService(
          isLogin = false,
          userPhone,
          mockNotificationService
        )

        // Enter authentication code
        fillAuthenticationCodeAndSubmit(page, smsCode)

        // Assert page looks right
        assertThat(page.getByTestId("claim-handle-input")).isVisible()
        assertThat(
          page.getByTestId("permissions-list").getByText("Update your handle and profile information")
        ).isVisible()
        assertThat(
          page.getByTestId("permissions-list")
            .getByText("Record, delete and modify the public and private follows and connections from your account to your Social Graph on Frequency")
        ).isVisible()

        // Check no JS errors were thrown or logged
        Assertions.assertThat(errors).isEmpty()
      }
    }

    @BrowserTest
    fun siwaNewUserFlowWithEmailSucceeds(engine: BrowserEngine) {
      pwe.createContext(engine).use { (_, page) ->
        // The initiation process manipulates the browser in a way that causes some JS errors
        val providerRequest = createProviderSiwaRequest(
          containers.frequency.wsAddress,
          keyPair,
          DEFAULT_PERMISSIONS,
          callbackUrl,
          userIdentifierAdminUrl,
          null,
          ApplicationContext(providerApplicationData.verifiedCredentialUrl)
        )
        setUpSiwaStartPage(page, providerRequest, objectMapper, host, userKeyPairType)

        completeNewUserFlowForEmail(engine, page, provider.client, providerRequest)
      }
    }

    /**
     * This is a convenient place to put a breakpoint if you need to generate new requests
     *
     * @return the SiwaRequest with it's base64UrlEncoded version as well
     */
    private fun generateRequestData(): Pair<SiwaRequest, String> {
      val providerRequest = createProviderSiwaRequest(
        containers.frequency.wsAddress,
        keyPair,
        DEFAULT_PERMISSIONS,
        callbackUrl,
        userIdentifierAdminUrl,
        null,
        ApplicationContext(providerApplicationData.verifiedCredentialUrl)
      )
      val providerRequestString = objectMapper.writeValueAsString(providerRequest)
      val base64UrlEncodedSiwaRequest = base64UrlEncode(providerRequestString.toByteArray(Charsets.UTF_8))

      return Pair(providerRequest, base64UrlEncodedSiwaRequest)
    }

    @BrowserTest
    fun siwaNewUserFlowWithEmailSucceedsViaGetIngress(engine: BrowserEngine) {
      pwe.createContext(engine).use { (_, page) ->
        val siwaRequestAndBase64UrlEncodedRequest = generateRequestData()
        val requestUrl = "$host/siwa/start?signedRequest=${siwaRequestAndBase64UrlEncodedRequest.second}&foo=bar&baz=biff"

        page.navigate(requestUrl)

        completeNewUserFlowForEmail(
          engine,
          page,
          provider.client,
          siwaRequestAndBase64UrlEncodedRequest.first
        )
      }
    }

    @BrowserTest
    fun siwaNewUserFlowWithEmailSucceedsWithPrefilledUserHandle(engine: BrowserEngine) {
      pwe.createContext(engine).use { (_, page) ->
        // The initiation process manipulates the browser in a way that causes some JS errors
        val providerRequest = createProviderSiwaRequest(
          containers.frequency.wsAddress,
          keyPair,
          DEFAULT_PERMISSIONS,
          callbackUrl,
          userIdentifierAdminUrl,
          null,
          ApplicationContext(providerApplicationData.verifiedCredentialUrl)
        )
        setUpSiwaStartPage(
          page,
          providerRequest,
          objectMapper,
          host,
          userKeyPairType,
          mapOf(
            PREFILL_USER_HANDLE_PARAMETER_NAME to userHandle
          ),
        )

        completeNewUserFlowForEmail(engine, page, provider.client, providerRequest)
      }
    }

    @BrowserTest
    fun siwaNewUserFlowWithEmailSucceedsWithPrefilledPhoneNumber(engine: BrowserEngine) {
      pwe.createContext(engine).use { (_, page) ->
        // The initiation process manipulates the browser in a way that causes some JS errors
        val providerRequest = createProviderSiwaRequest(
          containers.frequency.wsAddress,
          keyPair,
          DEFAULT_PERMISSIONS,
          callbackUrl,
          userIdentifierAdminUrl,
          null,
          ApplicationContext(providerApplicationData.verifiedCredentialUrl)
        )
        setUpSiwaStartPage(
          page,
          providerRequest,
          objectMapper,
          host,
          userKeyPairType,
          mapOf(
            PREFILL_PHONE_NUMBER_PARAMETER_NAME to userPhone
          )
        )

        val errors = collectJavaScriptErrors(engine, page)

        //Submit User Phone Number
        val response = page.waitForResponse("**/siwa/verify") {
          page.getByTestId("sms-identifier-submit").click()
        }

        Assertions.assertThat(response.status()).isEqualTo(200)
        Assertions.assertThat(page.getByTestId("user-identifier-admin-url").getAttribute("href")).isEqualTo(userIdentifierAdminUrl)

        // Check the notification service received a request to send an sms to this user
        mockNotificationService.verifySmsSendInvokedOnce()
        val request = mockNotificationService.getLastSmsSendRequest()
        Assertions.assertThat(request.destinationPhoneNumber).isEqualTo(userPhone)
        Assertions.assertThat(request.messageBody).contains("Your Frequency Access verification code is:")

        // Check no JS errors were thrown or logged
        Assertions.assertThat(errors).isEmpty()
      }
    }

    @BrowserTest
    fun emailLoginSucceedsOtp(engine: BrowserEngine) {
      val providerRequest = createProviderSiwaRequest(
        containers.frequency.wsAddress,
        keyPair,
        DEFAULT_PERMISSIONS,
        callbackUrl,
        userIdentifierAdminUrl,
        SiwaEmailHandling.OTP,
        ApplicationContext(providerApplicationData.verifiedCredentialUrl)
      )

      //Separate context needed for registering new user
      pwe.createContext(engine).use { (_, page) ->
        siwaRegisterNewUserE2E(
          page,
          sesUtil,
          host,
          apiUtil,
          objectMapper,
          mockNotificationService,
          provider,
          providerRequest,
          isPasskeyEnabled = false,
          userRegistrationDataEmail,
          userKeyPairType,
        )
      }

      pwe.createContext(engine).use { (_, page) ->
        // Navigate to the beginning of the flow
        // Using the same request as before
        setUpSiwaStartPage(page, providerRequest, objectMapper, host, userKeyPairType)

        // The initiation process manipulates the browser in a way that causes some JS errors
        val errors = collectJavaScriptErrors(engine, page)

        // Fill in user's email
        val emailInput = page.getByTestId("email-identifier-input")
        emailInput.fill(userEmail)

        val response = page.waitForResponse("**/siwa/verify") {
          page.getByTestId("email-identifier-submit").click()
        }

        Assertions.assertThat(response.status()).isEqualTo(200)

        val message = sesUtil.getLatestMessage(userEmail)

        Assertions.assertThat(message.Template).contains("OTP")

        val (token, _, _) = retrieveEmailParams(message)

        fillAuthenticationCodeAndSubmit(page, token)

        // Assert redirected to provider
        val currentUrl = page.url()
        assertCallbackUrlCorrect(currentUrl)

        // Check no JS errors were thrown or logged
        Assertions.assertThat(errors).isEmpty()

        val payloadResponse = fetchSiwaPayloadResponseForCallback(currentUrl, apiUtil)
        assertSiwaPayloadResponse(payloadResponse, emailIdentifier)
        assertLoginSiwaPayloads(payloadResponse.payloads)
      }
    }

    @BrowserTest
    fun emailLoginSucceedsMagicLink(engine: BrowserEngine) {
      val providerRequest = createProviderSiwaRequest(
        containers.frequency.wsAddress,
        keyPair,
        DEFAULT_PERMISSIONS,
        callbackUrl,
        userIdentifierAdminUrl,
        SiwaEmailHandling.MAGIC_LINK,
        ApplicationContext(providerApplicationData.verifiedCredentialUrl)
      )

      //Separate context needed for registering new user
      pwe.createContext(engine).use { (_, page) ->
        siwaRegisterNewUserE2E(
          page,
          sesUtil,
          host,
          apiUtil,
          objectMapper,
          mockNotificationService,
          provider ,
          providerRequest,
          isPasskeyEnabled = false,
          userRegistrationDataEmail,
          userKeyPairType,
        )
      }

      pwe.createContext(engine).use { (_, page) ->
        // Navigate to the beginning of the flow
        // Using the same request as before
        setUpSiwaStartPage(page, providerRequest, objectMapper, host, userKeyPairType)

        // The initiation process manipulates the browser in a way that causes some JS errors
        val errors = collectJavaScriptErrors(engine, page)

        // Fill in user's email
        val emailInput = page.getByTestId("email-identifier-input")
        emailInput.fill(userEmail)

        val response = page.waitForResponse("**/siwa/verify") {
          page.getByTestId("email-identifier-submit").click()
        }

        Assertions.assertThat(response.status()).isEqualTo(200)

        val message = sesUtil.getLatestMessage(userEmail)

        Assertions.assertThat(message.Template).doesNotContain("OTP")

        val (token, sessionId, _) = retrieveEmailParams(message)

        // Open email link
        page.navigate("$host/siwa/payloads?sessionId=$sessionId&token=$token")

        val loginLinkLocator = page.getByTestId("login-link")
        val callbackUrl = loginLinkLocator.getAttribute("href")

        assertCallbackUrlCorrect(callbackUrl)

        // Check no JS errors were thrown or logged
        Assertions.assertThat(errors).isEmpty()

        val payloadResponse = fetchSiwaPayloadResponseForCallback(callbackUrl, apiUtil)

        assertSiwaPayloadResponse(payloadResponse, emailIdentifier)
        assertLoginSiwaPayloads(payloadResponse.payloads)
      }
    }


    @BrowserTest
    fun smsLoginSucceeds(engine: BrowserEngine) {
      val providerRequest = createProviderSiwaRequest(
        containers.frequency.wsAddress,
        keyPair,
        DEFAULT_PERMISSIONS,
        callbackUrl,
        userIdentifierAdminUrl,
        null,
        ApplicationContext(providerApplicationData.verifiedCredentialUrl)
      )
      //Separate context needed for registering new user
      pwe.createContext(engine).use { (_, page) ->
        siwaRegisterNewUserE2E(
          page,
          sesUtil,
          host,
          apiUtil,
          objectMapper,
          mockNotificationService,
          provider,
          providerRequest,
          isPasskeyEnabled = false,
          userRegistrationDataPhone,
          userKeyPairType,
        )
      }

      pwe.createContext(engine).use { (_, page) ->
        // Navigate to the beginning of the flow
        // Using the same request as before
        setUpSiwaStartPage(page, providerRequest, objectMapper, host, userKeyPairType)

        // The initiation process manipulates the browser in a way that causes some JS errors
        val errors = collectJavaScriptErrors(engine, page)

        val sessionId = fillUserPhoneNumberAndSubmit(userPhone, page, userIdentifierAdminUrl)

        val smsCode = getSmsCodeFromMockNotificationService(
          isLogin = true,
          userPhone,
          mockNotificationService
        )
        Assertions.assertThat(apiUtil.getSiwaTokenForSessionId(sessionId).body!!.token).isEqualTo(smsCode)
        fillAuthenticationCodeAndSubmit(page, smsCode)

        // Assert redirected to provider
        val currentUrl = page.url()
        assertCallbackUrlCorrect(currentUrl)

        // Check no JS errors were thrown or logged
        Assertions.assertThat(errors).isEmpty()

        val payloadResponse = fetchSiwaPayloadResponseForCallback(currentUrl, apiUtil)
        assertSiwaPayloadResponse(payloadResponse, phoneIdentifier)
        assertLoginSiwaPayloads(payloadResponse.payloads)
      }
    }

    @BrowserTest
    fun smsLoginFailsWithBlockedPhoneNumber(engine: BrowserEngine) {
      pwe.createContext(engine).use { (_, page) ->
        // The initiation process manipulates the browser in a way that causes some JS errors
        val providerRequest = createProviderSiwaRequest(
          containers.frequency.wsAddress,
          keyPair,
          DEFAULT_PERMISSIONS,
          callbackUrl,
          userIdentifierAdminUrl,
          null,
          ApplicationContext(providerApplicationData.verifiedCredentialUrl)
        )
        setUpSiwaStartPage(page, providerRequest, objectMapper, host, userKeyPairType)

        val errors = collectJavaScriptErrors(engine, page)

        // Fill in user's phone number
        page.getByTestId("switch-button").click()
        page.getByTestId("phone-form").fill(BLOCKED_USER_PHONE)

        page.waitForResponse("**/siwa/verify") {
          page.getByTestId("sms-identifier-submit").click()
        }

        assertThat(page.getByTestId("email-identifier-input")).isVisible()
        assertThat(page.getByTestId("blocked-phone-number-error")).isVisible()

        // Check no JS errors were thrown or logged
        Assertions.assertThat(errors).isEmpty()
      }
    }

    @BrowserTest
    fun emailMigrationSucceeds(engine: BrowserEngine) {
      val providerRequest = createProviderSiwaRequest(
        containers.frequency.wsAddress,
        keyPair,
        GRAPH_SCHEMA_IDS,
        callbackUrl,
        userIdentifierAdminUrl,
        null,
        ApplicationContext(providerApplicationData.verifiedCredentialUrl)
      )
      //Separate browser contexts need to be created for this to work
      pwe.createContext(engine).use { (_, page) ->
        // Create a user through SIWA registration flow
        siwaRegisterNewUserE2E(
          page,
          sesUtil,
          host,
          apiUtil,
          objectMapper,
          mockNotificationService,
          provider,
          providerRequest,
          isPasskeyEnabled = false,
          userRegistrationDataEmail,
          userKeyPairType,
        )
      }
      pwe.createContext(engine).use { (_, page) ->
        // Navigate to the beginning of the flow
        // The same provider is now requesting *additional* permissions
        val updatedProviderRequest = createProviderSiwaRequest(
          containers.frequency.wsAddress,
          SubstrateOrAccountKeyPair.SubstrateKeyPairWrapper(containers.frequency.aliceKeyPair),
          PROFILE_SCHEMA_IDS + GRAPH_SCHEMA_IDS,
          callbackUrl,
          userIdentifierAdminUrl,
        )
        setUpSiwaStartPage(page, updatedProviderRequest, objectMapper, host, userKeyPairType)

        // The initiation process manipulates the browser in a way that causes some JS errors
        val errors = collectJavaScriptErrors(engine, page)

        // Fill in user's email
        val emailInput = page.getByTestId("email-identifier-input")
        emailInput.fill(userEmail)

        val response = page.waitForResponse("**/siwa/verify") {
          page.getByTestId("email-identifier-submit").click()
        }

        Assertions.assertThat(response.status()).isEqualTo(200)

        val message = sesUtil.getLatestMessage(userEmail)
        val (token, sessionId, _) = retrieveEmailParams(message)

        // Open email link
        page.navigate("$host/siwa/payloads?sessionId=$sessionId&token=$token")

        val acceptedResponse = fillPayloadInputsAndSubmit(page)

        // Should be redirected to provider callback
        Assertions.assertThat(acceptedResponse.status()).isEqualTo(303)

        // Use the callback URL parameters to fetch the SIWA payload response
        val callbackUrl = acceptedResponse.headerValue("Location")

        assertCallbackUrlCorrect(callbackUrl)

        // Check no JS errors were thrown or logged
        Assertions.assertThat(errors).isEmpty()

        val siwaPayload = fetchSiwaPayloadResponseForCallback(callbackUrl, apiUtil)

        // Check the response (except payloads)
        assertSiwaPayloadResponse(siwaPayload, emailIdentifier)

        assertMigrationSiwaPayloads(PROFILE_SCHEMA_IDS, siwaPayload.payloads)
      }
    }

    @BrowserTest
    fun emailMigrationSucceedsContinuedSession(engine: BrowserEngine) {
      pwe.createContext(engine).use { (_, page) ->
        // Create a user through SIWA registration flow
        val providerRequest = createProviderSiwaRequest(
          containers.frequency.wsAddress,
          keyPair,
          GRAPH_SCHEMA_IDS,
          callbackUrl,
          userIdentifierAdminUrl,
          null,
          ApplicationContext(providerApplicationData.verifiedCredentialUrl)
        )
        siwaRegisterNewUserE2E(
          page,
          sesUtil,
          host,
          apiUtil,
          objectMapper,
          mockNotificationService,
          provider,
          providerRequest,
          isPasskeyEnabled = false,
          userRegistrationDataEmail,
          userKeyPairType,
        )

        // Navigate to the beginning of the flow
        // The same provider is now requesting *additional* permissions
        val updatedProviderRequest = createProviderSiwaRequest(
          containers.frequency.wsAddress,
          keyPair,
          PROFILE_SCHEMA_IDS + GRAPH_SCHEMA_IDS,
          callbackUrl,
          userIdentifierAdminUrl,
          null,
          ApplicationContext(providerApplicationData.verifiedCredentialUrl)
        )
        setUpSiwaStartPage(page, updatedProviderRequest, objectMapper, host, userKeyPairType)

        // The initiation process manipulates the browser in a way that causes some JS errors
        val errors = collectJavaScriptErrors(engine, page)

        // Fill in user's email
        val emailInput = page.getByTestId("email-identifier-input")
        emailInput.fill(userEmail)

        val response = page.waitForResponse("**/siwa/verify") {
          page.getByTestId("email-identifier-submit").click()
        }

        Assertions.assertThat(response.status()).isEqualTo(303)
        val location = response.headerValue("Location").replace(hostnamePropertyValue, host) //replace hardcoding
        // Open email link
        page.navigate(location)

        val acceptedResponse = fillPayloadInputsAndSubmit(page)

        // Should be redirected to provider callback
        Assertions.assertThat(acceptedResponse.status()).isEqualTo(303)

        // Use the callback URL parameters to fetch the SIWA payload response
        val callbackUrl = acceptedResponse.headerValue("Location") //replace hardcoding

        assertCallbackUrlCorrect(callbackUrl)

        // Check no JS errors were thrown or logged
        Assertions.assertThat(errors).isEmpty()

        val siwaPayload = fetchSiwaPayloadResponseForCallback(callbackUrl, apiUtil)

        // Check the response (except payloads)
        assertSiwaPayloadResponse(siwaPayload, emailIdentifier)

        assertMigrationSiwaPayloads(PROFILE_SCHEMA_IDS, siwaPayload.payloads)
      }
    }
    @BrowserTest
    fun emailChangingIdentifiersDoesNotRedirect(engine: BrowserEngine) {
      val providerRequest = createProviderSiwaRequest(
        containers.frequency.wsAddress,
        keyPair,
        GRAPH_SCHEMA_IDS,
        callbackUrl,
        userIdentifierAdminUrl,
        null,
        ApplicationContext(providerApplicationData.verifiedCredentialUrl)
      )

      pwe.createContext(engine).use { (ctx, page) ->
        siwaRegisterNewUserE2E(
          page,
          sesUtil,
          host,
          apiUtil,
          objectMapper,
          mockNotificationService,
          provider,
          providerRequest,
          isPasskeyEnabled = false,
          userRegistrationDataEmail,
          userKeyPairType,
        )
        val cookies = ctx.cookies()
        val sessionId = findSessionIdCookie(cookies).value
        setUpSiwaStartPage(page, providerRequest, objectMapper, host, userKeyPairType)

        val errors = collectJavaScriptErrors(engine, page)
        emailVerificationSucceeds(engine, "not_the_same@example.com", page) //This would fail if it redirected as if the same session was going
        val cookieAfter = page.context().cookies()
        val newSessionId = findSessionIdCookie(cookieAfter).value

        Assertions.assertThat(errors).isEmpty()
        Assertions.assertThat(sessionId).isNotEqualTo(newSessionId)
      }
    }

    @BrowserTest
    fun smsMigrationSucceeds(engine: BrowserEngine) {
      val providerRequest = createProviderSiwaRequest(
        containers.frequency.wsAddress,
        keyPair,
        GRAPH_SCHEMA_IDS,
        callbackUrl,
        userIdentifierAdminUrl,
        null,
        ApplicationContext(providerApplicationData.verifiedCredentialUrl)
      )

      //Separate context needed for registering new user
      pwe.createContext(engine).use { (_, page) ->
        siwaRegisterNewUserE2E(
          page,
          sesUtil,
          host,
          apiUtil,
          objectMapper,
          mockNotificationService,
          provider,
          providerRequest,
          isPasskeyEnabled = false,
          userRegistrationDataPhone,
          userKeyPairType,
        )
      }

      pwe.createContext(engine).use { (_, page) ->
        // Navigate to the beginning of the flow
        // The same provider is now requesting *additional* permissions
        val updatedProviderRequest = createProviderSiwaRequest(
          containers.frequency.wsAddress,
          keyPair,
          PROFILE_SCHEMA_IDS + GRAPH_SCHEMA_IDS,
          callbackUrl,
          userIdentifierAdminUrl,
          null,
          ApplicationContext(providerApplicationData.verifiedCredentialUrl)
        )
        setUpSiwaStartPage(page, updatedProviderRequest, objectMapper, host, userKeyPairType)

        // The initiation process manipulates the browser in a way that causes some JS errors
        val errors = collectJavaScriptErrors(engine, page)

        val sessionId = fillUserPhoneNumberAndSubmit(userPhone, page, userIdentifierAdminUrl)

        val smsCode = getSmsCodeFromMockNotificationService(isLogin = true, userPhone, mockNotificationService)
        Assertions.assertThat(apiUtil.getSiwaTokenForSessionId(sessionId).body!!.token).isEqualTo(smsCode)
        fillAuthenticationCodeAndSubmit(page, smsCode)

        val acceptedResponse = fillPayloadInputsAndSubmit(page)

        // Should be redirected to provider callback
        Assertions.assertThat(acceptedResponse.status()).isEqualTo(303)

        // Use the callback URL parameters to fetch the SIWA payload response
        val callbackUrl = acceptedResponse.headerValue("Location")

        assertCallbackUrlCorrect(callbackUrl)

        // Check no JS errors were thrown or logged
        Assertions.assertThat(errors).isEmpty()

        val siwaPayload = fetchSiwaPayloadResponseForCallback(callbackUrl, apiUtil)

        // Check the response (except payloads)
        assertSiwaPayloadResponse(siwaPayload, phoneIdentifier)

        assertMigrationSiwaPayloads(PROFILE_SCHEMA_IDS, siwaPayload.payloads)
      }
    }

    @BrowserTest
    fun smsChangingIdentifiersDoesNotRedirect(engine: BrowserEngine) {
      val someOtherCountyCode = "+1"
      val someOtherPhone = "8055594436"
      val someOtherPhoneNumber = "$someOtherCountyCode$someOtherPhone"
      val providerRequest = createProviderSiwaRequest(
        containers.frequency.wsAddress,
        keyPair,
        GRAPH_SCHEMA_IDS,
        callbackUrl,
        userIdentifierAdminUrl,
        null,
        ApplicationContext(providerApplicationData.verifiedCredentialUrl)
      )

      //Separate context needed for registering new user
      pwe.createContext(engine).use { (ctx, page) ->
        siwaRegisterNewUserE2E(
          page,
          sesUtil,
          host,
          apiUtil,
          objectMapper,
          mockNotificationService,
          provider,
          providerRequest,
          isPasskeyEnabled = false,
          userRegistrationDataPhone,
          userKeyPairType,
        )

        val cookies = ctx.cookies()
        val sessionId = findSessionIdCookie(cookies).value
        setUpSiwaStartPage(page, providerRequest, objectMapper, host, userKeyPairType)

        val errors = collectJavaScriptErrors(engine, page)

        val newSessionId = fillUserPhoneNumberAndSubmit(someOtherPhoneNumber, page, userIdentifierAdminUrl) //This would fail if it redirected as if the same session was going

        Assertions.assertThat(errors).isEmpty()
        Assertions.assertThat(sessionId).isNotEqualTo(newSessionId)
      }
    }

    @BrowserTest
    fun smsMigrationSucceedsContinuedSession(engine: BrowserEngine) {
      pwe.createContext(engine).use { (_, page) ->
        val providerRequest = createProviderSiwaRequest(
          containers.frequency.wsAddress,
          keyPair,
          GRAPH_SCHEMA_IDS,
          callbackUrl,
          userIdentifierAdminUrl,
          null,
          ApplicationContext(providerApplicationData.verifiedCredentialUrl)
        )
        siwaRegisterNewUserE2E(
          page,
          sesUtil,
          host,
          apiUtil,
          objectMapper,
          mockNotificationService,
          provider,
          providerRequest,
          isPasskeyEnabled = false,
          userRegistrationDataPhone,
          userKeyPairType,
        )
        // Navigate to the beginning of the flow
        // The same provider is now requesting *additional* permissions
        val updatedProviderRequest = createProviderSiwaRequest(
          containers.frequency.wsAddress,
          keyPair,
          PROFILE_SCHEMA_IDS + GRAPH_SCHEMA_IDS,
          callbackUrl,
          userIdentifierAdminUrl,
          null,
          ApplicationContext(providerApplicationData.verifiedCredentialUrl)
        )
        setUpSiwaStartPage(page, updatedProviderRequest, objectMapper, host, userKeyPairType)

        // The initiation process manipulates the browser in a way that causes some JS errors
        val errors = collectJavaScriptErrors(engine, page)

        val location = fillUserPhoneNumberAndSubmitContinuedSessionExpected(page, userPhone)

        page.navigate(location.replace(hostnamePropertyValue, host))

        val acceptedResponse = fillPayloadInputsAndSubmit(page)

        // Should be redirected to provider callback
        Assertions.assertThat(acceptedResponse.status()).isEqualTo(303)
        // Use the callback URL parameters to fetch the SIWA payload response
        val callbackUrl = acceptedResponse.headerValue(HttpHeaders.LOCATION)

        assertCallbackUrlCorrect(callbackUrl)

        // Check no JS errors were thrown or logged
        Assertions.assertThat(errors).isEmpty()

        val siwaPayload = fetchSiwaPayloadResponseForCallback(callbackUrl, apiUtil)

        // Check the response (except payloads)
        assertSiwaPayloadResponse(siwaPayload, phoneIdentifier)

        assertMigrationSiwaPayloads(PROFILE_SCHEMA_IDS, siwaPayload.payloads)
      }
    }

    @Nested
    @ParameterizedClass
    @MethodSource("io.amplica.custodial_wallet.parameters.UserKeyPairTypeParameterSource#sr25519AndEthereum")
    inner class MultiProviderTests {

      @Parameter
      lateinit var secondProviderUserKeyPairType: io.amplica.custodial_wallet.util.key_creation.KeyPairType

      lateinit var secondProviderKeyPair: AccountKeyPair
      lateinit var secondProvider: FrequencyTestProvider
      lateinit var secondProviderApplicationData: ProviderApplicationData
      lateinit var secondProviderOrganizationData: OrganizationData

      @BeforeEach
      fun before() {
        when (secondProviderUserKeyPairType) {
          io.amplica.custodial_wallet.util.key_creation.KeyPairType.SR25519 -> {
            secondProviderKeyPair = Sr25519CryptoProvider.createKeyPair()
            secondProvider = containers.frequency.createProvider(secondProviderKeyPair, "SR2Provider")
            secondProviderApplicationData = ProviderApplicationData(
              URI("application.echo.com"),
              "SecondSRProviderApp",
              "sr2app",
              listOf(OriginDescriptor("app", "echo.com")),
              emptyMap()
            )
            secondProviderOrganizationData = OrganizationData(
              setOf(secondProvider.msaId),
              "SecondSRTest",
              "sr2pt",
              listOf(OriginDescriptor("https", "echo.com")),
              emptyMap()
            )
          }
          io.amplica.custodial_wallet.util.key_creation.KeyPairType.SECP256K1 -> {
            secondProviderKeyPair = Secp256K1CryptoProvider.createKeyPair()
            secondProvider = containers.frequency.createProvider(secondProviderKeyPair, "ETH2Provider")
            secondProviderApplicationData = ProviderApplicationData(
              URI("application.foxtrot.com"),
              "SecondEthProviderApp",
              "eth2papp",
              listOf(OriginDescriptor("app", "foxtrot.com")),
              emptyMap()
            )
            secondProviderOrganizationData = OrganizationData(
              setOf(secondProvider.msaId),
              "SecondETHTest",
              "eth2pt",
              listOf(OriginDescriptor("https", "foxtrot.com")),
              emptyMap()
            )
          }

          else ->
            throw IllegalArgumentException("KeyPair Type $secondProviderUserKeyPairType not supported for this test suite")
        }


        dbUtil.saveOrganizationData(listOf(
          secondProviderOrganizationData to secondProviderApplicationData
        ))
      }

      @BrowserTest
      fun smsDelegatingToNewProviderSucceeds(engine: BrowserEngine) {
        val firstProviderRequest = createProviderSiwaRequest(
          containers.frequency.wsAddress,
          keyPair,
          GRAPH_SCHEMA_IDS,
          callbackUrl,
          userIdentifierAdminUrl,
          applicationContext = ApplicationContext(providerApplicationData.verifiedCredentialUrl)
        )

        // Create an isolated context and register a new user with the top-level user key pair type
        pwe.createContext(engine).use { (_, page) ->
          siwaRegisterNewUserE2E(
            page,
            sesUtil,
            host,
            apiUtil,
            objectMapper,
            mockNotificationService,
            provider,
            firstProviderRequest,
            isPasskeyEnabled = false,
            userRegistrationDataPhone,
            userKeyPairType,
          )
        }

        pwe.createContext(engine).use { (_, page) ->
          // Navigate to the beginning of the flow
          // This time create a SIWA request for provider 'Bob'
          val secondProviderRequest = createProviderSiwaRequest(
            containers.frequency.wsAddress,
            SubstrateOrAccountKeyPair.AccountKeyPairWrapper(secondProviderKeyPair),
            GRAPH_SCHEMA_IDS,
            callbackUrl,
            userIdentifierAdminUrl,
            applicationContext = ApplicationContext(secondProviderApplicationData.verifiedCredentialUrl),
          )

          // NOTE: This SIWA request may use a different key pair than the first request
          setUpSiwaStartPage(page, secondProviderRequest, objectMapper, host, secondProviderUserKeyPairType)

          // The initiation process manipulates the browser in a way that causes some JS errors
          val errors = collectJavaScriptErrors(engine, page)

          val sessionId = fillUserPhoneNumberAndSubmit(userPhone, page, userIdentifierAdminUrl)

          val smsCode = getSmsCodeFromMockNotificationService(
            isLogin = true,
            userPhone,
            mockNotificationService
          )
          Assertions.assertThat(apiUtil.getSiwaTokenForSessionId(sessionId).body!!.token).isEqualTo(smsCode)
          fillAuthenticationCodeAndSubmit(page, smsCode)

          val acceptedResponse = fillPayloadInputsAndSubmit(page)

          // Should be redirected to provider callback
          Assertions.assertThat(acceptedResponse.status()).isEqualTo(303)

          // Use the callback URL parameters to fetch the SIWA payload response
          val callbackUrl = acceptedResponse.headerValue("Location")

          assertCallbackUrlCorrect(callbackUrl)

          // Check no JS errors were thrown or logged
          Assertions.assertThat(errors).isEmpty()

          val siwaPayload = fetchSiwaPayloadResponseForCallback(callbackUrl, apiUtil)

          // Check the response (except payloads)
          assertSiwaPayloadResponse(siwaPayload, phoneIdentifier)

          assertMigrationSiwaPayloads(GRAPH_SCHEMA_IDS, siwaPayload.payloads)
          submitSiwaSignedPayloads(secondProvider.client, siwaPayload)
        }

        // Look up the user's account to find their public key
        val userAccount = runBlocking {
          databaseService.findUserAccountByUserIdentifier(mapUserIdentifierToUserDetail(phoneIdentifier))!!
        }
        val userAccountKeyData = runBlocking {
          databaseService.findUserKeyDataByUserAccountIdAndKeyUsageType(userAccount.id!!, KeyUsageType.ACCOUNT).first()
        }
        val userPublicKeyBytes = fromHex(userAccountKeyData.publicKeyHex)

        // Assert that the user has delegated the correct permissions to both providers
        val client = secondProvider.client
        val universalAddress = publicKeyToUniversalAddress(userPublicKeyBytes, userAccountKeyData.encryptedPrivateKeyType)
        val userMsaId = client.getMsaIdByAccountId(universalAddress).join() ?: Assertions.fail("No MSA found for user!")

        val delegations = client.getAllGrantedDelegationsByMsaId(userMsaId).join()

        val expectedDelegations = listOf(
          DelegationItemResponse(provider.msaId, GRAPH_SCHEMA_IDS.map { SchemaGrantResponse(it, 0.toBigInteger()) }),
          DelegationItemResponse(secondProvider.msaId, GRAPH_SCHEMA_IDS.map { SchemaGrantResponse(it, 0.toBigInteger()) }),
        )
        Assertions.assertThat(delegations).containsExactlyInAnyOrderElementsOf(expectedDelegations)

        // Assert a provider external user row has been created for both delegations
        listOf(provider.msaId, secondProvider.msaId).forEach { providerMsaId ->
          val externalUser = runBlocking {
            databaseService.findProviderExternalUserByProviderMsaIdAndKeyPairTypeAndPublicKeyHexAndKeyUsageType(
              providerMsaId,
              userKeyPairType,
              userAccountKeyData.publicKeyHex,
              KeyUsageType.ACCOUNT
            )
          } ?: Assertions.fail("No external user row found for providerMsaId=$providerMsaId !")

          Assertions.assertThat(externalUser.userKeyDataId).isEqualTo(userAccountKeyData.id!!)
        }
      }
    }

    @BrowserTest
    fun siwaCommunityRewardsOptInSucceeds(engine: BrowserEngine) {
      pwe.createContext(engine).use { (_, page) ->
        val providerRequest = createProviderSiwaRequest(
          containers.frequency.wsAddress,
          keyPair,
          DEFAULT_PERMISSIONS,
          callbackUrl,
          userIdentifierAdminUrl,
          null,
          ApplicationContext(providerApplicationData.verifiedCredentialUrl)
        )

        setUpSiwaStartPage(page, providerRequest, objectMapper, host, userKeyPairType)
        completeNewUserFlowForEmail(engine, page, provider.client, providerRequest)

        page.navigate("$host/siwa/rewards")

        val response = page.waitForResponse("**/api/rewards/optIn") {
          page.getByTestId("opt-in-button").click()
        }

        Assertions.assertThat(response.status()).isEqualTo(204)
        assertThat(page.getByTestId("success-text")).isVisible()
      }
    }

    @Nested
    inner class IcsOAuthTests {
      @BrowserTest
      fun siwaIcsNewUserFlowWithEmailSucceeds(engine: BrowserEngine) {
        pwe.createContext(engine).use { (_, page) ->
          // The initiation process manipulates the browser in a way that causes some JS errors
          val providerRequest = createProviderSiwaRequest(
            containers.frequency.wsAddress,
            keyPair,
            emptyList(),
            callbackUrl,
            userIdentifierAdminUrl,
            null,
            ApplicationContext(providerApplicationData.verifiedCredentialUrl)
          )

          setUpSiwaStartPage(
            page,
            providerRequest,
            objectMapper,
            host,
            userKeyPairType,
            mapOf(
              "flow" to "ics"
            )
          )

          // Wait for the page to load
          assertThat(page.getByTestId("email-identifier-input")).isVisible()
          // Assert that the phone number toggle is not visible
          assertThat(page.getByTestId("switch-button")).isHidden()

          completeNewUserFlowForEmail(
            engine,
            page,
            provider.client,
            providerRequest,
            null, // No handle in the ICS flow (for now)
          )
        }
      }
    }
  }

  private fun emailVerificationSucceeds(engine: BrowserEngine, userEmail: String, page: Page) {
    val errors = collectJavaScriptErrors(engine, page)

    // Fill in user's email
    val emailInput = page.getByTestId("email-identifier-input")
    emailInput.fill(userEmail)

    val response = page.waitForResponse("**/siwa/verify") {
      page.getByTestId("email-identifier-submit").click()
    }

    val message = sesUtil.getLatestMessage(userEmail)
    val params = retrieveEmailParams(message)
    when(siwaEmailHandling) {
      SiwaEmailHandling.MAGIC_LINK -> {
        Assertions.assertThat(response.status()).isEqualTo(200)
        Assertions.assertThat(page.getByTestId("user-identifier-admin-url").getAttribute("href")).isEqualTo(userIdentifierAdminUrl)

        Assertions.assertThat(params).isNotNull

        // Check no JS errors were thrown or loggedfetchSiwaPayloadResponseForCallback
        Assertions.assertThat(errors).isEmpty()
      }

      SiwaEmailHandling.OTP -> {
        fillAuthenticationCodeAndSubmit(page, params.token)
      }
    }
  }

  fun completeNewUserFlowForEmail(
    engine: BrowserEngine,
    page: Page,
    client: FrequencyClient,
    providerRequest: SiwaRequest,
    handle: String? = userHandle
  ) {
    // NOTE(Julian, 2026-01-16): Hack for ICS auth flows
    val isIcsFlow = providerRequest.signatureRequest.payload.permissions.isEmpty()

    attachLoggerToPage(LOG, page)

    val errors = collectJavaScriptErrors(engine, page)

    // Fill in user's email
    val emailInput = page.getByTestId("email-identifier-input")
    emailInput.fill(userEmail)

    val response = page.waitForResponse("**/siwa/verify") {
      page.getByTestId("email-identifier-submit").click()
    }

    Assertions.assertThat(response.status()).isEqualTo(200)

    val message = sesUtil.getLatestMessage(userEmail)
    val (token, sessionId, _) = retrieveEmailParams(message)

    // Open email link
    page.navigate("$host/siwa/payloads?sessionId=$sessionId&token=$token")

    // Check no JS errors were thrown or logged so far--the index page encounters a TLS/SSL error in WebKit
    Assertions.assertThat(errors).isEmpty()

    val acceptedResponse = fillPayloadInputsAndSubmit(page, handle)

    val callback = if (isIcsFlow) {
      // Expect to be shown the 'submissionInProgress' page
      Assertions.assertThat(acceptedResponse.status()).isEqualTo(200)
      page.waitForURL { it.startsWith(callbackUrl) && it.contains("siwa/accepted") }

      // Then expect to be bounced to the callback URL
      page.waitForURL { it.startsWith(callbackUrl) && !it.contains("siwa") }

      page.url()
    } else {
      // Should be redirected to provider callback
      Assertions.assertThat(acceptedResponse.status()).isEqualTo(303)
      acceptedResponse.headerValue(HttpHeaders.LOCATION)
    }

    assertCallbackUrlCorrect(callback)

    val siwaPayload = fetchSiwaPayloadResponseForCallback(callback, apiUtil)

    // Check the response (except payloads)
    val expectedUserIdentifier = UserIdentifier(userEmail, UserIdentifierType.EMAIL)
    val graphKeyExpected = !isIcsFlow
    assertSiwaPayloadResponse(siwaPayload, expectedUserIdentifier, graphKeyExpected)

    if (isIcsFlow) {
      // The ICS flow always returns a 'login' response
      Assertions.assertThat(siwaPayload.userMsaId).isNotNull
      assertLoginSiwaPayloads(siwaPayload.payloads)
    } else {
      assertNewUserSiwaPayloads(
        userHandle,
        providerRequest.signatureRequest.payload.permissions,
        siwaPayload.payloads,
        graphKeyExpected
      )
      submitSiwaSignedPayloads(client, siwaPayload)
    }
  }

  private fun assertLoginSiwaPayloads(payloads: List<TypedPayloadResponseWithSignature<*>>, ) {
    val underlyingPayloads = payloads.map {
      it.payload
    }

    Assertions.assertThat(underlyingPayloads).hasExactlyElementsOfTypes(
      Caip122LoginPayloadResponse::class.java
    )

    for (underlyingPayload in underlyingPayloads) {
      when (underlyingPayload) {
        is Caip122LoginPayloadResponse -> {
          Assertions.assertThat(underlyingPayload.message).isNotNull()
        }
        else -> Assertions.fail("Invalid payload type: ${underlyingPayload::class}")
      }
    }
  }

  /** Does not check the payloads */
  private fun assertSiwaPayloadResponse(
    payloadResponse: SiwaPayloadResponse,
    expectedUserIdentifier: UserIdentifier,
    graphKeyExpected: Boolean = true,
  ) {
    val subjects = payloadResponse.credentials.map { credential -> credential.credentialSubject }
    when (expectedUserIdentifier.type) {
      UserIdentifierType.EMAIL -> {
        if (graphKeyExpected) {
          Assertions.assertThat(subjects).hasExactlyElementsOfTypes(
            CredentialSubject.Email::class.java,
            CredentialSubject.KeyPair::class.java
          )
        } else {
          Assertions.assertThat(subjects).hasExactlyElementsOfTypes(CredentialSubject.Email::class.java)
        }
      }

      UserIdentifierType.PHONE_NUMBER -> {
        if (graphKeyExpected) {
          Assertions.assertThat(subjects).hasExactlyElementsOfTypes(
            CredentialSubject.PhoneNumber::class.java,
            CredentialSubject.KeyPair::class.java
          )
        } else {
          Assertions.assertThat(subjects).hasExactlyElementsOfTypes(CredentialSubject.PhoneNumber::class.java)
        }
      }
    }

    payloadResponse.credentials.forEach { credential ->
      Assertions.assertThat(credential.context).containsExactly(
        "https://www.w3.org/ns/credentials/v2",
        "https://www.w3.org/ns/credentials/undefined-terms/v2"
      )
      Assertions.assertThat(credential.issuer).isEqualTo("did:web:localhost:8080")
      Assertions.assertThat(credential.validFrom)
        .isStrictlyBetween(ZonedDateTime.now().minus(validFromDelay.multipliedBy(2L)), ZonedDateTime.now())

      when (val subject = credential.credentialSubject) {
        is CredentialSubject.PhoneNumber -> {
          Assertions.assertThat(credential.type).containsExactly(
            CredentialType.VerifiedPhoneNumberCredential,
            CredentialType.VerifiableCredential
          )
          Assertions.assertThat(credential.credentialSchema).usingRecursiveComparison().isEqualTo(CredentialSchema.PHONE_NUMBER)
          Assertions.assertThat(subject.phoneNumber).isEqualTo(expectedUserIdentifier.value)
          Assertions.assertThat(subject.lastVerified)
            .isStrictlyBetween(ZonedDateTime.now().minusSeconds(5), ZonedDateTime.now())
        }

        is CredentialSubject.Email -> {
          Assertions.assertThat(credential.type).containsExactly(
            CredentialType.VerifiedEmailAddressCredential,
            CredentialType.VerifiableCredential
          )
          Assertions.assertThat(credential.credentialSchema).usingRecursiveComparison().isEqualTo(CredentialSchema.EMAIL_ADDRESS)
          Assertions.assertThat(subject.emailAddress).isEqualTo(expectedUserIdentifier.value)
          Assertions.assertThat(subject.lastVerified)
            .isStrictlyBetween(ZonedDateTime.now().minusSeconds(5), ZonedDateTime.now())
        }

        is CredentialSubject.KeyPair -> {
          Assertions.assertThat(credential.type).containsExactly(
            CredentialType.VerifiedGraphKeyCredential,
            CredentialType.VerifiableCredential
          )
          Assertions.assertThat(credential.credentialSchema).usingRecursiveComparison().isEqualTo(CredentialSchema.KEY_PAIR)
          Assertions.assertThat(subject)
            .usingRecursiveComparison()
            .ignoringFields("id", "encodedPublicKeyValue", "encodedPrivateKeyValue")
            .isEqualTo(
              CredentialSubject.KeyPair(
                // A new random account key pair is generated during every run
                "did:key:z???",
                // A new random graph pair is generated during every run
                "???",
                "???",
                KeyPairEncoding.BASE_16,
                KeyPairFormat.BARE,
                KeyPairType.X25519,
                DsnpKeyType.PublicKeyKeyAgreement
              )
            )
        }
      }

      // Verify the credential against the application's Ed25519 public key
      val applicationPublicKey = Ed25519SignatureManager.newKeyPairFromSeed(fromHex(ed25519SeedHex)).publicKey
      Assertions.assertThat(verifiableCredentialAuthenticator.verify(credential, applicationPublicKey)).isTrue()
    }
  }
}