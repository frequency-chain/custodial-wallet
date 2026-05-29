package io.amplica.custodial_wallet.client.redis

import arrow.core.Either
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.amplica.custodial_wallet.client.redis.conf.RedisClientConfig
import io.amplica.custodial_wallet.client.redis.conf.RedisConfigurationProperties
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.util.key_creation.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigInteger
import java.net.URI
import java.time.Duration
import java.time.Instant


@Testcontainers
@SpringJUnitConfig(classes = [CustodialWalletRedisClientTest::class, RedisClientConfig::class, MoreConfig::class])
@EnableConfigurationProperties(RedisConfigurationProperties::class)
@EnableAutoConfiguration
class CustodialWalletRedisClientTest {
  @Autowired
  lateinit var redisClient: CustodialWalletRedisClient

  companion object {

    lateinit var loginRequest: LoginRequest
    lateinit var signUpRequest: SignUpRequest
    lateinit var sessionInfo: SessionInfo
    lateinit var websiteSession1: WebsiteSession
    lateinit var websiteSession2: WebsiteSession
    lateinit var websiteSession3: WebsiteSession
    lateinit var authenticationCode: String
    lateinit var verificationCode: String
    lateinit var token: String
    lateinit var newToken: String
    lateinit var notFoundToken: String
    lateinit var sessionId: String
    lateinit var authorizationCode: String
    lateinit var signature: Signature
    lateinit var userIdentifier: UserIdentifier
    lateinit var publicKeyDto: PublicKeyDto
    lateinit var userPublicKey: PublicKeyDto

    @Container
    val redis = GenericContainer<Nothing>("valkey/valkey:7.2.6").apply {
      withExposedPorts(6379)
    }

    @DynamicPropertySource
    @JvmStatic
    fun registerDynamicProperties(registry: DynamicPropertyRegistry) {
      registry.add("spring.data.redis.host", redis::getHost)
      registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
      registry.add("unfinished.custodial-wallet.redis.expiration"){"10s"}
    }

    @BeforeAll
    @JvmStatic
    fun setUpClass() {
      signature = Signature(SignatureKeyPairType.SR25519, Encoding.HEX,"0x3259258c")
      userIdentifier = UserIdentifier("some@user.com", UserIdentifierType.EMAIL)
      publicKeyDto = PublicKeyDto("3259258c", Encoding.BASE_58, PublicKeyFormat.SS58, KeyPairType.SR25519)
      userPublicKey = PublicKeyDto("3259258d", Encoding.BASE_58, PublicKeyFormat.SS58, KeyPairType.SR25519)

      val addProviderPayloadRequest = AddProviderPayloadRequest(
        BigInteger.ONE,
        listOf(256, 128),
        URI.create("https://google.com")
      )

      val handlePayloadRequest = HandlePayloadRequest(
        "testHandle"
      )

      val handleRequest = HandleRequest(
        signature,
        handlePayloadRequest
      )

      val nonce = "nonce"

      val loginPayload = LoginPayload(
        nonce,
        URI.create("https://google.com")
      )

      val externalUserId = "someUserId"
      val emailIdentifier = UserIdentifier("some@user.com", UserIdentifierType.EMAIL)
      val phoneIdentifier = UserIdentifier("3331112222", UserIdentifierType.PHONE_NUMBER)
      val userIdentifiers = listOf(emailIdentifier, phoneIdentifier)
      val currentTime = System.currentTimeMillis()

      signUpRequest = SignUpRequest(
        externalUserId,
        userIdentifiers,
        publicKeyDto,
        signature,
        addProviderPayloadRequest,
        handleRequest
      )
      loginRequest = LoginRequest(
        userPublicKey,
        externalUserId,
        emailIdentifier,
        publicKeyDto,
        signature,
        loginPayload
      )
      sessionInfo = SessionInfo(
        true,
        null,
        currentTime,
        0,
        0
      )
      websiteSession1 = WebsiteSession(
        null,
        "samplecallbackurl1",
      )
      websiteSession2 = WebsiteSession(
        null,
        "samplecallbackurl2"
      )
      websiteSession3 = WebsiteSession(
        null,
        "samplecallbackurl3"
      )
      authenticationCode = "authcode"
      verificationCode = "verifcode"
      token = "token"
      newToken = "token2"
      notFoundToken = "notFoundToken"
      sessionId = "sessionId"
      authorizationCode = generateUUID()
    }
  }

