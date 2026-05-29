package io.amplica.custodial_wallet

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.strategyobject.substrateclient.crypto.ss58.SS58AddressFormat
import io.amplica.custodial_wallet.client.kms.EncryptedKey
import io.amplica.custodial_wallet.client.kms.KmsDecryptionKey
import io.amplica.custodial_wallet.client.kms.KmsEncryptionAlgorithm
import io.amplica.custodial_wallet.client.redis.CustodialWalletRedisClient
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifier
import io.amplica.custodial_wallet.conf.BeanNames
import io.amplica.custodial_wallet.container.CustodialWalletE2ETestStack
import io.amplica.custodial_wallet.controller.util.BooleanHolder
import io.amplica.custodial_wallet.controller.util.PublicKeyAndChainStateRequest
import io.amplica.custodial_wallet.controller.util.PublicKeyAndChainStateResponse
import io.amplica.custodial_wallet.db.repository.*
import io.amplica.custodial_wallet.dto.*
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiErrorDto
import io.amplica.custodial_wallet.orchestration.signing.SigningOrchestrationService
import io.amplica.custodial_wallet.orchestration.siwa.*
import io.amplica.custodial_wallet.siggen.SignatureGenerator.Companion.testKeyPair
import io.amplica.custodial_wallet.util.*
import io.amplica.custodial_wallet.util.key_creation.*
import io.amplica.custodial_wallet.web.AUTHORIZATION_CODE_PARAMETER_NAME
import io.amplica.custodial_wallet.web.CookieHelper
import io.amplica.custodial_wallet.web.SESSION_ID_COOKIE_NAME
import io.amplica.frequency.client.SpRuntimeMultiSignatureType
import io.amplica.frequency.signing_service.AddProviderPayload
import io.amplica.frequency.util.arrow.getOrThrow
import io.netty.handler.codec.http.cookie.ClientCookieDecoder
import io.netty.handler.codec.http.cookie.DefaultCookie
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import org.apache.http.entity.ContentType
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.getForEntity
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.*
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.util.UriComponentsBuilder
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.shaded.com.google.common.collect.FluentIterable
import java.math.BigInteger
import java.net.URI
import java.time.Instant
import java.util.*
import org.assertj.core.api.Assertions as Assertj