  @Nested
  @DisplayName("Reactive Redis Tests")
  inner class ReactiveTests{
    @Nested
    @DisplayName("Login Request Tests")
    inner class LoginRequestTests {
      private lateinit var id: String

      @BeforeEach
      fun setUpLoginRequestTests(){
        runBlocking {
          redisClient.deleteLoginRequestByToken(sessionId, token)
          println("##################################### SAVING")
          id = redisClient.saveLoginRequestByToken(loginRequest = loginRequest, token = token)
          println("##################################### END SAVING")
        }
      }

      @Test
      fun findLoginRequestByToken() {
        runBlocking {
          val foundLoginRequestUsingToken = redisClient.findLoginRequestByToken(id, token)
          Assertions.assertThat(foundLoginRequestUsingToken).isNotNull
          Assertions.assertThat(foundLoginRequestUsingToken).usingRecursiveComparison().ignoringFields("token")
            .isEqualTo(loginRequest)
        }
      }

      @Test
      fun findLoginRequestBySessionId() {
        runBlocking {
          val foundLoginRequestUsingId = redisClient.findLoginRequestBySessionId(id)
          Assertions.assertThat(foundLoginRequestUsingId).isNotNull
          Assertions.assertThat(foundLoginRequestUsingId).usingRecursiveComparison().ignoringFields("token")
            .isEqualTo(loginRequest)
        }
      }

      @Test
      fun loginRequestTokenNotFound() {
        runBlocking {
          val foundLoginRequest = redisClient.findLoginRequestByToken(id, notFoundToken)
          Assertions.assertThat(foundLoginRequest).isNull()
        }
      }

      @Test
      fun deleteLoginRequestByToken() {
        runBlocking {
          redisClient.deleteLoginRequestByToken(id, token)
          val foundLoginRequestUsingToken = redisClient.findLoginRequestByToken(id, token)
          Assertions.assertThat(foundLoginRequestUsingToken).isNull()
        }
      }

      @Test
      fun replaceLoginRequestToken() {
        runBlocking {
          redisClient.replaceLoginRequestToken(id, newToken)

          val foundLoginRequestUsingToken = redisClient.findLoginRequestByToken(id, newToken)
          Assertions.assertThat(foundLoginRequestUsingToken).isNotNull
          Assertions.assertThat(foundLoginRequestUsingToken).usingRecursiveComparison().ignoringFields("token")
            .isEqualTo(loginRequest)
          Assertions.assertThat(foundLoginRequestUsingToken?.token).isEqualTo(newToken)

          val loginRequestUsingOldToken = redisClient.findLoginRequestByToken(id, token)
          Assertions.assertThat(loginRequestUsingOldToken).isNull()
        }
      }
    }

    @Nested
    @DisplayName("SignUp Request Tests")
    inner class SignUpRequestTests {
      lateinit var id: String

      @BeforeEach
      fun setUpSignUpRequestTests() {
        runBlocking {
          redisClient.deleteSignUpRequestByToken(sessionId, token)
          id = redisClient.saveSignUpRequestByToken(signUpRequest = signUpRequest, token = token)
        }
      }

      @Test
      fun findSignUpRequestByToken() {
        runBlocking {
          val foundSignUpRequestUsingToken = redisClient.findSignUpRequestByToken(id, token)
          Assertions.assertThat(foundSignUpRequestUsingToken).isNotNull
          Assertions.assertThat(foundSignUpRequestUsingToken).usingRecursiveComparison().ignoringFields("token")
            .isEqualTo(signUpRequest)
        }
      }

      @Test
      fun findSignUpRequestBySessionId() {
        runBlocking {
          val foundSignUpRequestUsingSessionId = redisClient.findSignUpRequestBySessionId(id)
          Assertions.assertThat(foundSignUpRequestUsingSessionId).isNotNull
          Assertions.assertThat(foundSignUpRequestUsingSessionId).usingRecursiveComparison().ignoringFields("token")
            .isEqualTo(signUpRequest)
        }
      }

      @Test
      fun signUpRequestTokenNotFound() {
        runBlocking {
          val foundSignUpRequest = redisClient.findSignUpRequestByToken(id, notFoundToken)
          Assertions.assertThat(foundSignUpRequest).isNull()
        }
      }

      @Test
      fun deleteSignUpRequestByToken() {
        runBlocking {
          redisClient.deleteSignUpRequestByToken(id, token)
          val foundSignUpRequestUsingToken = redisClient.findSignUpRequestByToken(id, token)
          Assertions.assertThat(foundSignUpRequestUsingToken).isNull()
        }
      }

      @Test
      fun replaceSignUpRequestToken() {
        runBlocking {
          redisClient.replaceSignUpRequestToken(id, newToken)
          val foundSignUpRequestUsingToken = redisClient.findSignUpRequestByToken(id, newToken)
          Assertions.assertThat(foundSignUpRequestUsingToken).isNotNull
          Assertions.assertThat(foundSignUpRequestUsingToken).usingRecursiveComparison().ignoringFields("token")
            .isEqualTo(signUpRequest)
        }
      }
    }

    @Nested
    @DisplayName("Webview Session Info Tests")
    inner class SessionInfoTests {
      @BeforeEach
      fun setUpWebviewSessionInfoTests() {
        runBlocking {
          redisClient.saveSessionInfoBySessionId(sessionId, sessionInfo)
        }
      }

      @Test
      fun findWebviewSessionInfoBySessionId() {
        runBlocking {
          val foundWebviewSessionInfo = redisClient.findSessionInfoBySessionId(sessionId)
          Assertions.assertThat(foundWebviewSessionInfo).isNotNull
          Assertions.assertThat(foundWebviewSessionInfo).usingRecursiveComparison().isEqualTo(sessionInfo)
        }
      }

      @Test
      fun updateWebviewSessionInfoBySessionId() {
        runBlocking {
          val updatedSessionInfo = sessionInfo.copy(
            resendCount = sessionInfo.resendCount + 1,
            incorrectTokenRetries = sessionInfo.incorrectTokenRetries + 1
          )

          redisClient.saveSessionInfoBySessionId(sessionId, updatedSessionInfo)

          val foundWebviewSessionInfo = redisClient.findSessionInfoBySessionId(sessionId)
          Assertions.assertThat(foundWebviewSessionInfo).isNotNull
          Assertions.assertThat(foundWebviewSessionInfo).usingRecursiveComparison().isEqualTo(updatedSessionInfo)
        }
      }
    }

    @Nested
    @DisplayName("Website Session Tests")
    inner class WebsiteSessionTests {
      lateinit var id1: String
      lateinit var id2: String
      lateinit var id3: String
      lateinit var idForAuthorizationCode: String

      @BeforeEach
      fun setUpWebviewSessionTests() {
        runBlocking {
          id1 = redisClient.saveWebsiteSession(websiteSession1)
          id2 = redisClient.saveWebsiteSessionByAuthenticationCode(authenticationCode, websiteSession2)
          id3 = redisClient.saveWebsiteSessionByVerificationCode(verificationCode, websiteSession3)
          idForAuthorizationCode = redisClient.saveWebsiteSessionByAuthorizationCode(authorizationCode, websiteSession1)
        }
      }

      @Test
      fun findWebsiteSessionBySessionIdAndAuthenticationCode() {
        runBlocking {
          val foundWebsiteSession = redisClient.findWebsiteSessionBySessionIdAndAuthenticationCode(id2, authenticationCode)
          Assertions.assertThat(foundWebsiteSession).isNotNull
          Assertions.assertThat(foundWebsiteSession!!.id).isEqualTo(id2)
          Assertions.assertThat(foundWebsiteSession.callbackUrl).isEqualTo(websiteSession2.callbackUrl)
        }
      }

      @Test
      fun findWebsiteSessionBySessionIdAndVerificationCode() {
        runBlocking {
          val foundWebsiteSession = redisClient.findWebsiteSessionBySessionIdAndVerificationCode(id3, verificationCode)
          Assertions.assertThat(foundWebsiteSession).isNotNull
          Assertions.assertThat(foundWebsiteSession!!.id).isNotNull
          Assertions.assertThat(foundWebsiteSession.callbackUrl).isEqualTo(websiteSession3.callbackUrl)
        }
      }

      @Test
      fun findWebsiteSessionBySessionId() {
        runBlocking {
          val foundWebsiteSession = redisClient.findWebsiteSessionBySessionId(id1)
          Assertions.assertThat(foundWebsiteSession).isNotNull
          Assertions.assertThat(foundWebsiteSession!!.id).isEqualTo(id1)
          Assertions.assertThat(foundWebsiteSession.callbackUrl).isEqualTo(websiteSession1.callbackUrl)
        }
      }

      @Test
      fun updateWebsiteSessionBySessionId() {
        runBlocking {
          // GIVEN
          val updatedWebsiteSession = websiteSession1.copy(
            id = id1,
            incorrectTokenRetries = websiteSession1.incorrectTokenRetries + 1
          )

          // WHEN
          redisClient.saveWebsiteSession(updatedWebsiteSession)

          // THEN
          val foundWebsiteSession = redisClient.findWebsiteSessionBySessionId(id1)
          Assertions.assertThat(foundWebsiteSession).isNotNull
          Assertions.assertThat(foundWebsiteSession).usingRecursiveComparison().isEqualTo(updatedWebsiteSession)
        }
      }

      @Test
      fun deleteWebsiteSessionBySessionId() {
        runBlocking {
          redisClient.deleteWebsiteSessionBySessionId(id1)
          val foundWebsiteSession = redisClient.findWebsiteSessionBySessionId(id1)
          Assertions.assertThat(foundWebsiteSession).isNull()
        }
      }

      @Test
      fun findWebsiteSessionBySessionIdAndAuthorizationCode(): Unit = runBlocking {
        val foundWebsiteSession = redisClient.findWebsiteSessionBySessionIdAndAuthorizationCode(idForAuthorizationCode, authorizationCode)
        Assertions.assertThat(foundWebsiteSession).isNotNull
      }

      @Test
      fun getAndDeleteWebsiteSessionBySessionIdAndAuthenticationCode(): Unit = runBlocking {
        val previousWebsiteSession = redisClient.findWebsiteSessionBySessionIdAndAuthenticationCode(id2, authenticationCode)
        val deletedWebsiteSession = redisClient.getAndDeleteWebsiteSessionByAuthenticationCode(id2, authenticationCode)
        Assertions.assertThat(previousWebsiteSession).isEqualTo(deletedWebsiteSession)
        val foundWebsiteSession = redisClient.findWebsiteSessionBySessionIdAndAuthenticationCode(id2, authenticationCode)
        val foundWebsiteSessionJustSessionId = redisClient.findWebsiteSessionBySessionId(id2)
        Assertions.assertThat(foundWebsiteSession).isNull()
        Assertions.assertThat(foundWebsiteSessionJustSessionId).isNull()
        val deleteAgain = redisClient.getAndDeleteWebsiteSessionByAuthenticationCode(id2, authenticationCode)
        Assertions.assertThat(deleteAgain).isNull()
      }

      @Test
      fun getAndDeleteWebsiteSessionAndAuthenticationCodeNoExistingSession(): Unit = runBlocking {
        val deletedWebsiteSession = redisClient.getAndDeleteWebsiteSessionByAuthenticationCode("madeupid", authenticationCode)
        Assertions.assertThat(deletedWebsiteSession).isNull()
      }
    }

    @Nested
    @DisplayName("PayloadToSignTests")
    inner class PayloadToSignTests {
      lateinit var payloadToSignRequest: PayloadToSignRequest<AddProviderPayloadRequest>
      lateinit var payloadToSignRequest2: PayloadToSignRequest<HandlePayloadRequest>
      lateinit var payloadToSignRequest3: PayloadToSignRequest<AddProviderPayloadRequest>
      lateinit var sessionId: String
      lateinit var sessionId2: String
      lateinit var sessionId3: String
      @BeforeEach
      fun setUpPayloadToSignRequests(){
        runBlocking {
          payloadToSignRequest = PayloadToSignRequest(
            null,
            "ttt",
            userIdentifier,
            publicKeyDto,
            signature,
            "ttt",
            AddProviderPayloadRequest(1.toBigInteger(), listOf(5, 6, 7, 8), URI.create("google.com")),
            null,
            null,
          )
          payloadToSignRequest2 = PayloadToSignRequest(
            null,
            "ttt",
            userIdentifier,
            publicKeyDto,
            signature,
            "ttt",
            HandlePayloadRequest("sampleHandle"),
            null,
            null,
          )
          payloadToSignRequest3 = PayloadToSignRequest(
            null,
            "ttt",
            userIdentifier,
            publicKeyDto,
            signature,
            "ttt",
            AddProviderPayloadRequest(3.toBigInteger(), listOf(1, 4, 7, 10), URI.create("yahoo.com")),
            null,
            null,
          )
          sessionId = redisClient.savePayloadToSign(
            payloadToSignRequest
          )
          sessionId2 = redisClient.savePayloadToSignByAuthenticationCode(
            authenticationCode,
            payloadToSignRequest2
          )
          sessionId3 = redisClient.savePayloadToSignByAuthorizationCode(
            authorizationCode,
            payloadToSignRequest3
          )
        }
      }

      @Test
      fun findPayloadToSign() {
        runBlocking {
          val foundPayload = redisClient.findPayloadToSignBySessionId<AddProviderPayloadRequest>(sessionId)
          Assertions.assertThat(foundPayload).isNotNull
          Assertions.assertThat(foundPayload!!.payload).usingRecursiveComparison().isEqualTo(payloadToSignRequest.payload)
        }
      }

      @Test
      fun findPayloadToSignByAuthorizationCode() {
        runBlocking {
          val foundPayload = redisClient.findPayloadToSignBySessionIdAndAuthorizationCode<AddProviderPayloadRequest>(
            sessionId3,
            authorizationCode
          )
          Assertions.assertThat(foundPayload).isNotNull
          Assertions.assertThat(foundPayload!!.payload).usingRecursiveComparison().isEqualTo(payloadToSignRequest3.payload)
        }
      }

      @Test
      fun deletePayloadToSignBySessionId() {
        runBlocking {
          redisClient.deletePayloadToSignBySessionId(sessionId)
          val foundPayload = redisClient.findPayloadToSignBySessionId<AddProviderPayloadRequest>(sessionId)
          Assertions.assertThat(foundPayload).isNull()
        }
      }
    }

    @Nested
    @DisplayName("BatchPayloadToSignTests")
    inner class BatchPayloadToSignTests {
      private lateinit var batchPayloadToSignRequest: BatchPayloadToSignRequest
      private lateinit var batchPayloadToSignRequest2: BatchPayloadToSignRequest
      private lateinit var batchPayloadToSignRequest3: BatchPayloadToSignRequest
      private lateinit var sessionId: String
      private lateinit var sessionId2: String
      private lateinit var sessionId3: String
      private lateinit var payloads: List<TypedPayloadRequestWithSignature<PayloadRequest>>
      private lateinit var payloads2: List<TypedPayloadRequestWithSignature<PayloadRequest>>
      private lateinit var payloads3: List<TypedPayloadRequestWithSignature<PayloadRequest>>
      private lateinit var typedPayload1: TypedPayloadRequestWithSignature<PayloadRequest>
      private lateinit var typedPayload2: TypedPayloadRequestWithSignature<PayloadRequest>
      private lateinit var typedPayload3: TypedPayloadRequestWithSignature<PayloadRequest>

      @BeforeEach
      fun setUpPayloadToSignRequests(){
        runBlocking {
          typedPayload1 = TypedPayloadRequestWithSignature(
            signature,
            PayloadType.ADD_PROVIDER,
            AddProviderPayloadRequest(1.toBigInteger(), listOf(5, 6, 7, 8), URI.create("google.com"))
          )
          typedPayload2 = TypedPayloadRequestWithSignature(
            signature,
            PayloadType.CLAIM_HANDLE,
            HandlePayloadRequest("sampleHandle")
          )
          typedPayload3 = TypedPayloadRequestWithSignature(
            signature,
            PayloadType.ADD_PROVIDER,
            AddProviderPayloadRequest(1.toBigInteger(), listOf(1, 2, 3, 4), URI.create("google.com"))
          )
          payloads = listOf(typedPayload1, typedPayload2)
          payloads2 = listOf(typedPayload2, typedPayload1)
          payloads3 = listOf(typedPayload1, typedPayload3)
          batchPayloadToSignRequest = BatchPayloadToSignRequest(
            null,
            "ttt",
            userIdentifier,
            publicKeyDto,
            "google.com",
            payloads,
            null,
            null,
          )
          batchPayloadToSignRequest2 = BatchPayloadToSignRequest(
            null,
            "ttt",
            userIdentifier,
            publicKeyDto,
            "google.com",
            payloads2,
            null,
            null,
          )
          batchPayloadToSignRequest3 = BatchPayloadToSignRequest(
            null,
            "ttt",
            userIdentifier,
            publicKeyDto,
            "google.com",
            payloads3,
            null,
            null,
          )
          sessionId = redisClient.saveBatchPayloadToSignRequest(
            batchPayloadToSignRequest
          )
          sessionId2 = redisClient.saveBatchPayloadToSignRequestByAuthenticationCode(
            authenticationCode,
            batchPayloadToSignRequest2
          )
          sessionId3 = redisClient.saveBatchPayloadToSignRequestByAuthorizationCode(
            authorizationCode,
            batchPayloadToSignRequest3
          )
        }
      }

      @Test
      fun findBatchPayloadToSign() {
        runBlocking {
          val foundPayload = redisClient.findBatchPayloadToSignRequestBySessionId(sessionId)
          Assertions.assertThat(foundPayload).isNotNull
          Assertions.assertThat(foundPayload!!.payloads).usingRecursiveComparison().isEqualTo(payloads)
        }
      }

      @Test
      fun findPayloadToSignByAuthenticationCode() {
        runBlocking {
          val foundPayload = redisClient.findBatchPayloadToSignRequestBySessionIdAndAuthenticationCode(
            sessionId2,
            authenticationCode
          )
          Assertions.assertThat(foundPayload).isNotNull
          Assertions.assertThat(foundPayload!!.payloads).usingRecursiveComparison().isEqualTo(payloads2)
        }
      }

      @Test
      fun findPayloadToSignByAuthorizationCode() {
        runBlocking {
          val foundPayload = redisClient.findBatchPayloadToSignRequestBySessionIdAndAuthorizationCode(
            sessionId3,
            authorizationCode
          )
          Assertions.assertThat(foundPayload).isNotNull
          Assertions.assertThat(foundPayload!!.payloads).usingRecursiveComparison().isEqualTo(payloads3)
        }
      }

      @Test
      fun deletePayloadToSignBySessionId() {
        runBlocking {
          redisClient.deleteBatchPayloadToSignRequestBySessionId(sessionId)
          val foundPayload = redisClient.findBatchPayloadToSignRequestBySessionId(sessionId)
          Assertions.assertThat(foundPayload).isNull()
        }
      }
    }

    @Nested
    @DisplayName("Siwa Session Tests")
    inner class SiwaSessionTests {

      private val signatureRequest = SignedSiwaSignatureRequest(
        publicKeyDto,
        signature,
        SiwaSignatureRequest("example-callback", listOf(1), "example-userIdentifierAdminUrl")
      )
      private val request = SiwaRequest(
        signatureRequest,
        listOf(
          RequestedCredential.AnyOf(
            listOf(
              RequestedCredential.SpecificCredential(
                RequestedCredentialType.VerifiedEmailAddressCredential,
                listOf("???")
              )
            )
          )
        )
      )

      private val unauthenticatedSiwaSession = UnauthenticatedSiwaSession(
        request,
        fullCallbackUrl = request.signatureRequest.payload.callback,
        userKeyPairType = KeyPairType.SR25519,
        flowKind = SiwaFlowKind.SOCIAL,
      )
      private val authorizationCode = generateUUID()
      private val authenticatedSiwaSession = AuthenticatedSiwaSession(
        request,
        authorizationCode,
        userIdentifier,
        request.signatureRequest.payload.callback,
        KeyPairType.SR25519,
        SiwaFlowKind.SOCIAL,
      )

      @Test
      fun saveUnauthenticatedSiwaSession() {
        runBlocking {
          // WHEN
          redisClient.saveSiwaSession(unauthenticatedSiwaSession)

          // THEN
          val foundSiwaSession = redisClient.findSiwaSessionBySessionId(unauthenticatedSiwaSession.id)
          Assertions.assertThat(foundSiwaSession).isNotNull
          Assertions.assertThat(foundSiwaSession).usingRecursiveComparison().isEqualTo(unauthenticatedSiwaSession)
        }
      }

      @Test
      fun saveAuthenticatedSiwaSession() {
        runBlocking {
          // WHEN
          redisClient.saveSiwaSession(authenticatedSiwaSession)

          // THEN
          val foundSiwaSession = redisClient.findSiwaSessionBySessionId(authenticatedSiwaSession.id)
          Assertions.assertThat(foundSiwaSession).isNotNull
          Assertions.assertThat(foundSiwaSession).usingRecursiveComparison().isEqualTo(authenticatedSiwaSession)
        }
      }

      @Nested
      @DisplayName("With a saved Unauthenticated Session")
      inner class WithSavedUnauthenticatedSession {
        @BeforeEach
        fun saveSession(): Unit = runBlocking {
          redisClient.saveSiwaSession(unauthenticatedSiwaSession)
        }

        @Test
        fun findSiwaSessionBySessionId(): Unit = runBlocking {
          val foundSiwaSession = redisClient.findSiwaSessionBySessionId(unauthenticatedSiwaSession.id)
          Assertions.assertThat(foundSiwaSession).usingRecursiveComparison().isEqualTo(unauthenticatedSiwaSession)

        }

        @Test
        fun deleteSiwaSessionBySessionId(): Unit = runBlocking {
          // WHEN
          redisClient.deleteSiwaSessionBySessionId(unauthenticatedSiwaSession.id)

          // THEN
          val foundWebsiteSession = redisClient.findSiwaSessionBySessionId(unauthenticatedSiwaSession.id)
          Assertions.assertThat(foundWebsiteSession).isNull()
        }
      }

      @Nested
      @DisplayName("With a saved Authenticated Session")
      inner class WithSavedAuthenticatedSession {

        @BeforeEach
        fun saveSession(): Unit = runBlocking {
          redisClient.saveSiwaSession(authenticatedSiwaSession)
        }

        @Test
        fun findSiwaSessionBySessionId(): Unit = runBlocking {
          val foundSiwaSession = redisClient.findSiwaSessionBySessionId(authenticatedSiwaSession.id)
          Assertions.assertThat(foundSiwaSession).usingRecursiveComparison().isEqualTo(authenticatedSiwaSession)

        }

        @Test
        fun deleteSiwaSessionBySessionId(): Unit = runBlocking {
          // WHEN
          redisClient.deleteSiwaSessionBySessionId(authenticatedSiwaSession.id)

          // THEN
          val foundWebsiteSession = redisClient.findSiwaSessionBySessionId(unauthenticatedSiwaSession.id)
          Assertions.assertThat(foundWebsiteSession).isNull()
        }

        @Test
        fun findSiwaSessionIdByAuthorizationCode(): Unit = runBlocking {
          //GIVEN
          redisClient.saveSiwaSessionIdByAuthorizationCode(authenticatedSiwaSession.id, authorizationCode)

          //WHEN
          val siwaSessionId = redisClient.findSiwaSessionIdByAuthorizationCode(authorizationCode)

          //THEN
          Assertions.assertThat(siwaSessionId).isEqualTo(authenticatedSiwaSession.id)
        }

        @Test
        fun findSiwaSessionByAuthorizationCode(): Unit = runBlocking {
          //GIVEN
          redisClient.saveSiwaSessionIdByAuthorizationCode(authenticatedSiwaSession.id, authorizationCode)

          //WHEN
          val foundSiwaSession = redisClient.findSiwaSessionByAuthorizationCode(authorizationCode)

          //THEN
          Assertions.assertThat(foundSiwaSession).usingRecursiveComparison().isEqualTo(authenticatedSiwaSession)
        }
      }
    }

    @Nested
    @DisplayName("Ses Template Tests")
    inner class SesTemplateTests {
      private val sesTemplate = SesTemplate("MySesTemplate")
      private val sesTemplates = setOf(sesTemplate)

      @BeforeEach
      fun saveSesTemplates() = runBlocking {
        redisClient.saveSesTemplates(sesTemplates)
      }

      @Test
      fun findAllSesTemplates(): Unit = runBlocking {
        //GIVEN WHEN
        val foundSesTemplates = redisClient.findAllSesTemplates()

        //THEN
        Assertions.assertThat(foundSesTemplates).containsAll(sesTemplates)
      }
    }

    @Nested
    @DisplayName("Migration Task No Msa Tests")
    inner class MigrationTaskNoMsaTests {

      @AfterEach
      fun teardown(): Unit = runBlocking {
        redisClient.deleteMigrationTaskNoMsaCount()
      }

      @Test
      fun findNoMsaCountIsNull(): Unit = runBlocking {
        // GIVEN WHEN
        val migrationTaskNoMsaCount = redisClient.findMigrationTaskNoMsaCount()

        //THEN
        Assertions.assertThat(migrationTaskNoMsaCount).isNull()
      }

      @Test
      fun findNoMsaCount(): Unit = runBlocking {
        //GIVEN
        redisClient.saveMigrationTaskNoMsaCount(10)

        // WHEN
        val migrationTaskNoMsaCount = redisClient.findMigrationTaskNoMsaCount()

        //THEN
        Assertions.assertThat(migrationTaskNoMsaCount).isNotNull
        Assertions.assertThat(migrationTaskNoMsaCount).isEqualTo(10)
      }
    }

    @Test
    fun saveUserActivityRecord(): Unit = runBlocking {
      // GIVEN
      val record = UserActivityRecord(
        BigInteger.TEN,
        Duration.ofSeconds(3600),
        Instant.now(),
      )

      // WHEN
      redisClient.saveUserActivityRecord(record)
      val result = redisClient.findUserActivityRecord(record.userAccountId)

      // THEN
      Assertions.assertThat(result).usingRecursiveComparison().isEqualTo(record)
    }
  }

  @Nested
  @DisplayName("AsyncSubmission Tests")
  inner class AsyncSubmissionTests {
    @Test
    fun saveAndFindAsyncSubmissionUnresolved(): Unit = runBlocking {
      //GIVEN
      val asyncSubmission = AsyncSubmission<Any>(generateUUID(), SubmissionStatus.SUBMITTED)
      val savedAsyncSubmission = redisClient.saveAsyncSubmission(asyncSubmission)

      //WHEN
      val foundSavedAsyncSubmission = redisClient.findAsyncSubmission<Any>(savedAsyncSubmission.id)

      //THEN
      Assertions.assertThat(foundSavedAsyncSubmission).usingRecursiveComparison().isEqualTo(savedAsyncSubmission)
    }
  }

  @Test
  fun saveAndFindAsyncSubmissionApiErrorCodeResolved(): Unit = runBlocking {
    //GIVEN
    val asyncSubmission = AsyncSubmission<Any>(generateUUID(), SubmissionStatus.FAILED, Either.Left(1234))
    val savedAsyncSubmission = redisClient.saveAsyncSubmission(asyncSubmission)

    //WHEN
    val foundSavedAsyncSubmission = redisClient.findAsyncSubmission<Any>(savedAsyncSubmission.id)

    //THEN
    Assertions.assertThat(foundSavedAsyncSubmission).usingRecursiveComparison().isEqualTo(savedAsyncSubmission)
  }

  @Test
  fun saveAndFindAsyncSubmissionDelegationGrantedResolved(): Unit = runBlocking {
    //GIVEN
    val asyncSubmission = AsyncSubmission<Any>(
      generateUUID(),
      SubmissionStatus.SUCCESS,
      Either.Right(DelegationGranted(1.toBigInteger(), 2.toBigInteger()))
    )
    val savedAsyncSubmission = redisClient.saveAsyncSubmission(asyncSubmission)

    //WHEN
    val foundSavedAsyncSubmission = redisClient.findAsyncSubmission<Any>(savedAsyncSubmission.id)

    //THEN
    Assertions.assertThat(foundSavedAsyncSubmission).usingRecursiveComparison().isEqualTo(savedAsyncSubmission)
  }
}

@Configuration
class MoreConfig{
  @Bean
  fun objectMapper(): ObjectMapper {
    return jacksonObjectMapper().registerModule(JavaTimeModule())
  }
}