@CustodialWalletE2ESpringTestConfiguration
@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
class CustodialWalletApplicationTests(
  @Qualifier(BeanNames.DB_UTIL) private val dbUtil: DbUtil,
) {
  @Autowired
  lateinit var userAccountRepository: ReactiveUserAccountRepository
  @Autowired
  lateinit var userKeyDataRepository: ReactiveUserKeyDataRepository
  @Autowired
  lateinit var providerExternalUserRepository: ReactiveProviderExternalUserRepository
  @Autowired
  lateinit var providerExternalUserDetailRepository: ReactiveProviderExternalUserDetailRepository

  @Autowired
  lateinit var databaseService: CustodialWalletDatabaseService
  @Autowired
  lateinit var cookieHelper: CookieHelper
  @Autowired
  lateinit var signingOrchestrationService: SigningOrchestrationService
  @Autowired
  lateinit var siwaOrchestrationService: SiwaOrchestrationService

  @Autowired
  lateinit var testRestTemplate: TestRestTemplate

  @Autowired
  lateinit var redisClient: CustodialWalletRedisClient

  @Value("\${unfinished.custodial-wallet.admin.access.token}")
  private lateinit var accessToken: String
  @Value("\${unfinished.custodial-wallet.admin.shared.secret}")
  private lateinit var sharedSecret: String
  @Value("\${unfinished.custodial-wallet.admin.shared.secret.rebuild.signup.payload}")
  private lateinit var sharedSecretRebuildSignupPayload: String
  // This value is overridden by `LocalStackTestContainer` to point to the local SES
  @Value("\${unfinished.custodial-wallet.aws-ses.service_endpoint}")
  private lateinit var sesEndpoint: String

  companion object {
    private val LOG = LoggerFactory.getLogger(CustodialWalletApplicationTests::class.java)
    // Utilities
    private val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    // Useful constants
    private const val SHARED_SECRET_PARAMETER_NAME = "shared_secret"

    // Frequency
    // NOTE(Julian, 2024-08-20): These values are not correct. 7-10 are the graph schemas and there is no ID greater than 11.
    private val DEFAULT_SCHEMA_IDS = listOf(5, 7, 8, 9, 10)

    // User example data
    private const val PROVIDER_EXTERNAL_USER_ID = "SomeExternalUserId"
    private const val TEST_EMAIL_ADDRESS = "joshua.rothfus@unfinished.com"
    private val emailUserIdentifier = UserIdentifier(TEST_EMAIL_ADDRESS, UserIdentifierType.EMAIL)
    private const val BASE_HANDLE = "teddy"


    private val userKeyPair = Sr25519KeyPairCreator.createKeyPair()

    private val mockNotificationService = MockNotificationService()
    private const val USER_IDENTIFIER_ADMIN_URL = "https://www.mewe.com"
    private val REGISTRATION_DATA_EMAIL = UserRegistrationData(EMAIL_IDENTIFIER, BASE_HANDLE, USER_IDENTIFIER_ADMIN_URL)
    private val REGISTRATION_DATA_SMS = UserRegistrationData(SMS_IDENTIFIER, BASE_HANDLE, USER_IDENTIFIER_ADMIN_URL)


    @Container
    val containers = CustodialWalletE2ETestStack()

    @DynamicPropertySource
    @JvmStatic
    fun registerContainerProperties(registry: DynamicPropertyRegistry) {
      registry.add("unfinished.custodial-wallet.notification-service.service_endpoint") {
        "http://localhost:${mockNotificationService.port()}"
      }
      containers.registerDynamicProperties(registry)
    }
  }

  @BeforeEach
  fun setup() {
    dbUtil.saveOrganizationData()
    /*
    This is needed to have the same set up as Spring Boot 3.3.9, otherwise the test needs to change and it wasn't at all
    clear to me why, it's like a strange quirck of the SimpleClientHttpRequestFactory cause I'd say the ReactorClientHttpRequestFactory
    is working correct in that it will ALWAYS redirect and for whatever reason the SimpleClientHttpRequestFactory doesn't redirect for the
    givenProperBatchPayloadToSignRequest_whenBatchSigningRequest_BatchSuccessfullySigned test but it does for others...
     */
    val simpleClientHttpRequestFactory = ClientHttpRequestFactoryBuilder.of(SimpleClientHttpRequestFactory::class.java).build()
    val restTemplateBuilder = (RestTemplateBuilder({
      it.requestFactory = simpleClientHttpRequestFactory
    }).rootUri(testRestTemplate.rootUri))
    testRestTemplate = TestRestTemplate(restTemplateBuilder)
  }

  @AfterEach
  fun tearDown() {
    dbUtil.deleteFromAllTables()
  }

  @Test
  fun healthcheck() {
    val response = testRestTemplate.getForEntity("/actuator/health", String::class.java)
    Assertions.assertEquals(HttpStatus.OK, response.statusCode)

    @Suppress("UNCHECKED_CAST")
    val map: Map<String, Any> = jacksonObjectMapper().readValue(response.body.toString(), Map::class.java) as Map<String, Any>

    @Suppress("UNCHECKED_CAST")
    val components = map["components"] as Map<String, Map<String, *>>

    Assertions.assertTrue(components.containsKey("chain"))
    Assertions.assertTrue(components.containsKey("sesClient"))
    Assertions.assertTrue(components.containsKey("kmsClient"))

    Assertions.assertTrue(components.containsKey("accountService"))
    Assertions.assertTrue(components["accountService"]?.get("status") == "UP")
  }

  @Test
  fun chainHealthcheck() {
    val response = testRestTemplate.getForEntity("/actuator/health/chain", String::class.java)
    Assertions.assertEquals(HttpStatus.OK, response.statusCode)
  }

  @Test
  fun kmsClientHealthcheck() {
    val response = testRestTemplate.getForEntity("/actuator/health/kmsClient", String::class.java)
    Assertions.assertEquals(HttpStatus.OK, response.statusCode)
  }


  @Test
  fun swagger() {
    val response = testRestTemplate.getForEntity("/api", String::class.java)
    Assertions.assertEquals(HttpStatus.OK, response.statusCode)
  }

  @Test
  fun prometheus() {
    val response = testRestTemplate.getForEntity("/actuator/prometheus", String::class.java)
    Assertions.assertEquals(HttpStatus.OK, response.statusCode)
  }

  /**
   * E2E method for creating an account through siwa, Then DirectLogin can be used.
   */

  private suspend fun siwaRegisterUser(userRegistrationData: UserRegistrationData): SiwaPayloadResponse {
    val siwaPayloadResponse = siwaRegisterNewUserOrchestration(
      siwaOrchestrationService,
      containers.frequency,
      redisClient, userRegistrationData
    )

    val userDetail = UserDetail.fromUserIdentifier(userRegistrationData.userIdentifier)
    val account = databaseService.findOneUserAccountByUserIdentifiers(listOf(userDetail))
    Assertions.assertNotNull(account)

    return siwaPayloadResponse
  }

  /**
   * E2E method for direct Email login to website, requires an account to have already been created with userIdentifier
   */
  private suspend fun websiteEmailLogin(userIdentifier: UserIdentifier) : String {

    //// STEP 1 - The aliceClient requests the email signup page and is given a session ID
    // Given
    val headers = createDefaultHttpHeaders()
    val loginPageUri = URI("/")

    // When
    val loginPageResponse: ResponseEntity<String> =
      testRestTemplate.getForEntity(loginPageUri, String::class.java)

    // Then
    Assertions.assertEquals(HttpStatus.OK, loginPageResponse.statusCode)

    //// STEP 2 - Email the user with a link (embedded with a token)
    // Given
    val directLoginRequest = DirectLoginRequest(userIdentifier.value, UserIdentifierType.EMAIL, null)
    val loginApiRequest: HttpEntity<DirectLoginRequest> = HttpEntity(directLoginRequest, headers)
    val loginApiUri = URI("/api/login/direct")

    // When
    val loginApiResponse: ResponseEntity<Boolean> =
      testRestTemplate.postForEntity(loginApiUri, loginApiRequest, Boolean::class.java)
    val responseHeaders = loginApiResponse.headers["Set-Cookie"]

    // Then
    Assertions.assertEquals(HttpStatus.OK, loginApiResponse.statusCode)
    Assertions.assertEquals(true, loginApiResponse.body)

    val cookieMap: Map<String, DefaultCookie> = FluentIterable.from(responseHeaders!!).transform { ClientCookieDecoder.LAX.decode(it) as DefaultCookie }.uniqueIndex { it?.name() }
    val sessionIdCookie = cookieMap[SESSION_ID_COOKIE_NAME]
    Assertions.assertNotNull(sessionIdCookie)
    assertSessionIdCookie(sessionIdCookie)

    val headersWithCookie = createDefaultHttpHeaders()
    headersWithCookie.addAll("Cookie", responseHeaders)

    val result = getLastSesMessage(sesEndpoint)

    Assertions.assertNotNull(result)
    val templateData = result.TemplateData
    Assertions.assertNotNull(templateData)
    val code = extractQueryParamFromUrl(templateData as String, "authenticationCode")
    val sessionId = sessionIdCookie!!.value()

    //// STEP 3 - The proper token is sent back

    // Given
    val authUri = URI("/web/login/email?sessionId=${sessionId}&authenticationCode=${code}")

    // When
    val authenticateLoginResponse = testRestTemplate.exchange(authUri.toString(), HttpMethod.GET, HttpEntity<String>(headersWithCookie), String::class.java)
    val loggedInSessionId = checkAndGetSessionIdFromCookieInHeaders(authenticateLoginResponse.headers)
    Assertions.assertNotEquals(sessionId, loggedInSessionId)

    // Then
    Assertions.assertEquals(HttpStatus.OK,authenticateLoginResponse.statusCode)
    Assertions.assertNotNull(authenticateLoginResponse.body)
    Assertions.assertTrue(authenticateLoginResponse.body!!.contains("My Account"))

    return loggedInSessionId
  }

  /**
   * E2E method for direct Sms login to website, requires an account to have already been created with userIdentifier
   */
  private suspend fun websiteSmsLogin(userIdentifier: UserIdentifier) : String{
    //// STEP 1 - The aliceClient requests the login page and is given a sessionID
    // Given
    val headers = createDefaultHttpHeaders()
    val loginPageUri = URI("/")

    // When
    val loginPageResponse: ResponseEntity<String> =
      testRestTemplate.getForEntity(loginPageUri, String::class.java)

    // Then
    Assertions.assertEquals(HttpStatus.OK, loginPageResponse.statusCode)

    //// STEP 2 - Send sms to the user with authentication code
    // Given
    val directLoginRequest = DirectLoginRequest(userIdentifier.value, UserIdentifierType.PHONE_NUMBER, null)
    val loginApiRequest: HttpEntity<DirectLoginRequest> = HttpEntity(directLoginRequest, headers)
    val loginApiUri = URI("/api/login/direct")

    // When
    val loginApiResponse: ResponseEntity<Boolean> =
      testRestTemplate.postForEntity(loginApiUri, loginApiRequest, Boolean::class.java)
    val responseHeaders = loginApiResponse.headers["Set-Cookie"]

    // Then
    Assertions.assertEquals(HttpStatus.OK, loginApiResponse.statusCode)
    Assertions.assertEquals(false, loginApiResponse.body)

    val cookieMap: Map<String, DefaultCookie> = FluentIterable.from(responseHeaders!!).transform { ClientCookieDecoder.LAX.decode(it) as DefaultCookie }.uniqueIndex { it?.name() }
    val sessionIdCookie = cookieMap[SESSION_ID_COOKIE_NAME]
    Assertions.assertNotNull(sessionIdCookie)
    assertSessionIdCookie(sessionIdCookie)

    val headersWithCookie = HttpHeaders()
    headersWithCookie.set(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
    headersWithCookie.set(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_FORM_URLENCODED.toString())
    headersWithCookie.addAll("Cookie", responseHeaders)
    val sessionId = sessionIdCookie!!.value()

    val websiteSession = redisClient.findWebsiteSessionBySessionId(sessionId)
    val code: String = websiteSession!!.authenticationCode!!

    //// STEP 3 - User sends proper auth code, is verified, and goes to user data table page

    val body = "smsCode=${code}"
    // Given
    val httpEntity = HttpEntity(body, headersWithCookie)
    val authUri = URI("/web/login/sms")

    // When
    val authenticateLoginResponse = testRestTemplate.postForEntity(authUri.toString(), httpEntity, String::class.java)
    val loginSessionId = checkAndGetSessionIdFromCookieInHeaders(authenticateLoginResponse.headers)
    Assertions.assertNotEquals(sessionId, loginSessionId)

    // Then
    Assertions.assertEquals(HttpStatus.OK,authenticateLoginResponse.statusCode)
    Assertions.assertNotNull(authenticateLoginResponse.body)
    Assertions.assertTrue(authenticateLoginResponse.body!!.contains("My Account"))

    return loginSessionId


  }

  @Nested
  @DisplayName("Account Tests")
  inner class AccountTests {

    @Nested
    @DisplayName("Email Tests")
    inner class EmailTests {

      private lateinit var siwaPayloadResponse: SiwaPayloadResponse
      private lateinit var loginSessionId: String

      @BeforeEach
      fun setUpSmsTests(): Unit = runBlocking {
        siwaPayloadResponse = siwaRegisterUser(REGISTRATION_DATA_EMAIL)
        submitSiwaSignedPayloads(containers.frequency.aliceProviderClient, siwaPayloadResponse)
        loginSessionId = websiteEmailLogin(REGISTRATION_DATA_EMAIL.userIdentifier)
      }

      @Test
      fun addNewEmailContact(): Unit = runBlocking {
        // STEP 1: User inputs new email for provider and email is sent to new email
        // Given
        val headers = createDefaultHttpHeaders()
        headers.add("Cookie", cookieHelper.createResponseCookie(loginSessionId).toString())
        val firstWebsiteSession = redisClient.findWebsiteSessionBySessionId(loginSessionId)
        val addEmailBody = AddIdentifierRequest(firstWebsiteSession?.userAccountIds!![0], "test@email.com")
        val addEmailHttpEntity = HttpEntity(addEmailBody, headers)

        // When
        val handleAddEmailResponse = testRestTemplate.postForEntity(
          URI("/api/account/contact/email/verify"),
          addEmailHttpEntity,
          BooleanHolder::class.java
        )

        // Then
        Assertions.assertEquals(HttpStatus.ACCEPTED, handleAddEmailResponse.statusCode)
        Assertions.assertEquals(handleAddEmailResponse.body!!.response, true)

        // STEP 2: User receives new email and clicks verify
        // Given
        val cookieMap: Map<String, DefaultCookie> =
          FluentIterable.from(headers["Cookie"]!!).transform { ClientCookieDecoder.LAX.decode(it) as DefaultCookie }
            .uniqueIndex { it?.name() }
        val sessionIdCookie = cookieMap[SESSION_ID_COOKIE_NAME]
        val sessionId = sessionIdCookie!!.value()

        val websiteSession = redisClient.findWebsiteSessionBySessionId(sessionId)
        val code: String = websiteSession!!.verificationCode!!

        val authUri = URI("/web/add/email?sessionId=${sessionId}&verificationCode=${code}")

        // When
        val handleVerificationLinkResponse =
          testRestTemplate.exchange(authUri.toString(), HttpMethod.GET, HttpEntity<Unit>(headers), String::class.java)

        // Then
        Assertions.assertEquals(HttpStatus.OK, handleVerificationLinkResponse.statusCode)
        Assertions.assertNotNull(handleVerificationLinkResponse.body)
        Assertions.assertTrue(handleVerificationLinkResponse.body!!.contains("My Account"))
        Assertions.assertTrue(handleVerificationLinkResponse.body!!.contains("test@email.com"))
      }

      @Test
      fun revokeEmailDelegation(): Unit = runBlocking {
        val aliceProviderMsa = containers.frequency.aliceProviderMsaId

        val headers = createDefaultHttpHeaders()
        headers.add("Cookie", cookieHelper.createResponseCookie(loginSessionId).toString())
        val revokeDelegationUri = URI("/api/account/delegations/provider/$aliceProviderMsa")
        val revokeDelegationResponse: ResponseEntity<Boolean> = testRestTemplate.exchange(
          revokeDelegationUri,
          HttpMethod.DELETE,
          HttpEntity(null, headers),
          Boolean::class.java
        )
        Assertions.assertEquals(revokeDelegationResponse.body, true)
      }

      @Test
      fun logoutEmailUser(): Unit = runBlocking {
        val previousSession = redisClient.findWebsiteSessionBySessionId(loginSessionId)
        Assertions.assertNotNull(previousSession)
        val headers = createDefaultHttpHeaders()
        headers.add("Cookie", cookieHelper.createResponseCookie(loginSessionId).toString())
        val logoutResponse =
          testRestTemplate.exchange("/web/logout", HttpMethod.GET, HttpEntity<Unit>(headers), String::class.java)

        Assertions.assertEquals(200, logoutResponse.statusCode.value())
        Assertions.assertNotNull(logoutResponse.body)
        Assertions.assertTrue(logoutResponse.body!!.contains("Login"))
        val currentSession = redisClient.findWebsiteSessionBySessionId(loginSessionId)
        Assertions.assertNull(currentSession)
      }
    }

    @Nested
    @DisplayName("Sms Tests")
    inner class SmsTests {
      private lateinit var siwaPayloadResponse: SiwaPayloadResponse
      private lateinit var loginSessionId: String

      @BeforeEach
      fun setUpSmsTests(): Unit = runBlocking {
        siwaPayloadResponse = siwaRegisterUser(REGISTRATION_DATA_SMS)
        submitSiwaSignedPayloads(containers.frequency.aliceProviderClient, siwaPayloadResponse)
        loginSessionId = websiteSmsLogin(REGISTRATION_DATA_SMS.userIdentifier)
      }


      @Test
      fun addNewSmsContact(): Unit = runBlocking {
        // STEP 1:
        // Given
        val headers = createDefaultHttpHeaders()
        headers.add("Cookie", cookieHelper.createResponseCookie(loginSessionId).toString())
        val firstWebsiteSession = redisClient.findWebsiteSessionBySessionId(loginSessionId)
        val addSmsBody = AddIdentifierRequest(firstWebsiteSession!!.userAccountIds!![0], "+12133211234" )
        val addSmsHttpEntity = HttpEntity(addSmsBody, headers)

        // When
        val handleAddSmsResponse = testRestTemplate.postForEntity(URI("/api/account/contact/sms/verify"), addSmsHttpEntity, BooleanHolder::class.java)

        // Then
        Assertions.assertEquals(HttpStatus.ACCEPTED, handleAddSmsResponse.statusCode)
        Assertions.assertEquals(handleAddSmsResponse.body!!.response, true)

        headers.set(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_FORM_URLENCODED.toString())

        val cookieMap: Map<String, DefaultCookie> = FluentIterable.from(headers["Cookie"]!!).transform { ClientCookieDecoder.LAX.decode(it) as DefaultCookie }.uniqueIndex { it?.name() }
        val sessionIdCookie = cookieMap[SESSION_ID_COOKIE_NAME]
        val sessionId = sessionIdCookie!!.value()

        val websiteSession = redisClient.findWebsiteSessionBySessionId(sessionId)
        val code: String = websiteSession!!.verificationCode!!

        val body = "smsCode=${code}"
        val newAddSmsHttpEntity = HttpEntity(body, headers)
        val authUri = URI("/web/add/sms")

        // When
        val handleVerificationSmsResponse = testRestTemplate.postForEntity(authUri.toString(), newAddSmsHttpEntity, String::class.java)

        // Then
        Assertions.assertEquals(HttpStatus.OK,handleVerificationSmsResponse.statusCode)
        Assertions.assertNotNull(handleVerificationSmsResponse.body)
        Assertions.assertTrue(handleVerificationSmsResponse.body!!.contains("My Account"))
        Assertions.assertTrue(handleVerificationSmsResponse.body!!.contains("+12133211234"))
      }

      @Test
      fun logoutSmsUser(): Unit = runBlocking {
        val previousSession = redisClient.findWebsiteSessionBySessionId(loginSessionId)
        Assertions.assertNotNull(previousSession)
        val headers = createDefaultHttpHeaders()
        headers.add("Cookie", cookieHelper.createResponseCookie(loginSessionId).toString())
        val logoutResponse = testRestTemplate.exchange("/web/logout", HttpMethod.GET, HttpEntity<Unit>(headers), String::class.java)

        Assertions.assertEquals(200, logoutResponse.statusCode.value())
        Assertions.assertNotNull(logoutResponse.body)
        Assertions.assertTrue(logoutResponse.body!!.contains("Login"))
        val currentSession = redisClient.findWebsiteSessionBySessionId(loginSessionId)
        Assertions.assertNull(currentSession)
      }

      @Test
      fun revokeDelegationAfterSmsSignup(): Unit = runBlocking {

        val aliceProviderMsa = containers.frequency.aliceProviderMsaId

        val headers = createDefaultHttpHeaders()
        headers.add("Cookie", cookieHelper.createResponseCookie(loginSessionId).toString())
        val revokeDelegationUri = URI("/api/account/delegations/provider/$aliceProviderMsa")
        val revokeDelegationResponse: ResponseEntity<Boolean> = testRestTemplate.exchange(
          revokeDelegationUri,
          HttpMethod.DELETE,
          HttpEntity(null, headers),
          Boolean::class.java
        )
        Assertions.assertEquals(revokeDelegationResponse.body, true)
      }


    }
  }

  @Nested
  @DisplayName("Exception Tests")
  inner class ExceptionTests {
    @Test
    fun apiException() {
      val apiError = ApiError.fromId(1)
      val response = testRestTemplate.getForEntity("/exception/1", ApiErrorDto::class.java)
      Assertions.assertEquals(apiError.httpStatus, response.statusCode)
      Assertions.assertEquals(1, response.body!!.id)
      Assertions.assertEquals(apiError.description, response.body!!.description)
    }

    @Test
    fun nonApiException() {
      val apiError = ApiError.fromId(0)
      val response = testRestTemplate.getForEntity("/exception/catchAll", ApiErrorDto::class.java)
      Assertions.assertEquals(apiError.httpStatus, response.statusCode)
      Assertions.assertEquals(0, response.body!!.id)
      Assertions.assertEquals(apiError.description, response.body!!.description)
    }
  }

  @Nested
  @DisplayName("Lookup tests")
  inner class LookupTests {
    private lateinit var publicKeyDto: PublicKeyDto
    private lateinit var notFoundPublicKeyDto: PublicKeyDto
    private lateinit var graphPublicKeyDto: PublicKeyDto
    private val keyPair2 = Sr25519KeyPairCreator.createKeyPair()
    private val testUserIdentifier = UserIdentifier("someEmail@example.com", UserIdentifierType.EMAIL)
    private val testUserIdentifierNotFound = UserIdentifier("someEmail2@example.com", UserIdentifierType.EMAIL)

    @BeforeEach
    fun setUp() {
      val keyPair = userKeyPair
      val graphKeyPair = X25519KeyPairCreator.createKeyPair()
      val graphKeyPair2 = X25519KeyPairCreator.createKeyPair()
      val sr25519KeyPair =
        Sr25519KeyPairBytes(keyPair.publicKeyBytes, keyPair.privateKeyBytes, KeyPairSignatureAlgorithm.SR25519)
      publicKeyDto = Sr25519KeyPairCreator.createSr25519PublicKeyDto(sr25519KeyPair, SS58AddressFormat.SUBSTRATE_ACCOUNT)
      notFoundPublicKeyDto = Sr25519KeyPairCreator.createSr25519PublicKeyDto(keyPair2, SS58AddressFormat.SUBSTRATE_ACCOUNT)
      graphPublicKeyDto = X25519KeyPairCreator.createX25519PublicKeyDtoBareFormat(graphKeyPair)
      runBlocking {
        val userAccount = userAccountRepository.save(UserAccount.create()).awaitSingle()
        Assertions.assertNotNull(userAccount.id)
        if(userAccount.id != null) {
          val userKeyData = userKeyDataRepository.save(
            UserKeyData.create(
              userAccount.id!!,
              keyPair.publicKeyBytes,
              EncryptedKey(keyPair.privateKeyBytes, KmsDecryptionKey("someKeyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)),
              KeyPairType.SR25519,
              KeyUsageType.ACCOUNT
            )
          ).awaitSingle()
          Assertions.assertNotNull(userKeyData.id)
          val now = BigInteger.valueOf(Instant.now().toEpochMilli())
          val providerExternalUser = ProviderExternalUser(containers.frequency.aliceProviderMsaId, "someExternalId", userKeyData.id!!, now, now)
          val savedProviderExternalUser = providerExternalUserRepository.save(providerExternalUser).awaitSingle()

          databaseService.saveUserIdentifierAndProviderExternalUserDetail(
            savedProviderExternalUser.id!!,
            userAccount.id!!,
            UserDetail(
              testUserIdentifier.value, UserDetailType.valueOf(testUserIdentifier.type.toString()), 1
            ),
          )

          val graphUserKeyData = userKeyDataRepository.save(
            UserKeyData.create(
              userAccount.id!!,
              graphKeyPair.publicKeyBytes,
              EncryptedKey(graphKeyPair.privateKeyBytes, KmsDecryptionKey("someKeyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)),
              KeyPairType.X25519,
              KeyUsageType.GRAPH
            )
          ).awaitSingle()
          Assertions.assertNotNull(graphUserKeyData.id)
        }

        val userAccount2 = userAccountRepository.save(UserAccount.create()).awaitSingle()
        Assertions.assertNotNull(userAccount2.id)
        if(userAccount2.id != null) {
          val userKeyData = userKeyDataRepository.save(
            UserKeyData.create(
              userAccount.id!!,
              keyPair2.publicKeyBytes,
              EncryptedKey(keyPair2.privateKeyBytes, KmsDecryptionKey("someKeyId2", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)),
              KeyPairType.SR25519,
              KeyUsageType.ACCOUNT
            )
          ).awaitSingle()
          Assertions.assertNotNull(userKeyData.id)
          val now = BigInteger.valueOf(Instant.now().toEpochMilli())
          val providerExternalUser =
            ProviderExternalUser(containers.frequency.aliceProviderMsaId, "someExternalId2", userKeyData.id!!, now, now)
          val savedProviderExternalUser = providerExternalUserRepository.save(providerExternalUser).awaitSingle()

          databaseService.saveUserIdentifierAndProviderExternalUserDetail(
            savedProviderExternalUser.id!!,
            userAccount2.id!!,
            UserDetail(
              testUserIdentifierNotFound.value, UserDetailType.valueOf(testUserIdentifierNotFound.type.toString()), 1
            ),
          )

          val graphUserKeyData = userKeyDataRepository.save(
            UserKeyData.create(
              userAccount2.id!!,
              graphKeyPair2.publicKeyBytes,
              EncryptedKey(graphKeyPair.privateKeyBytes, KmsDecryptionKey("someKeyId2", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)),
              KeyPairType.X25519,
              KeyUsageType.GRAPH
            )
          ).awaitSingle()
          Assertions.assertNotNull(graphUserKeyData.id)
        }
      }

    }

    @Test
    fun findPublicKeys() {
      //GIVEN
      val keyPair = Sr25519KeyPairCreator.createKeyPair()
      val graphKeyPair = X25519KeyPairCreator.createKeyPair()
      val publicKeyRequestFound = PublicKeyRequest(publicKeyDto)
      val publicKeyRequestNotFound = PublicKeyRequest(Sr25519KeyPairCreator.createSr25519PublicKeyDto(keyPair, SS58AddressFormat.SUBSTRATE_ACCOUNT))
      val publicKeyRequestFound2 = PublicKeyRequest(graphPublicKeyDto)
      val publicKeyRequestNotFound2 = PublicKeyRequest(X25519KeyPairCreator.createX25519PublicKeyDtoBareFormat(graphKeyPair))

      val publicKeysRequest = PublicKeysRequest(listOf(publicKeyRequestFound, publicKeyRequestNotFound, publicKeyRequestFound2, publicKeyRequestNotFound2 ))
      println(mapper.writeValueAsString(publicKeysRequest))
      val httpEntity = HttpEntity(publicKeysRequest, createDefaultHttpHeaders())
      //WHEN
      val publicKeysResponseResponseEntity = testRestTemplate.postForEntity("/api/publicKey/admin/find?access_token={access_token}", httpEntity, PublicKeysResponse::class.java, mapOf("access_token" to accessToken))

      //THEN
      Assertions.assertEquals(200, publicKeysResponseResponseEntity.statusCode.value())

      val publicKeysResponse = publicKeysResponseResponseEntity.body
      Assertions.assertNotNull(publicKeysResponse)
      val publicKeyResponseList = publicKeysResponse?.publicKeys
      Assertions.assertNotNull(publicKeyResponseList)
      if (publicKeyResponseList != null) {
        Assertions.assertEquals(4, publicKeyResponseList.size)
        val publicKeyResponseFound = publicKeyResponseList[0]
        Assertions.assertTrue(publicKeyResponseFound.isPresent)
        Assertions.assertEquals(publicKeyRequestFound.publicKey, publicKeyResponseFound.publicKey)
        val publicKeyResponseNotFound = publicKeyResponseList[1]
        Assertions.assertFalse(publicKeyResponseNotFound.isPresent)
        Assertions.assertEquals(publicKeyRequestNotFound.publicKey, publicKeyResponseNotFound.publicKey)
        val publicKeyResponseFound2 = publicKeyResponseList[2]
        Assertions.assertTrue(publicKeyResponseFound2.isPresent)
        Assertions.assertEquals(publicKeyRequestFound2.publicKey, publicKeyResponseFound2.publicKey)
        val publicKeyResponseNotFound2 = publicKeyResponseList[3]
        Assertions.assertFalse(publicKeyResponseNotFound2.isPresent)
        Assertions.assertEquals(publicKeyRequestNotFound2.publicKey, publicKeyResponseNotFound2.publicKey)
      }

    }

    @Test
    fun getFinalizedHeadBlockNumber() {
      val response = testRestTemplate.getForEntity("/api/chain/finalizedHead/blockNumber", FinalizedHeadNumberResponse::class.java)
      Assertions.assertEquals(200, response.statusCode.value())
      Assertions.assertNotNull(response.body)
      Assertions.assertTrue(response.body!!.finalizedHeadNumber > 0.toBigInteger())
    }

    @Test
    fun getLatestBlockNumber() {
      val response = testRestTemplate.getForEntity("/api/chain/latest/blockNumber", LatestBlockNumberResponse::class.java)
      Assertions.assertEquals(200, response.statusCode.value())
      Assertions.assertNotNull(response.body)
      Assertions.assertTrue(response.body!!.latestBlockNumber > 0.toBigInteger())
    }

    @Test
    fun getPublicKeyAndChainStateNotOnChain() {
      //GIVEN
      val request = PublicKeyAndChainStateRequest(containers.frequency.aliceProviderPublicKeyDto, testUserIdentifierNotFound)
      println(mapper.writeValueAsString(request))
      val requestEntity = HttpEntity<PublicKeyAndChainStateRequest>(request, createDefaultHttpHeaders())

      //WHEN
      val responseEntity = testRestTemplate.postForEntity("/api/publicKey/admin/state?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}", requestEntity, PublicKeyAndChainStateResponse::class.java, mapOf(SHARED_SECRET_PARAMETER_NAME to sharedSecretRebuildSignupPayload))

      //THEN
      val response = responseEntity.body
      Assertions.assertNotNull(response)
      if(response != null) {
        Assertions.assertEquals(notFoundPublicKeyDto, response.userPublicKey)
        Assertions.assertEquals(false, response.existsOnChain)
      }
    }

    @Test
    fun getPublicKeyAndChainState() = runBlocking {
      //GIVEN
      val providerMsaId = containers.frequency.aliceProviderMsaId
      val currentBlockNumber = containers.frequency.aliceProviderClient.getLastBlockNumber().join().toLong()
      val expirationBlockNumber = currentBlockNumber + 10L

      val payloadRequest = io.amplica.frequency.payload.AddProviderPayload(
        providerMsaId,
        DEFAULT_SCHEMA_IDS,
        expirationBlockNumber
      )
      val signature = signingOrchestrationService.signPayload(userKeyPair, payloadRequest)

      val createdMsaId = containers.frequency.aliceProviderClient.createSponsoredAccountWithDelegationWithCapacity(
        userKeyPair.publicKeyBytes,
        SpRuntimeMultiSignatureType.SR25519,
        fromHex(signature.encodedValue),
        AddProviderPayload(providerMsaId, DEFAULT_SCHEMA_IDS, expirationBlockNumber),
      ).join().getOrThrow()

      val universalAddress = publicKeyToUniversalAddress(userKeyPair.publicKeyBytes, userKeyPair.keyPairType)
      val userMsaId = containers.frequency.aliceProviderClient.getMsaIdByAccountId(universalAddress).join()

      Assertions.assertEquals(userMsaId, createdMsaId.delegator!!.value)

      val request = PublicKeyAndChainStateRequest(containers.frequency.aliceProviderPublicKeyDto, testUserIdentifier)
      val requestEntity = HttpEntity<PublicKeyAndChainStateRequest>(request, createDefaultHttpHeaders())

      //WHEN
      val responseEntity = testRestTemplate.postForEntity("/api/publicKey/admin/state?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}", requestEntity, PublicKeyAndChainStateResponse::class.java, mapOf(SHARED_SECRET_PARAMETER_NAME to sharedSecretRebuildSignupPayload))

      //THEN
      val response = responseEntity.body
      Assertions.assertNotNull(response)
      if(response != null) {
        Assertions.assertEquals(publicKeyDto, response.userPublicKey)
        Assertions.assertEquals(true, response.existsOnChain)
      }
    }
  }

  @Nested
  @DisplayName("Admin Tests")
  inner class AdminTests {
    private lateinit var providerExternalUserDetails: MutableList<ProviderExternalUserDetail>
    private lateinit var providerExternalUsers: MutableList<ProviderExternalUser>
    private lateinit var userAccounts: MutableList<UserAccount>

    @BeforeEach
    fun setUp() {
      providerExternalUserDetails = mutableListOf()
      providerExternalUsers = mutableListOf()
      userAccounts = mutableListOf()
    }

    fun createUserAccount(numberOfProviderExternalUsers: Int, numberOfProviderExternalUserDetails: Int): Unit = runBlocking {
      val graphKeyPair = X25519KeyPairCreator.createKeyPair()
      val userAccount = userAccountRepository.save(UserAccount.create()).awaitSingle()
      userAccounts.add(userAccount)
      Assertions.assertNotNull(userAccount.id)
      val kmsDecryptionKey = KmsDecryptionKey("someKeyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)

      //This is tied to UserAccount so we only need one
      val graphUserKeyData = userKeyDataRepository.save(
        UserKeyData.create(
          userAccount.id!!,
          graphKeyPair.publicKeyBytes,
          EncryptedKey(
            graphKeyPair.privateKeyBytes,
            kmsDecryptionKey
          ),
          KeyPairType.X25519,
          KeyUsageType.GRAPH
        )
      ).awaitSingle()
      graphUserKeyData.userAccount = userAccount
      Assertions.assertNotNull(graphUserKeyData.id)

      for (currentProviderExternalUserSuffix in 1..numberOfProviderExternalUsers) {
        val providerExternalUserKeyPair = Sr25519KeyPairCreator.createKeyPair()
        val userKeyData = userKeyDataRepository.save(
          UserKeyData.create(
            userAccount.id!!,
            providerExternalUserKeyPair.publicKeyBytes,
            EncryptedKey(providerExternalUserKeyPair.privateKeyBytes, kmsDecryptionKey),
            KeyPairType.SR25519,
            KeyUsageType.ACCOUNT
          )
        ).awaitSingle()
        userKeyData.userAccount = userAccount

        val providerExternalUser = providerExternalUserRepository.save(
          ProviderExternalUser.create(
            BigInteger.ONE,
            "providerExternalUserId${System.currentTimeMillis()}-$currentProviderExternalUserSuffix",
            userKeyData.id!!
          )
        ).awaitSingle()
        providerExternalUsers.add(providerExternalUser)
        providerExternalUser.userKeyData = userKeyData
        Assertions.assertNotNull(providerExternalUser.id)

        repeat(numberOfProviderExternalUserDetails) {
          val userDetail = UserDetail(
            "$TEST_EMAIL_ADDRESS${System.currentTimeMillis()}-$currentProviderExternalUserSuffix-",
            UserDetailType.EMAIL,
            1
          )

          val providerExternalUserDetail = databaseService.saveUserIdentifierAndProviderExternalUserDetail(providerExternalUser.id!!, userAccount.id!!, userDetail)

          providerExternalUserDetail.providerExternalUser = providerExternalUser
          Assertions.assertNotNull(providerExternalUserDetail.id)

          providerExternalUserDetails.add(providerExternalUserDetail)
        }
      }
    }

    private suspend fun assertFromUserAccountWasNotDeleted(userAccount: UserAccount, expectedProviderExternalUserDetailAmount: Int, expectedProviderExternalUserAmount: Int, expectedUserKeyDataAmount: Int) {
      val userAccountId = userAccount.id!!
      Assertj.assertThat(userAccountRepository.findById(userAccountId).awaitSingleOrNull()).isNotNull

      val userKeyDataIdList = userKeyDataRepository.findByUserAccountId(userAccountId).collectList().awaitSingle().map { it.id!! }
      Assertj.assertThat(userKeyDataIdList).hasSize(expectedUserKeyDataAmount)

      val providerExternalUserIds = providerExternalUserRepository.findByUserKeyDataIdIn(userKeyDataIdList).collectList().awaitSingle().map{ it.id!! }
      Assertj.assertThat(providerExternalUserIds).hasSize(expectedProviderExternalUserAmount)

      val providerExternalUserDetailsByUserAccountId = providerExternalUserDetailRepository.findByUserAccountIdIn(listOf(userAccountId)).collectList().awaitSingle()
      Assertj.assertThat(providerExternalUserDetailsByUserAccountId).hasSize(expectedProviderExternalUserDetailAmount)
      val providerExternalUserDetailsByIn = providerExternalUserDetailRepository.findByProviderExternalUserIdIn(providerExternalUserIds).collectList().awaitSingle()
      Assertj.assertThat(providerExternalUserDetailsByUserAccountId).hasSameSizeAs(providerExternalUserDetailsByIn)
    }

    private suspend fun assertFromProviderExternalUserDetailWasDeleted(providerExternalUserDetails: List<ProviderExternalUserDetail>) {
      val providerExternalUsers: MutableList<ProviderExternalUser> = mutableListOf()
      for(providerExternalUserDetail in providerExternalUserDetails) {
        providerExternalUsers.add(providerExternalUserDetail.providerExternalUser!!)
        Assertj.assertThat(providerExternalUserDetailRepository.findById(providerExternalUserDetail.id!!).awaitSingleOrNull()).isNull()
      }

      assertFromProviderExternalUserWasDeleted(providerExternalUsers)
    }

    private suspend fun assertFromProviderExternalUserWasDeleted(providerExternalUsers: List<ProviderExternalUser>) {
      for(providerExternalUser in providerExternalUsers) {
        Assertj.assertThat(providerExternalUserRepository.findById(providerExternalUser.id!!).awaitSingleOrNull()).isNull()
        val userKeyData = providerExternalUser.userKeyData!!
        Assertj.assertThat(userKeyDataRepository.findById(userKeyData.id!!).awaitSingleOrNull()).isNull()
        Assertj.assertThat(userAccountRepository.findById(userKeyData.userAccountId).awaitSingleOrNull()).isNull()
      }
    }

    /**
     * This represents a situation where the user, because of bad transaction management, they have their
     * ProviderExternalUserDetail information gone but a ProviderExternalUSer presence
     *
     */
    private fun createNoProviderExternalUserDetailValueUserAccount() {
      createUserAccount(2, 0)
    }

    @Nested
    @DisplayName("Delete User")
    inner class DeleteUserTests {

      @Test
      fun adminDeleteUserByExternalUserId():Unit = runBlocking {
        //GIVEN
        createNoProviderExternalUserDetailValueUserAccount()
        createUserAccount(2, 0) //Shouldn't be touched
        createUserAccount(2, 2) //Shouldn't be touched
        val firstProviderExternalUserWithNoProviderExternalUserDetails = providerExternalUsers[0]

        //WHEN
        val httpEntity = HttpEntity(DeleteUserByExternalIdRequest(firstProviderExternalUserWithNoProviderExternalUserDetails.providerExternalId, containers.frequency.aliceProviderPublicKeyDto), createDefaultHttpHeaders())
        val deleteUserResponseEntity = testRestTemplate.postForEntity("/api/admin/deleteUser/externalId?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}", httpEntity, DeleteUserResponse::class.java, mapOf(SHARED_SECRET_PARAMETER_NAME to sharedSecret))

        //THEN
        Assertions.assertEquals(200, deleteUserResponseEntity.statusCode.value())
        Assertions.assertEquals(true, deleteUserResponseEntity.body?.result)

        assertFromProviderExternalUserWasDeleted(listOf(providerExternalUsers[0], providerExternalUsers[1]))
        assertFromUserAccountWasNotDeleted(userAccounts[1], 0, 2, 2 + 1)
        assertFromUserAccountWasNotDeleted(userAccounts[2], 4, 2, 2 + 1) //+1 is the Graph Key
      }

      @Test
      fun deleteUserByExternalUserIdReturnsFalseBodyIfExternalUserIdDoesNotExist() {
        val httpEntity = HttpEntity(DeleteUserByExternalIdRequest("someExternalUserIdThatIsNotFound", containers.frequency.aliceProviderPublicKeyDto), createDefaultHttpHeaders())
        val deleteUserResponseEntity = testRestTemplate.postForEntity("/api/admin/deleteUser/externalId?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}", httpEntity, DeleteUserResponse::class.java, mapOf(SHARED_SECRET_PARAMETER_NAME to sharedSecret))

        Assertions.assertEquals(200, deleteUserResponseEntity.statusCode.value())
        Assertions.assertEquals(false, deleteUserResponseEntity.body?.result)
      }

      @Test
      fun deleteUserByExternalUserIdReturn403IfWrongSharedSecret() {
        createNoProviderExternalUserDetailValueUserAccount()
        val firstProviderExternalUserWithNoProviderExternalUserDetails = providerExternalUsers[0]
        val httpEntity = HttpEntity(DeleteUserByExternalIdRequest(firstProviderExternalUserWithNoProviderExternalUserDetails.providerExternalId, containers.frequency.aliceProviderPublicKeyDto), createDefaultHttpHeaders())
        val deleteUserResponseEntity = testRestTemplate.postForEntity("/api/admin/deleteUser/externalId?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}", httpEntity, DeleteUserResponse::class.java, mapOf(SHARED_SECRET_PARAMETER_NAME to "notasharedsecret"))

        Assertions.assertEquals(403, deleteUserResponseEntity.statusCode.value())
      }

      @Test
      fun deleteUser():Unit = runBlocking {
        createUserAccount(1, 1)
        createUserAccount(2, 2) //That shouldn't be touched
        val firstProviderExternalUserDetail = providerExternalUserDetails[0]
        val userDetail = UserDetail(firstProviderExternalUserDetail.userDetailValue, firstProviderExternalUserDetail.userDetailType, 0)
        val httpEntity = HttpEntity(userDetail, createDefaultHttpHeaders())
        val deleteUserResponseEntity = testRestTemplate.postForEntity("/api/admin/deleteUser?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}", httpEntity, DeleteUserResponse::class.java, mapOf(SHARED_SECRET_PARAMETER_NAME to sharedSecret))

        Assertions.assertEquals(200, deleteUserResponseEntity.statusCode.value())
        Assertions.assertEquals(true, deleteUserResponseEntity.body?.result)
        assertFromProviderExternalUserDetailWasDeleted(listOf(providerExternalUserDetails[0]))
        assertFromUserAccountWasNotDeleted(userAccounts[1], 4, 2, 2 + 1) //+1 is the Graph Key
      }

      @Test
      fun deleteUserWithTwoProviderExternalUsers(): Unit = runBlocking {
        createUserAccount(2, 2)
        createUserAccount(2, 2) //That shouldn't be touched
        val firstProviderExternalUserDetail = providerExternalUserDetails[0]
        val userDetail = UserDetail(firstProviderExternalUserDetail.userDetailValue, firstProviderExternalUserDetail.userDetailType, 0)

        val httpEntity = HttpEntity(userDetail, createDefaultHttpHeaders())
        val deleteUserResponseEntity = testRestTemplate.postForEntity("/api/admin/deleteUser?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}", httpEntity, DeleteUserResponse::class.java, mapOf(SHARED_SECRET_PARAMETER_NAME to sharedSecret))

        Assertions.assertEquals(200, deleteUserResponseEntity.statusCode.value())
        Assertions.assertEquals(true, deleteUserResponseEntity.body?.result)
        assertFromProviderExternalUserDetailWasDeleted(providerExternalUserDetails.slice(0 .. 1))
        assertFromUserAccountWasNotDeleted(userAccounts[1], 4, 2, 2 + 1) //+1 is the Graph Key
      }

      @Test
      fun deleteUserReturnsFalseBodyIfUserIdentifierDoesNotExist() {
        val httpEntity = HttpEntity(UserIdentifier("blahblahblah@email.com", UserIdentifierType.EMAIL), createDefaultHttpHeaders())
        val deleteUserResponseEntity = testRestTemplate.postForEntity("/api/admin/deleteUser?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}", httpEntity, DeleteUserResponse::class.java, mapOf(SHARED_SECRET_PARAMETER_NAME to sharedSecret))

        Assertions.assertEquals(200, deleteUserResponseEntity.statusCode.value())
        Assertions.assertEquals(false, deleteUserResponseEntity.body?.result)
      }

      @Test
      fun deleteUserReturn403IfWrongSharedSecret() {
        createUserAccount(1, 1)
        val firstProviderExternalUserDetail = providerExternalUserDetails[0]
        val userDetail = UserDetail(firstProviderExternalUserDetail.userDetailValue, firstProviderExternalUserDetail.userDetailType, 0)
        val httpEntity = HttpEntity(userDetail, createDefaultHttpHeaders())
        val deleteUserResponseEntity = testRestTemplate.postForEntity("/api/admin/deleteUser?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}", httpEntity, DeleteUserResponse::class.java, mapOf(SHARED_SECRET_PARAMETER_NAME to "notasharedsecret"))

        Assertions.assertEquals(403, deleteUserResponseEntity.statusCode.value())
      }
    }





    @Nested
    @DisplayName("Revoke Delegation")
    inner class RevokeDelegationTests {

      @Test
      fun revokeDelegationAndHandle() : Unit = runBlocking {
        val siwaPayloadResponse = siwaRegisterUser(REGISTRATION_DATA_EMAIL)
        submitSiwaSignedPayloads(containers.frequency.aliceProviderClient, siwaPayloadResponse)

        val lastBlockNumber = containers.frequency.aliceProviderClient.getLastBlockNumber().join()
        val handlePayload = siwaPayloadResponse.payloads.find { payload ->  payload.type == PayloadType.CLAIM_HANDLE }!!.payload as HandlePayloadResponse

        // Make blocks passed the expiration on the handle
        repeat((handlePayload.expiration - lastBlockNumber.longValueExact() + 1).toInt()) {
          containers.frequency.aliceProviderClient.createBlock(true, finalize = true).join()
        }

        val httpEntity = HttpEntity(ProviderUserIdentifier(containers.frequency.aliceProviderMsaId, EMAIL_IDENTIFIER), createDefaultHttpHeaders())
        val revokeDelegationAndHandleResponseEntity = testRestTemplate.postForEntity(
          "/api/admin/revokeDelegationAndHandle?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}",
          httpEntity,
          Void::class.java,
          mapOf(SHARED_SECRET_PARAMETER_NAME to sharedSecret)
        )
        Assertions.assertEquals(200, revokeDelegationAndHandleResponseEntity.statusCode.value())
      }

      @Test
      fun revokeDelegationAndHandleWithoutRegisteredHandle(): Unit = runBlocking {
        val siwaPayloadResponse = siwaRegisterUser(REGISTRATION_DATA_EMAIL)
        val handlePayload = siwaPayloadResponse.payloads.find { it.type == PayloadType.ADD_PROVIDER }
        val noHandlePayloadResponse = siwaPayloadResponse.copy(payloads = listOf(handlePayload!!))
        submitSiwaSignedPayloads(containers.frequency.aliceProviderClient, noHandlePayloadResponse)

        val httpEntity = HttpEntity(ProviderUserIdentifier(containers.frequency.aliceProviderMsaId, EMAIL_IDENTIFIER), createDefaultHttpHeaders())
        val revokeDelegationAndHandleResponseEntity = testRestTemplate.postForEntity(
          "/api/admin/revokeDelegationAndHandle?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}",
          httpEntity,
          Void::class.java,
          mapOf(SHARED_SECRET_PARAMETER_NAME to sharedSecret)
        )
        Assertions.assertEquals(200, revokeDelegationAndHandleResponseEntity.statusCode.value())
      }

      @Test
      fun retireMsaAndDeleteUser(): Unit = runBlocking {
        val siwaPayloadResponse = siwaRegisterUser(REGISTRATION_DATA_EMAIL)
        submitSiwaSignedPayloads(containers.frequency.aliceProviderClient, siwaPayloadResponse)
        //directEmailLogin(EMAIL_IDENTIFIER)
        val lastBlockNumber = containers.frequency.aliceProviderClient.getLastBlockNumber().join()
        val handlePayload = siwaPayloadResponse.payloads.find { payload ->  payload.type == PayloadType.CLAIM_HANDLE }!!.payload as HandlePayloadResponse

        // Make blocks passed the expiration on the handle
        repeat((handlePayload.expiration - lastBlockNumber.longValueExact() + 1).toInt()) {
          containers.frequency.aliceProviderClient.createBlock(true, finalize = true).join()
        }

        val httpEntity = HttpEntity(ProviderUserIdentifier(BigInteger.ONE, EMAIL_IDENTIFIER), createDefaultHttpHeaders())

        val revokeResult = testRestTemplate.postForEntity(
          "/api/admin/revokeDelegationAndHandle?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}",
          httpEntity,
          Void::class.java,
          mapOf(SHARED_SECRET_PARAMETER_NAME to sharedSecret)
        )

        Assertions.assertEquals(200, revokeResult.statusCode.value())

        containers.frequency.aliceProviderClient.createBlock(true, finalize = true).join()

        val httpEntity2 = HttpEntity(ProviderUserIdentifier(BigInteger.ONE, EMAIL_IDENTIFIER), createDefaultHttpHeaders())
        val retireMsaAndDeleteUserResponseEntity = testRestTemplate.postForEntity(
          "/api/admin/deleteUserAndRetireMsa?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}",
          httpEntity2,
          DeleteUserResponse::class.java,
          mapOf(SHARED_SECRET_PARAMETER_NAME to sharedSecret)
        )
        Assertions.assertEquals(200, retireMsaAndDeleteUserResponseEntity.statusCode.value())
        Assertions.assertNotNull(retireMsaAndDeleteUserResponseEntity.body)
        Assertions.assertEquals(true, retireMsaAndDeleteUserResponseEntity.body!!.result)
      }
    }


  }

  @Nested
  @DisplayName("Handle Tests")
  inner class HandleTests {

    @Test
    fun givenProperChangeHandleRequest_whenRequestSent_ChangeHandleFlowExecutesProperly(): Unit = runBlocking {
      //// STEP 1 - The aliceClient requests the Onboarding page and is given a session ID
      val siwaPayloadResponse = siwaRegisterUser(REGISTRATION_DATA_EMAIL)
      submitSiwaSignedPayloads(containers.frequency.aliceProviderClient, siwaPayloadResponse)
      val loginSessionId = websiteEmailLogin(REGISTRATION_DATA_EMAIL.userIdentifier)
      // Given
      val headers = createDefaultHttpHeaders()
      headers.add("Cookie", cookieHelper.createResponseCookie(loginSessionId).toString())
      //val handlePayload = HandlePayloadRequest(BASE_HANDLE)
      //val handleSignature = generateHandleRequestSignature(handlePayload)
      //val handleRequest = HandleRequest(handleSignature, handlePayload)
      val changeHandleRequest = UserChangeHandleRequest(BASE_HANDLE)

      //// STEP 4 - sign change handle request using key account key of user

      // When
      val changeHandleUri = URI("/api/account/handle")
      val changeHandleHttpRequest: HttpEntity<UserChangeHandleRequest> = HttpEntity(changeHandleRequest, headers)
      val changeHandleResponse: ResponseEntity<UserChangeHandleResponse> = testRestTemplate.exchange(
        changeHandleUri,
        HttpMethod.PUT,
        changeHandleHttpRequest,
        UserChangeHandleResponse::class.java
      )
      // Then
      Assertions.assertEquals(HttpStatus.OK, changeHandleResponse.statusCode)
      val response = changeHandleResponse.body
      Assertions.assertNotNull(response)
      Assertions.assertTrue { response!!.claimedHandle.contains(BASE_HANDLE) }
    }
  }

  @Nested
  @DisplayName("Signed Payload Tests")
  inner class SignedPayloadTests {
    private val keyPair = testKeyPair()
    private val sr25519KeyPair = Sr25519KeyPairBytes(
      keyPair.publicKeyBytes.bytes,
      keyPair.privateKeyBytes.bytes,
      KeyPairSignatureAlgorithm.SR25519,
    )

    private val addProviderPayload = AddProviderPayloadRequest(containers.frequency.aliceProviderMsaId, DEFAULT_SCHEMA_IDS, URI("https://www.mewe.com/authenticate"))
    private val addProviderSignature = signingOrchestrationService.signPayload(
      sr25519KeyPair,
      io.amplica.custodial_wallet.orchestration.payload.SignUpRequest(
        addProviderPayload.msaId,
        addProviderPayload.schemaIds,
        addProviderPayload.url?.toString(),
      )
    )
    private val addProviderType = PayloadType.ADD_PROVIDER
    private val typedAddProviderPayload = TypedPayloadRequestWithSignature(addProviderSignature, addProviderType, addProviderPayload)

    private val claimHandlePayload = HandlePayloadRequest("sampleHandle")
    private val claimHandleSignature = signingOrchestrationService.signPayload(
      sr25519KeyPair,
      io.amplica.custodial_wallet.orchestration.payload.HandleRequest(claimHandlePayload.baseHandle),
    )
    private val claimHandleType = PayloadType.CLAIM_HANDLE
    private val typedClaimHandlePayload = TypedPayloadRequestWithSignature(claimHandleSignature, claimHandleType, claimHandlePayload)

    private val batchPayloadToSignRequest = BatchPayloadToSignRequest(
      null,
      PROVIDER_EXTERNAL_USER_ID,
      EMAIL_IDENTIFIER,
      containers.frequency.aliceProviderPublicKeyDto,
      "https://amplicaaccess.com",
      listOf(typedAddProviderPayload, typedClaimHandlePayload),
      null,
      null,
    )

    private val authenticationCode = "123456"

    suspend fun postBatchPayloadToSign(payloadToSign: BatchPayloadToSignRequest): URI {
      // Given
      siwaRegisterUser(REGISTRATION_DATA_EMAIL)

      val headers = createDefaultHttpHeaders()
      val signBatchRequest: HttpEntity<BatchPayloadToSignRequest> = HttpEntity(payloadToSign, headers)
      val signBatchUri = URI("/api/sign/batch")

      // When
      val signBatchResponse: ResponseEntity<Unit> = testRestTemplate.postForEntity(signBatchUri, signBatchRequest, Unit::class.java)

      // Then
      Assertions.assertEquals(201, signBatchResponse.statusCode.value())
      Assertions.assertNotNull(signBatchResponse.headers.location)
      return signBatchResponse.headers.location!!
    }


    @Test
    fun givenProperBatchPayloadToSignRequest_whenRequestSent_PersistBatchPayloadExecutesProperly(): Unit = runBlocking {
      postBatchPayloadToSign(batchPayloadToSignRequest)
    }

    @Test
    fun givenProperBatchPayloadToSignRequest_whenBatchSigningRequest_BatchSuccessfullySigned(): Unit = runBlocking {
      // Step 1 -> stores payload returns permissions location
      val location = postBatchPayloadToSign(batchPayloadToSignRequest) // -> stores payload returns permissions location

      // Step 2 -> Attaches sessionId as cookie returns page with accept form
      val permissionsResponse: ResponseEntity<String> = testRestTemplate.getForEntity(location.path, String::class.java)
      Assertions.assertEquals(200, permissionsResponse.statusCode.value())

      // Step 3 -> generate and stores the authentication code returns page with verify form
      val returnedSessionId = runBlocking {
        redisClient.saveBatchPayloadToSignRequestByAuthenticationCode(authenticationCode, batchPayloadToSignRequest)
      }

      // Step 4 -> generate and stores the authorization code returns redirect
      val verifyUri = UriComponentsBuilder.fromUri(URI("/web/sign/verify"))
        .queryParam("sessionId", returnedSessionId)
        .queryParam("authenticationCode", authenticationCode)
        .build()
        .toUri()
      val verifyResponse: ResponseEntity<String> = testRestTemplate.getForEntity(verifyUri, String::class.java)
      Assertions.assertEquals(303, verifyResponse.statusCode.value())
      Assertions.assertNotNull(verifyResponse.headers.location)
      val redirectUri = verifyResponse.headers.location!!
      Assertions.assertEquals(batchPayloadToSignRequest.callback, redirectUri.toString().substringBefore("?"))

      val authorizationCode = redirectUri.query.substringAfter("=")

      val foundPayload = runBlocking {
        redisClient.findBatchPayloadToSignRequestBySessionIdAndAuthorizationCode(returnedSessionId, authorizationCode)
      }

      Assertions.assertNotNull(foundPayload)
      Assertions.assertEquals(batchPayloadToSignRequest.publicKey, foundPayload!!.publicKey)
      Assertions.assertEquals(batchPayloadToSignRequest.externalUserId, foundPayload.externalUserId)
      Assertions.assertEquals(batchPayloadToSignRequest.userIdentifier, foundPayload.userIdentifier)
      Assertj.assertThat(batchPayloadToSignRequest.payloads).usingRecursiveComparison().isEqualTo(foundPayload.payloads)

      // Step 5 -> authorize batch signing returns signed batch
      val authorizeUri = UriComponentsBuilder.fromUri(URI("/api/sign/authorize"))
        .queryParam("sessionId", returnedSessionId)
        .queryParam(AUTHORIZATION_CODE_PARAMETER_NAME, authorizationCode)
        .build()
        .toUri()
      val authorizeResponse: ResponseEntity<BatchSignedPayloadResponse> = testRestTemplate.getForEntity(authorizeUri, BatchSignedPayloadResponse::class.java)
      Assertions.assertEquals(200, authorizeResponse.statusCode.value())
      Assertions.assertNotNull(authorizeResponse.body)

      val signedBatch = authorizeResponse.body!!
      Assertions.assertEquals(batchPayloadToSignRequest.externalUserId, signedBatch.externalUserId)
      Assertj.assertThat(batchPayloadToSignRequest.payloads[0].payload).usingRecursiveComparison()
        .isEqualTo(signedBatch.payloads[0].payload)
      Assertj.assertThat(batchPayloadToSignRequest.payloads[1].payload).usingRecursiveComparison()
        .isEqualTo(signedBatch.payloads[1].payload)
    }
  }

  @Nested
  @DisplayName("Passkey Wallet Tests")
  @Disabled("Disabling for now because Playwright tests test the same flow end to end")
  inner class PasskeyWalletTests {
    private lateinit var acceptRegistrationRequest: AcceptRegistrationRequest

    private val accountKeyEthereum = PublicKeyDto(
      "0x02d37e792f9e2c2e4e1798b2568855349b4f9d687117f1639f47e63ce68bb0f2ad",
      Encoding.HEX,
      PublicKeyFormat.BARE,
      KeyPairType.SECP256K1
    )

    private val accountKey = PublicKeyDto(
      "0x9c0fc9e7c652d34803b7450514db612ed81cd2a65d505309b0c1de53f25aae2b",
      Encoding.HEX,
      PublicKeyFormat.BARE,
      KeyPairType.SR25519
    )

    fun acceptRegistrationRequestWithSrCredentialPublicKeySignature() : BigInteger? {
      val userAccount =
        runBlocking {
          userAccountRepository.save(UserAccount.create()).awaitSingle()
        }

      val headers = createDefaultHttpHeaders()
      acceptRegistrationRequest = AcceptRegistrationRequest(
        BASE_HANDLE,
        EncodedBytes(
          "0x7b2274797065223a22776562617574686e2e637265617465222c226368616c6c656e6765223a225a66344e624e737a33654b613430387a494e5f54487975387268544748366b3276674f3363573033536651222c226f726967696e223a2268747470733a2f2f706173736b65792e616d706c6963612e696f3a38303830222c2263726f73734f726967696e223a66616c73657d",
          Encoding.HEX
        ),
        EncodedBytes(
          "0xa363666d74646e6f6e656761747453746d74a06861757468446174615894589f094cd6e2bd35a7fb8df7b9bb35e3c03b1205a8c22e1201c6018cb59fa3515d00000000d548826e79b4db40a3d811116f7e83490010ed449b794e7f4860b59da8e08c9b7044a501020326200121582041a0bfcc823410ca6d659fb1970e9e9533f074dbb09f30a9fbfbbd5ca609ed552258206aefda79aab08a86068606c3c769326d7379b64778051c1da873c8f0c145893f",
          Encoding.HEX
        ),
        "{}", //clientExtensions
        setOf("internal"),
        EncodedBytes(
          "0x65fe0d6cdb33dde29ae34f3320dfd31f2bbcae14c61fa936be03b7716d3749f4",
          Encoding.HEX
        ),
        "7USbeU5_SGC1najgjJtwRA==", //credentialId
        PublicKeyDto(
          "0x026f4a29a88520842a526b171476ed83d54f1e4b337ad5167fd085c904b4c1df48",
          Encoding.HEX,
          PublicKeyFormat.BARE,
          KeyPairType.PASSKEY_COMPRESSED
        ),
        accountKey,
        Signature(
          SignatureKeyPairType.SR25519,
          Encoding.HEX,
          "0xc20ce333110a0a99a4b45649640d9739dc77a235a918965ef1565003ca88c774509b751a4f7773bbf2561f20842dfe2bbdc4ba5e5df70847a8be1cb10e74a180"
        ),
        null
      )

      val sessionId = "sessionId"

      runBlocking {
        redisClient.saveSiwaSession(
          AuthenticatedSiwaSession(
            SIWA_REQUEST,
            sessionId,
            emailUserIdentifier,
            CALLBACK_URL,
            USER_KEY_PAIR_TYPE,
            SiwaFlowKind.SOCIAL,
            SiwaIntent.Login(userAccount.id!!, true),
            userAccount.id,
            null,
            null
          )
        )
      }

      val acceptRegistrationHttpRequest: HttpEntity<AcceptRegistrationRequest> = HttpEntity(acceptRegistrationRequest, headers)
      val acceptRegistrationUri = URI("/api/passkey/registration/accept")
      headers.add("Cookie", cookieHelper.createResponseCookie(sessionId).toString())

      val acceptRegistrationResponse: ResponseEntity<BooleanHolder> = testRestTemplate.postForEntity(acceptRegistrationUri, acceptRegistrationHttpRequest, BooleanHolder::class.java)
      Assertions.assertEquals(true, acceptRegistrationResponse.body!!.response)

      return userAccount.id
    }

    @Test
    fun acceptRegistrationRequestWithEthCredentialPublicKeySignature() {
      val userAccount =
        runBlocking {
          userAccountRepository.save(UserAccount.create()).awaitSingle()
        }

      val headers = createDefaultHttpHeaders()

      val passkeyCompressedPublicKey = PublicKeyDto(
        "0x026f4a29a88520842a526b171476ed83d54f1e4b337ad5167fd085c904b4c1df48",
        Encoding.HEX,
        PublicKeyFormat.BARE,
        KeyPairType.PASSKEY_COMPRESSED
      )

      val secp256K1Signature = Signature(
        SignatureKeyPairType.SECP256K1,
        Encoding.HEX,
        "0xb629425e0d4daca9d5c3b16b74665b113dcc1cc38d7aab6137c0f507628ffbcd7b627fc394d4a85b71a0f6b3160f2864bfd0d7ab77abaacbfbee6c5458c2e91f1b"
      )

      acceptRegistrationRequest = AcceptRegistrationRequest(
        BASE_HANDLE,
        EncodedBytes(
          "0x7b2274797065223a22776562617574686e2e637265617465222c226368616c6c656e6765223a225a66344e624e737a33654b613430387a494e5f54487975387268544748366b3276674f3363573033536651222c226f726967696e223a2268747470733a2f2f706173736b65792e616d706c6963612e696f3a38303830222c2263726f73734f726967696e223a66616c73657d",
          Encoding.HEX
        ),
        EncodedBytes(
          "0xa363666d74646e6f6e656761747453746d74a06861757468446174615894589f094cd6e2bd35a7fb8df7b9bb35e3c03b1205a8c22e1201c6018cb59fa3515d00000000d548826e79b4db40a3d811116f7e83490010ed449b794e7f4860b59da8e08c9b7044a501020326200121582041a0bfcc823410ca6d659fb1970e9e9533f074dbb09f30a9fbfbbd5ca609ed552258206aefda79aab08a86068606c3c769326d7379b64778051c1da873c8f0c145893f",
          Encoding.HEX
        ),
        "{}", //clientExtensions
        setOf("internal"),
        EncodedBytes(
          "0x65fe0d6cdb33dde29ae34f3320dfd31f2bbcae14c61fa936be03b7716d3749f4",
          Encoding.HEX
        ),
        "7USbeU5_SGC1najgjJtwRA==", //credentialId
        passkeyCompressedPublicKey,
        accountKeyEthereum,
        secp256K1Signature,
        null
      )

      val sessionId = "sessionId"

      runBlocking {
        redisClient.saveSiwaSession(
          AuthenticatedSiwaSession(
            SIWA_REQUEST,
            sessionId,
            emailUserIdentifier,
            CALLBACK_URL,
            USER_KEY_PAIR_TYPE,
            flowKind = SiwaFlowKind.SOCIAL,
            SiwaIntent.Login(userAccount.id!!, true),
            userAccount.id,
            null,
            null
          )
        )
      }

      val acceptRegistrationHttpRequest: HttpEntity<AcceptRegistrationRequest> = HttpEntity(acceptRegistrationRequest, headers)
      val acceptRegistrationUri = URI("/api/passkey/registration/accept")
      headers.add("Cookie", cookieHelper.createResponseCookie(sessionId).toString())

      val acceptRegistrationResponse: ResponseEntity<BooleanHolder> = testRestTemplate.postForEntity(acceptRegistrationUri, acceptRegistrationHttpRequest, BooleanHolder::class.java)
      Assertions.assertEquals(true, acceptRegistrationResponse.body!!.response)

    }

    @Test
    fun getCredentialResponseSuccessfully() {
      //GIVEN
      acceptRegistrationRequestWithSrCredentialPublicKeySignature()
      val credentialId = "7USbeU5_SGC1najgjJtwRA=="

      //WHEN
      val getCredentialResponseUri = UriComponentsBuilder.fromUri(URI("/api/passkey/credential/${credentialId}"))
        .build()
        .toUri()
        val headers = createDefaultHttpHeaders()
        headers.add("Cookie", cookieHelper.createResponseCookie("sessionId").toString())
      val credentialResponse: ResponseEntity<CredentialResponseDto>  = testRestTemplate.exchange(
        getCredentialResponseUri.toString(),
        HttpMethod.GET,
        HttpEntity<CredentialResponseDto>(headers),
        CredentialResponseDto::class.java
      )

      //THEN
      val savedWallet = runBlocking {
        databaseService.findPasskeyWalletByCredentialId(credentialId)
      }
      Assertions.assertEquals(credentialResponse.statusCode.value(), 200)
      Assertions.assertEquals(credentialResponse.body!!.credentialId, credentialId)
      Assertions.assertEquals(
        credentialResponse.body!!.passkeyCompressedPublicKey.encodedValue,
        savedWallet!!.credential.compressedPublicKeyBase64Url
      )
    }

    @Test
    fun getCredentialResponsesSuccessfully() {
      //GIVEN
      val userAccountId = acceptRegistrationRequestWithSrCredentialPublicKeySignature()

      //WHEN
      val getCredentialResponsesUri = UriComponentsBuilder.fromUri(URI("/api/passkey/credentials"))
        .build()
        .toUri()
      val headers = createDefaultHttpHeaders()
      headers.add("Cookie", cookieHelper.createResponseCookie("sessionId").toString())
      val credentialsResponse: ResponseEntity<CredentialResponsesDto>  = testRestTemplate.exchange(
        getCredentialResponsesUri.toString(),
        HttpMethod.GET,
        HttpEntity<CredentialResponseDto>(headers),
        CredentialResponsesDto::class.java
      )

      //THEN
      runBlocking {
        databaseService.findPasskeyWalletsByUserAccountId(userAccountId!!)
      }
      Assertions.assertEquals(credentialsResponse.statusCode.value(), 200)
    }

    @Test
    fun getCredentialFailsPasskeyAssociatedWithUserButWrongCredential() {
      //GIVEN
      acceptRegistrationRequestWithSrCredentialPublicKeySignature()
      val credentialId = "fakeCredentialId"

      //WHEN
      val getCredentialResponseUri = UriComponentsBuilder.fromUri(URI("/api/passkey/credential/${credentialId}"))
        .build()
        .toUri()
      val headers = createDefaultHttpHeaders()
      headers.add("Cookie", cookieHelper.createResponseCookie("sessionId").toString())
      val credentialResponse: ResponseEntity<ApiErrorDto>  = testRestTemplate.exchange(
        getCredentialResponseUri.toString(),
        HttpMethod.GET,
        HttpEntity<ApiErrorDto>(headers),
        ApiErrorDto::class.java
      )

      //THEN
      Assertions.assertEquals(credentialResponse.statusCode.value(), 404)
      Assertions.assertTrue(credentialResponse.body!!.description.startsWith("A passkey wallet exists with this user but was not found for this device"))
    }

    @Test
    fun getCredentialFailsNoPasskeyAssociatedWithUser() {
      //GIVEN
      val userAccount =
        runBlocking {
          userAccountRepository.save(UserAccount.create()).awaitSingle()
        }

      val credentialId = "fakeCredentialId"

      runBlocking {
        redisClient.saveSiwaSession(
          AuthenticatedSiwaSession(
            SIWA_REQUEST,
            "sessionId",
            emailUserIdentifier,
            CALLBACK_URL,
            USER_KEY_PAIR_TYPE,
            SiwaFlowKind.SOCIAL,
            SiwaIntent.Login(userAccount.id!!, true),
            userAccount.id,
            null,
            null
          )
        )
      }

      //WHEN
      val getCredentialResponseUri = UriComponentsBuilder.fromUri(URI("/api/passkey/credential/${credentialId}"))
        .build()
        .toUri()
      val headers = createDefaultHttpHeaders()
      headers.add("Cookie", cookieHelper.createResponseCookie("sessionId").toString())
      val credentialResponse: ResponseEntity<ApiErrorDto>  = testRestTemplate.exchange(
        getCredentialResponseUri.toString(),
        HttpMethod.GET,
        HttpEntity<ApiErrorDto>(headers),
        ApiErrorDto::class.java
      )

      //THEN
      Assertions.assertEquals(credentialResponse.statusCode.value(), 404)
      Assertions.assertTrue(credentialResponse.body!!.description.startsWith("A passkey wallet associated with this user was not found"))
    }
  }

  @Nested
  @DisplayName("WebApiController Tests")
  inner class WebApiControllerTests {
    @Test
    fun testGetLocalizedMessages() {
      //GIVEN
      val messageKey = "error.generic.title"
      val expectedMessage = "Oi!"
      val localeOverride = Locale.UK

      //WHEN
      val jollyOlEnglishMessages = testRestTemplate.getForObject("/api/web/messages?localeOverride={localeOverride}", Map::class.java, localeOverride.toLanguageTag())

      //THEN
      Assertj.assertThat(jollyOlEnglishMessages).isNotNull
      Assertj.assertThat(jollyOlEnglishMessages!![messageKey]).isEqualTo(expectedMessage)
    }
  }

  @Nested
  @DisplayName("Deliverability API Tests")
  inner class DeliverabilityApiTests {

    @ParameterizedTest
    @CsvSource(value = [
      "EMAIL,fang.runin@sinegard.edu.nk",
      "PHONE_NUMBER,+12133211234",
    ])
    fun deliverability(type: String, value: String): Unit = runBlocking {
      // GIVEN
      if (type == UserIdentifierType.PHONE_NUMBER.name) {
        stubFor(
          get(urlEqualTo("/api/lookup/phoneNumber/$value"))
            .willReturn(
              okJson(
                """{
                "phoneNumber":"$value",
                "blockStatus":{"isBlocked":"false"}
              }"""
              )
            )
        )
      }

      // WHEN
      val response = testRestTemplate.getForEntity<BooleanHolder>(
        "/api/deliverability/$type/$value"
      )

      // THEN
      Assertj.assertThat(response.statusCode.is2xxSuccessful).isTrue()
      Assertj.assertThat(response.body!!.response).isTrue()
    }

    @ParameterizedTest
    @CsvSource(value = [
      "OTHER,foo+bar",
    ])
    fun deliverabilityErrors(type: String, value: String): Unit = runBlocking {
      // WHEN
      val response = testRestTemplate.getForEntity<ApiErrorDto>(
        "/api/deliverability/$type/$value"
      )

      // THEN
      Assertj.assertThat(response.statusCode.is4xxClientError).isTrue()
      Assertj.assertThat(response.body!!.id).isEqualTo(ApiError.INVALID_REQUEST.id)
    }

  }
}
