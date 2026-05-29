package io.amplica.custodial_wallet.orchestration

import arrow.core.Either
import com.strategyobject.substrateclient.crypto.ss58.SS58AddressFormat
import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.client.captcha.CaptchaClient
import io.amplica.custodial_wallet.client.kms.EncryptedKey
import io.amplica.custodial_wallet.client.kms.KmsClient
import io.amplica.custodial_wallet.client.kms.KmsDecryptionKey
import io.amplica.custodial_wallet.client.kms.KmsEncryptionAlgorithm
import io.amplica.custodial_wallet.client.notification.NotificationServiceClient
import io.amplica.custodial_wallet.client.redis.CustodialWalletRedisClient
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.client.redis.dto.LoginPayload
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifier
import io.amplica.custodial_wallet.controller.util.RetryHelper
import io.amplica.custodial_wallet.db.repository.*
import io.amplica.custodial_wallet.dto.AccountInfo
import io.amplica.custodial_wallet.dto.AddIdentifierVerificationResponse
import io.amplica.custodial_wallet.dto.GetHandleResponse
import io.amplica.custodial_wallet.dto.ProviderUserInfo
import io.amplica.custodial_wallet.email.EmailService
import io.amplica.custodial_wallet.email.client.*
import io.amplica.custodial_wallet.email.client.conf.AwsSesProperties
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.internationalization.MessageFactory
import io.amplica.custodial_wallet.orchestration.passkey.PasskeyWalletService
import io.amplica.custodial_wallet.orchestration.signing.SigningOrchestrationService
import io.amplica.custodial_wallet.orchestration.util.mapUserIdentifiersToUserDetails
import io.amplica.custodial_wallet.orchestration.util.mapUserIdentifiersToUserIdentifiersResponse
import io.amplica.custodial_wallet.service.password.PasswordService
import io.amplica.custodial_wallet.service.provider_metadata.ProviderMetadata
import io.amplica.custodial_wallet.service.provider_metadata.ProviderMetadataService
import io.amplica.custodial_wallet.util.createTransactionalOperatorDouble
import io.amplica.custodial_wallet.util.fromHex
import io.amplica.custodial_wallet.util.generateUniquePassword
import io.amplica.custodial_wallet.util.key_creation.*
import io.amplica.custodial_wallet.util.toHex
import io.amplica.frequency.client.*
import io.amplica.frequency.crypto.provider.Sr25519CryptoProvider
import io.amplica.frequency.crypto.toSignatureBytes
import io.amplica.frequency.payload.AddGraphKeyPayload
import io.amplica.frequency.payload.AddProviderPayload
import io.amplica.frequency.payload.CreateHandlePayload
import io.amplica.frequency.service.SigningService
import io.amplica.frequency.util.FrequencyEnvironment
import io.amplica.frequency.util.GraphConfiguration
import io.amplica.frequency.util.GraphHelper
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.*
import java.math.BigInteger
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture

class CustodialWalletOrchestrationServiceTest {
  private lateinit var mockSesClient: SesClient
  private lateinit var mockRedisClient: CustodialWalletRedisClient
  private lateinit var mockDatabaseService: CustodialWalletDatabaseService
  private lateinit var mockKmsClient: KmsClient
  private lateinit var mockFrequencyClient: FrequencyClient
  private lateinit var mockSigningService: SigningService
  private lateinit var mockSigningOrchestrationService: SigningOrchestrationService
  private lateinit var orchestration: DefaultCustodialWalletOrchestrationService
  private lateinit var providerMetadataService: ProviderMetadataService
  private lateinit var graphHelper: GraphHelper
  private lateinit var mockMessageFactory: MessageFactory
  private lateinit var mockRetryHelper: RetryHelper
  private lateinit var mockLookupService: LookupOrchestrationService
  private lateinit var mockNotificationServiceClient: NotificationServiceClient
  private lateinit var mockEmailService: EmailService
  private lateinit var mockPasswordService: PasswordService
  private lateinit var mockPasskeyWalletService: PasskeyWalletService
  private lateinit var mockCaptchaClient: CaptchaClient

  companion object {
    private const val authorizedReturnHost = "google.com"
    private val authorizedReturnURL: URI = URI.create("https://$authorizedReturnHost")
    const val expirationBlock = 10L

    // We mock frequency client to always return block head to be 1 down below
    val signature = Signature(SignatureKeyPairType.SR25519, Encoding.HEX, "0x3259258c")
    val graphSignature = Signature(
      SignatureKeyPairType.SR25519,
      Encoding.HEX,
      "0xf41a8063cacabc88ddc914a3ed85e1073efc992458b4d12ba791bb7d02f6d918490985936430d93dbe5f60d044919c99b5e7e5e1c341f4e8c36fcc09c09a1d84"
    )

    val addProviderPayloadRequest = AddProviderPayloadRequest(
      BigInteger.ONE,
      emptyList(),
      authorizedReturnURL
    )
    val handlePayloadRequest = HandlePayloadRequest(
      "testHandle"
    )
    private val handleRequest = HandleRequest(
      signature,
      handlePayloadRequest
    )
    private const val nonce = "nonce"
    private val loginPayload = LoginPayload(
      nonce,
      authorizedReturnURL
    )

    const val externalUserId = "someUserId"
    val emailIdentifier = UserIdentifier("some@user.com", UserIdentifierType.EMAIL)
    private val phoneIdentifier = UserIdentifier("+3331112222", UserIdentifierType.PHONE_NUMBER)
    private val emailIdentifierNullPriority = UserIdentifier("someOther@user.com", UserIdentifierType.EMAIL)
    private val phoneIdentifierNullPriority = UserIdentifier("+3334442222", UserIdentifierType.PHONE_NUMBER)
    private val userIdentifiers = listOf(emailIdentifier, phoneIdentifier)
    private val userIdentifiersNullPriority = listOf(emailIdentifierNullPriority, phoneIdentifierNullPriority)
    private val publicKey = byteArrayOf(-44, 53, -109, -57, 21, -3, -45, 28, 97, 20, 26, -67, 4, -87, -97, -42, -126, 44, -123, 88, -123, 76, -51, -29, -102, 86, -124, -25, -91, 109, -94, 125)
    private val userPublicKeyBytes = byteArrayOf(-44, 53, -109, -57, 21, -3, -45, 28, 97, 20, 26, -67, 4, -87, -97, -42, -126, 44, -123, 88, -123, 76, -51, -29, -102, 86, -124, -25, -91, 109, -94, 125)
    private val publicKeyEncoded =
      Sr25519KeyPairCreator.encodeSr25519PublicKey(publicKey, SS58AddressFormat.BARE_SR_25519)
    private val publicKeyDto: PublicKeyDto =
      PublicKeyDto(publicKeyEncoded, Encoding.BASE_58, PublicKeyFormat.SS58, KeyPairType.SR25519)
    private val userPublicKey: PublicKeyDto = PublicKeyDto(
      Sr25519KeyPairCreator.encodeSr25519PublicKey(
        userPublicKeyBytes, SS58AddressFormat.BARE_SR_25519
      ), Encoding.BASE_58, PublicKeyFormat.SS58, KeyPairType.SR25519
    )

    val signUpRequest = SignUpRequest(
      externalUserId,
      userIdentifiers,
      publicKeyDto,
      signature,
      addProviderPayloadRequest,
      handleRequest
    )


    val loginRequest = LoginRequest(
      userPublicKey,
      externalUserId,
      emailIdentifier,
      publicKeyDto,
      signature,
      loginPayload
    )

    val loginRequestSms = LoginRequest(
      userPublicKey,
      externalUserId,
      phoneIdentifier,
      publicKeyDto,
      signature,
      loginPayload
    )

    private val keypair = Sr25519KeyPairCreator.createKeyPair()
    val privateKey = keypair.privateKeyBytes
    val privateKeyEncrypted = keypair.privateKeyBytes

    private val walletId = 1.toBigInteger()
    private val publicKeyHex = toHex(keypair.publicKeyBytes)
    private val encryptedPrivateKeyHex = toHex(privateKeyEncrypted)
    private val encryptedPrivateKeyType = KeyPairType.SR25519
    private val accountKeyUsageType = KeyUsageType.ACCOUNT
    private const val kmsEncryptionKeyId = "8c67db3ad31b432b86843ea633fca71555bf624e"
    private val kmsEncryptionKeyIdType = KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT
    private val createdAt = Instant.now().toEpochMilli().toBigInteger()
    private val lastModified = createdAt


    val fullUserKeyData = UserKeyData(
      1.toBigInteger(),
      walletId,
      publicKeyHex,
      encryptedPrivateKeyHex,
      encryptedPrivateKeyType,
      kmsEncryptionKeyId,
      kmsEncryptionKeyIdType,
      accountKeyUsageType,
      null,
      createdAt,
      lastModified,
      1.toBigInteger()
    )

    const val sessionId = "sampleSessionId"
    const val sessionId2 = "anotherSessionId"
    const val authenticationCode = "123456"
    const val verificationCode = "654321"

    const val awsSesSourceName = "Teddy Willard"
    const val awsSesSourceEmail = "teddywillard@gmail.com"
    const val awsSesLoginTemplateName = "FrequencyAccessLoginTemplate"
    const val awsSesSignupTemplateName = "FrequencyAccessSignupTemplate"
    const val awsSesDirectLoginTemplateName = "FrequencyAccessDirectLogin"
    const val awsSesAddIdentifierTemplateName = "FrequencyAccessDirectLogin"
    const val awsSesTokensReceivedTemplateName = "CommunityRewardsTokensReceived"
    const val smsDirectLoginTemplateName = "frequencyAccessDirectLogin"
    const val smsAddIdentifieTemplateName = "addIdentifie"
    const val smsWebviewSignUpTemplateName = "frequencyAccessWebviewSignup"
    const val smsWebviewLoginTemplateName = "frequencyAccessWebviewLogin"
    const val awsKmsKeyId = "dafhdsfhsd-hshshgfshfh-shfrshdfshsd-fhsdfhsdfhfdgshdf"
    val expiration: Duration = Duration.ofSeconds(300)
    val timerExpiration: Duration = Duration.ofSeconds(90)
    val otpExpiration: Duration = Duration.ofMinutes(20)
    const val resendLimit = 20
    const val smsSourceNumber = "+15128675309"

    const val hostName = "http://localhost:8080"
    val meweMetadata = ProviderMetadata("MeWe", "mewe", emptyList(), emptyMap())
    val unfinishedMetadata = ProviderMetadata("Unfinished", "unfinished", emptyList(), emptyMap())
    val schemaIdPermissionsMap = mapOf(
      setOf(7, 8, 9, 10) to "permission.account.graph"
    )

    val userActivityExpiration = Duration.ofMinutes(5)
    val changeHandlePeriod = Duration.ofMinutes(5)

    val sampleSessionInfo =
      SessionInfo(true, authorizedReturnURL.toString(), System.currentTimeMillis() - 180000L, 0, 0)
  }

  @BeforeEach
  fun setUp(): Unit = runBlocking {

    mockSesClient = mock()
    mockRedisClient = mock()
    mockDatabaseService = mock()
    mockKmsClient = mock()
    mockFrequencyClient = mock()
    mockSigningService = mock()
    mockSigningOrchestrationService = mock()
    providerMetadataService = mock()
    graphHelper = GraphHelper(GraphConfiguration(FrequencyEnvironment.ROCOCO, listOf()))
    mockMessageFactory = mock()
    mockRetryHelper = mock()
    mockLookupService = mock()
    mockNotificationServiceClient = mock()
    mockEmailService = mock()
    mockPasswordService = mock()
    mockPasskeyWalletService = mock()
    mockCaptchaClient = mock()

    orchestration = DefaultCustodialWalletOrchestrationService(
      DefaultCustodialWalletProperties(
        expirationBlock,
        timerExpiration,
        otpExpiration,
        resendLimit,
        hostName,
        schemaIdPermissionsMap,
        true,
        userActivityExpiration,
        changeHandlePeriod,
        AwsSesProperties(
          awsSesSourceName,
          awsSesSourceEmail,
          awsSesLoginTemplateName,
          awsSesSignupTemplateName,
          awsSesDirectLoginTemplateName,
          awsSesAddIdentifierTemplateName,
          awsSesTokensReceivedTemplateName,
        ),
        SmsProperties(
          smsSourceNumber,
          smsDirectLoginTemplateName,
          smsAddIdentifieTemplateName,
          smsWebviewSignUpTemplateName,
          smsWebviewLoginTemplateName,
        ),
      ),
      mockRedisClient,
      mockFrequencyClient,
      mockSigningService,
      mockSigningOrchestrationService,
      mockDatabaseService,
      SS58AddressFormat.SUBSTRATE_ACCOUNT,
      mockMessageFactory,
      mockRetryHelper,
      createTransactionalOperatorDouble(),
      mockLookupService,
      mockNotificationServiceClient,
      mockEmailService,
      mockPasswordService,
      mockPasskeyWalletService,
      mockCaptchaClient,
    )


    whenever(mockRedisClient.timeToLive).thenReturn(expiration)

    whenever(
      mockSigningOrchestrationService.signPayload(
        any(),
        argThat<AddProviderPayload> { this.authorizedMsaId == addProviderPayloadRequest.msaId },
        any(),
      )
    ).thenReturn(signature)

    whenever(
      mockSigningOrchestrationService.signPayload(
        any(),
        isA<AddGraphKeyPayload>(),
        eq(Encoding.HEX)
      )
    ).thenReturn(graphSignature)

    whenever(
      mockSigningOrchestrationService.signPayload(
        any(),
        isA<CreateHandlePayload>(),
        eq(Encoding.HEX)
      )
    ).thenReturn(signature)

    whenever(
      mockSigningOrchestrationService.signPayload(
        eq(keypair),
        isA<io.amplica.custodial_wallet.orchestration.payload.LoginRequest>(),
        eq(Encoding.HEX)
      )
    ).thenReturn(signature)

    whenever(mockKmsClient.encryptPrivateKey(any(), any())).thenReturn(
      EncryptedKey(
        privateKeyEncrypted,
        KmsDecryptionKey(awsKmsKeyId, KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)
      )
    )
    whenever(mockLookupService.getDecryptedPrivateKey(any())).thenReturn(privateKey)

    whenever(mockSesClient.sendEmail(any<SendEmailRequest>())).thenReturn(SendEmailResponse("messageId"))
    whenever(mockSesClient.templateExists(any<TemplateExistsRequest>())).thenReturn(TemplateExistsResponse(true))

    whenever(mockMessageFactory.createMessage(any(), any(), anyOrNull(), anyOrNull())).thenReturn("smsMessage")


    whenever(mockLookupService.findLoginRequestBySessionId(sessionId)).thenReturn(loginRequest)
    whenever(mockLookupService.findLoginRequestBySessionId(sessionId2)).thenReturn(loginRequestSms)
    whenever(mockLookupService.findSignUpRequestBySessionId(sessionId)).thenReturn(signUpRequest)
    whenever(mockLookupService.findSessionInfoBySessionId(sessionId)).thenReturn(sampleSessionInfo)

    whenever(
      mockLookupService.getIdentifierOfTypeOrThrow(
        sessionId,
        signUpRequest.userIdentifiers,
        UserIdentifierType.EMAIL
      )
    ).thenReturn(emailIdentifier)
    whenever(
      mockLookupService.getIdentifierOfTypeOrThrow(
        sessionId,
        signUpRequest.userIdentifiers,
        UserIdentifierType.PHONE_NUMBER
      )
    ).thenReturn(phoneIdentifier)

    whenever(mockLookupService.getGrantedSchemasByMsaId(BigInteger.ONE, BigInteger.ONE)).thenReturn(listOf(7, 8, 9, 10))
    whenever(mockLookupService.getHandle(BigInteger.ONE)).thenReturn(GetHandleResponse("test", "test", 45))
    whenever(mockLookupService.getMsaIdByPublicKeyHex(publicKeyHex)).thenReturn(1.toBigInteger())
    whenever(mockLookupService.getProviderMetaData(BigInteger.ONE)).thenReturn(meweMetadata)
    whenever(mockLookupService.getProviderMetaData(BigInteger.TWO)).thenReturn(unfinishedMetadata)
    whenever(mockLookupService.retrieveCurrentBlockNumber()).thenReturn(1)
    whenever(mockLookupService.retrieveMsaId(any(), any())).thenReturn(1.toBigInteger())
    whenever(mockPasskeyWalletService.walletExistsForAccount(1.toBigInteger())).thenReturn(false)
  }

  @Nested
  @DisplayName("Delete User By User Identifier Tests")
  inner class DeleteUserByUserIdentifierTests {
    private lateinit var userDetails: List<UserDetail>

    @BeforeEach
    fun setup() {
      userDetails = mapUserIdentifiersToUserDetails(listOf(emailIdentifier))
    }

    @Test
    fun deleteUserWithGivenUserIdentifierSuccess(): Unit = runBlocking {
      //GIVEN
      whenever(mockDatabaseService.deleteAllUserAccountsByUserDetailCascading(userDetails[0])).thenReturn(true)

      //WHEN
      val retVal = orchestration.deleteUserByUserIdentifier(emailIdentifier).result

      //THEN
      Assertions.assertThat(retVal).isTrue
    }

    @Test
    fun deleteUserWithGivenUserIdentifierWithNoExistingUserError(): Unit = runBlocking {
      //GIVEN
      whenever(mockDatabaseService.deleteAllUserAccountsByUserDetailCascading(userDetails[0])).thenReturn(false)

      //WHEN
      val retVal = orchestration.deleteUserByUserIdentifier(emailIdentifier).result

      //THEN
      Assertions.assertThat(retVal).isFalse
    }
  }

  @Nested
  @DisplayName("Send Website Login Url Tests")
  inner class SendWebsiteLoginUrlTests {
    private val emailLoginRequest = DirectLoginRequest(emailIdentifier.value, emailIdentifier.type, null)
    private val smsLoginRequest = DirectLoginRequest(phoneIdentifier.value, phoneIdentifier.type, null)

    @BeforeEach
    fun setup(): Unit = runBlocking {
      whenever(
        mockRedisClient.saveWebsiteSessionByAuthenticationCode(
          any(), argThat { websiteSession -> websiteSession.userAccountIds!!.size == 1 })
      ).thenReturn(sessionId)

    }

    @Test
    fun emailSendWebsiteLoginUrlSuccess(): Unit = runBlocking {
      //GIVEN
      whenever(
        mockLookupService.getExistingAccountIdFromContactMethod(
          any(),
          any()
        )
      ).thenReturn(BigInteger.ONE)

      //WHEN
      val returnedSessionId = orchestration.sendLoginUrl(emailLoginRequest, Locale.ENGLISH, null, null,)

      //THEN
      Mockito.verify(mockEmailService, Mockito.times(1)).sendDirectLoginEmail(same(emailLoginRequest.contactMethod), any(), same(sessionId), same(Locale.ENGLISH))
      Assertions.assertThat(returnedSessionId).isEqualTo(sessionId)
    }

    @Test
    fun smsSendWebsiteLoginUrlSuccess(): Unit = runBlocking {
      //GIVEN
      whenever(
        mockLookupService.getExistingAccountIdFromContactMethod(
          any(),
          any()
        )
      ).thenReturn(BigInteger.ONE)

      //WHEN
      val returnedSessionId = orchestration.sendLoginUrl(smsLoginRequest, Locale.ENGLISH, null, null)

      //THEN
      Mockito.verify(mockNotificationServiceClient, Mockito.times(1)).sendSms(any(), isNull(), isNull(), isNull())
      Assertions.assertThat(returnedSessionId).isEqualTo(sessionId)
    }

    @Test
    fun emailSendWebsiteLoginUrlForUnregisteredUserError(): Unit = runBlocking {
      //GIVEN
      whenever(mockLookupService.getExistingAccountIdFromContactMethod(any(), any())).thenReturn(null)

      //WHEN THEN
      Assertions.assertThatThrownBy {
        runBlocking { orchestration.sendLoginUrl(emailLoginRequest, Locale.ENGLISH, null, null) }
      }.isInstanceOf(ApiException::class.java)
        .hasMessage("No User Accounts found for given contact method provided: ${emailIdentifier.value}")
    }

    @Test
    fun sendLoginUrlIllegalCallbackUrl(): Unit = runBlocking {
      //GIVEN
      whenever(mockLookupService.getExistingAccountIdFromContactMethod(any(), any())).thenReturn(null)

      //WHEN THEN
      Assertions.assertThatThrownBy {
        runBlocking {
          orchestration.sendLoginUrl(
            DirectLoginRequest(emailIdentifier.value, emailIdentifier.type, "https://www.illegal.com"),
            Locale.ENGLISH,
            null,
            null,
          )
        }
      }.isInstanceOf(ApiException::class.java)
        .hasMessage("No User Accounts found for given contact method provided: ${emailIdentifier.value}")
    }
  }

  @Nested
  @DisplayName("Retrieve User Info Or Callback Tests")
  inner class RetrieveProviderUserInfoOrCallbackTests {

    private val authorizationCodeSessionId = "authorizationCodeSessionId"
    private val authenticationCode = "123456"
    private val userAccountId = BigInteger.ONE
    private val userAccounts = listOf(userAccountId)
    private val websiteSession = WebsiteSession(null, null, emailIdentifier, userAccounts, authenticationCode)
    private val websiteSessionWithCallback =
      WebsiteSession(null, "http://callback.com", emailIdentifier, userAccounts, authenticationCode)
    private val userDetail = UserDetail("email@test.com", UserDetailType.EMAIL, 1)

    private val userData = listOf(
      UserData(
        BigInteger.ONE,
        publicKeyHex,
        "i7598023454hfnap",
        KeyPairType.SR25519,
        "0832htfq43huwapfh4ofi",
        KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
        BigInteger.ONE,
        "someUserId",
        BigInteger.ONE,
        BigInteger.ONE,
        userDetail.value,
        userDetail.type,
        userDetail.priority
      )
    )



    @Test
    fun handleAuthenticationLinkSuccessfully(): Unit = runBlocking {

      //GIVEN
      whenever(mockRedisClient.findWebsiteSessionBySessionIdAndAuthenticationCode(sessionId, authenticationCode))
        .thenReturn(websiteSession)
      whenever(mockLookupService.getUserDataFromWebsiteSession(websiteSession)).thenReturn(userData)
      whenever(mockLookupService.findUserDetailsFromUserAccountId(userAccountId)).thenReturn(listOf(userDetail))
      whenever(mockPasswordService.checkPasswordExistsByUserAccountId(BigInteger.ONE)).thenReturn(true)


      //WHEN
      val expectedUserInfo = orchestration.mapUserDataToProviderUserInfo(userData)
      val accountInfo = orchestration.retrieveAccountInfoOrCallback(websiteSession).fold({ it }, { null })

      Assertions.assertThat(accountInfo).isNotNull
      Assertions.assertThat(accountInfo!!.userDetails).isNotEmpty
      Assertions.assertThat(accountInfo.userDetails[0].value).isEqualTo("email@test.com")


      val providerUserInfoList: List<ProviderUserInfo> = accountInfo.providerUserInfo

      //THEN
      Assertions.assertThat(providerUserInfoList).isNotEmpty
      Assertions.assertThat(providerUserInfoList).containsAll(expectedUserInfo)
      Assertions.assertThat(providerUserInfoList[0].providerName).isEqualTo("MeWe")
      Assertions.assertThat(providerUserInfoList[0].userHandle).isEqualTo("test.45")
      Assertions.assertThat(providerUserInfoList[0].permissions).contains("permission.account.graph")
      verify(mockRedisClient, times(0)).saveWebsiteSessionByAuthorizationCode(any(), same(websiteSession))
    }

    @Test
    fun handleAuthenticationLinkSuccessfullyCallbackUrlCase(): Unit = runBlocking {
      //GIVEN
      whenever(mockRedisClient.findWebsiteSessionBySessionIdAndAuthenticationCode(sessionId, authenticationCode))
        .thenReturn(websiteSessionWithCallback)
      whenever(mockRedisClient.saveWebsiteSessionByAuthorizationCode(any(), same(websiteSessionWithCallback)))
        .thenReturn(authorizationCodeSessionId)
      whenever(mockLookupService.getUserDataFromWebsiteSession(websiteSessionWithCallback)).thenReturn(userData)
      whenever(mockLookupService.findUserDetailsFromUserAccountId(userAccountId)).thenReturn(listOf(userDetail))
      whenever(mockPasswordService.checkPasswordExistsByUserAccountId(BigInteger.ONE)).thenReturn(true)

      //WHEN
      val userInfoOrRedirect = orchestration.retrieveAccountInfoOrCallback(websiteSessionWithCallback)
      val redirect: String = userInfoOrRedirect.fold({ "FAIL!" }, { it })

      //THEN
      Assertions.assertThat(redirect).isNotNull
      Assertions.assertThat(redirect).isNotEqualTo("FAIL!")
      Assertions.assertThat(redirect).startsWith("redirect:http://callback.com?sessionId=$authorizationCodeSessionId")
    }
  }

  @Nested
  @DisplayName("Authenticate Login Test")
  inner class AuthenticateLoginTest {
    @Test
    fun handleAuthenticationLinkFailedNoWebsiteSession(): Unit = runBlocking {
      val authCode = "notarealcode"
      Assertions.assertThatThrownBy {
        runBlocking {
          orchestration.authenticateLogin(sessionId, authCode, Locale.US)
        }
      }.isInstanceOf(ApiException::class.java)
        .hasMessage("No Website Session found for this session ID $sessionId and/or authentication code $authCode")
    }
  }

  @Nested
  @DisplayName("Login Validate Authorization Code Tests")
  inner class LoginValidateAuthorizationCodeTests {

    val msaId: BigInteger = BigInteger.ONE
    private val websiteSession = WebsiteSession(null, "callbackUrl", msaId = msaId)
    private val websiteSessionNoMsaId = WebsiteSession(null, "callbackUrl")
    private val authorizationCode = "authCode"
    private val sessionId = "sessionId"
    private val authorizationCodeRequest = AuthorizationCodeRequest(authorizationCode, sessionId)

    @Test
    fun loginValidateAuthorizationCode(): Unit = runBlocking {
      //GIVEN
      whenever(
        mockLookupService.findWebsiteSessionBySessionIdAndAuthorizationCode(
          sessionId,
          authorizationCode
        )
      ).thenReturn(websiteSession)

      //WHEN
      val authorizationWebsiteSessionResponse = orchestration.loginValidateAuthorizationCode(authorizationCodeRequest)

      //THEN
      Assertions.assertThat(authorizationWebsiteSessionResponse).isNotNull
      Assertions.assertThat(authorizationWebsiteSessionResponse.msaId).isSameAs(msaId)
    }

    @Test
    fun loginValidateAuthorizationCodeNoMsaIdFound(): Unit = runBlocking {
      //GIVEN
      whenever(
        mockLookupService.findWebsiteSessionBySessionIdAndAuthorizationCode(
          sessionId,
          authorizationCode
        )
      ).thenReturn(websiteSessionNoMsaId)

      //WHEN THEN
      Assertions.assertThatThrownBy {
        runBlocking {
          orchestration.loginValidateAuthorizationCode(authorizationCodeRequest)
        }
      }.isInstanceOf(ApiException::class.java)
        .hasMessage("No msaId found in sessionId=sessionId")
        .extracting("apiError").isEqualTo(ApiError.NO_MSA_ID_FOUND_ERROR)
    }
  }

  @Nested
  @DisplayName("Revoke Delegation Tests")
  inner class RevokeDelegationTests {
    @Test
    fun revokeDelegation(): Unit = runBlocking {
      whenever(
        mockLookupService.findAccountUserKeyDataByProviderMsaIdAndUserDetail(
          BigInteger.ONE,
          UserDetail(emailIdentifier.value, UserDetailType.valueOf(emailIdentifier.type.toString()))
        )
      ).thenReturn(fullUserKeyData)
      whenever(mockKmsClient.decryptPrivateKey(any())).thenReturn(privateKey)
      whenever(mockFrequencyClient.getGrantedSchemasByMsaId(BigInteger.ONE,BigInteger.ONE)).thenReturn(
        CompletableFuture.supplyAsync {
          listOf((SchemaGrantResponse(7, BigInteger.ONE)))
        }
      )
      whenever(mockLookupService.findWebsiteSessionBySessionId(sessionId)).thenReturn(
        WebsiteSession(
          null,
          null,
          emailIdentifier,
          null,
          null,
          null,
          loggedIn = UserState.LOGGED_IN
        )
      )
      whenever(
        mockFrequencyClient.revokeDelegationByDelegator(privateKey, keypair.publicKeyBytes, BigInteger.ONE)
      ).thenReturn(
        CompletableFuture.supplyAsync {
          Either.Right(DelegationRevoked(MessageSourceId(BigInteger.ONE), MessageSourceId(BigInteger.ONE)))
        }
      )
      val revoked = orchestration.revokeDelegation(BigInteger.ONE, publicKeyHex, sessionId)
      Assertions.assertThat(revoked).isTrue
    }
  }

  @Nested
  @DisplayName("Send New Identifier Verification Email Tests")
  inner class SendNewIdentifierVerificationEmailTests {
    @Test
    fun sendNewIdentifierVerificationEmailSuccess(): Unit = runBlocking {
      val userAccountId = BigInteger.ONE
      val newIdentifierEmail = "test@email.com"
      val addIdentifierRequest =
        AddIdentifierRequest(userAccountId, newIdentifierEmail)
      val websiteSession =
        WebsiteSession(sessionId, null, emailIdentifier, listOf(BigInteger.ONE), loggedIn = UserState.LOGGED_IN)
      whenever(mockLookupService.findWebsiteSessionBySessionId(sessionId)).thenReturn(websiteSession)
      orchestration.sendNewIdentifierVerificationEmail(addIdentifierRequest, sessionId, Locale.US)
      Mockito.verify(mockEmailService, Mockito.times(1)).sendNewIdentifierVerificationEmail(same(newIdentifierEmail), any(), same(sessionId), same(Locale.US))
    }
  }

  @Nested
  @DisplayName("Send New Identifier Verification Sms Tests")
  inner class SendNewIdentifierVerificationSmsTests {
    @Test
    fun sendNewIdentifierVerificationSmsSuccess(): Unit = runBlocking {
      val userAccountId = BigInteger.ONE
      val newIdentifierSms = "+10987654321"
      val addIdentifierRequest =
        AddIdentifierRequest(userAccountId, newIdentifierSms)
      val websiteSession =
        WebsiteSession(sessionId, null, phoneIdentifier, listOf(BigInteger.ONE), loggedIn = UserState.LOGGED_IN)
      whenever(mockLookupService.findWebsiteSessionBySessionId(sessionId)).thenReturn(websiteSession)

      orchestration.sendNewIdentifierVerificationSms(addIdentifierRequest, sessionId, Locale.US)
      Mockito.verify(mockNotificationServiceClient, Mockito.times(1))
        .sendSms(any(), isNull(), isNull(), isNull())
    }
  }

  @Nested
  inner class ChangeHandleTests {
    val sessionId = "session-id"
    val userAccountId: BigInteger = BigInteger.TEN
    val session = WebsiteSession(
      sessionId,
      null,
      userAccountIds = listOf(userAccountId),
      loggedIn = UserState.LOGGED_IN
    )
    val staleKeyPair = Sr25519CryptoProvider.createKeyPair()
    val staleUserKeyData = UserKeyData(
      34.toBigInteger(),
      userAccountId,
      toHex(staleKeyPair.publicKeyBytes.bytes),
      "0x1234",
      KeyPairType.SR25519,
      "stale-kms-id",
      KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
      KeyUsageType.ACCOUNT,
      null,
      4_000.toBigInteger(),
      4_000.toBigInteger(),
      BigInteger.ONE,
    )
    val currentKeyPair = Sr25519CryptoProvider.createKeyPair()
    val currentUserKeyData = UserKeyData(
      35.toBigInteger(),
      userAccountId,
      toHex(currentKeyPair.publicKeyBytes.bytes),
      "0x1234",
      KeyPairType.SR25519,
      "stale-kms-id",
      KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
      KeyUsageType.ACCOUNT,
      null,
      10_000.toBigInteger(),
      10_000.toBigInteger(),
      BigInteger.ONE,
    )

    val newHandle = "aaron"

    @BeforeEach
    fun setup(): Unit = runBlocking {
      whenever(mockLookupService.findWebsiteSessionBySessionId(eq(sessionId))).thenReturn(session)

      whenever(
        mockLookupService.findAllUserKeyDataByUserAccountIdAndKeyUsageType(
          eq(userAccountId),
          eq(KeyUsageType.ACCOUNT)
        )
      ).thenReturn(listOf(currentUserKeyData, staleUserKeyData))
      whenever(
        mockLookupService.getDecryptedAccountKeyPair(argThat { this.id == currentUserKeyData.id })
      ).thenReturn(currentKeyPair)
      whenever(mockLookupService.retrieveCurrentBlockNumber()).thenReturn(680L)

      whenever(
        mockSigningService.signPayload(
          eq(currentKeyPair),
          eq(CreateHandlePayload(newHandle, 690L))
        )
      ).thenReturn(fromHex("0x6802").toSignatureBytes())
    }

    @Test
    fun changeHandleSucceeds(): Unit = runBlocking {
      // GIVEN
      val mockClaimedHandle = newHandle + ".23"
      whenever(
        mockFrequencyClient.changeHandleWithCapacity(
          any(),
          any(),
          eq(fromHex("0x6802")),
          any()
        )
      ).thenReturn(
        CompletableFuture.completedFuture(
          Either.Right(
            HandleClaimed(
              MessageSourceId(23.toBigInteger()),
              mockClaimedHandle
            )
          )
        )
      )

      // WHEN
      val claimedHandle = orchestration.changeHandle(sessionId, newHandle)

      // THEN
      Assertions.assertThat(claimedHandle).isEqualTo(mockClaimedHandle)
      // Updates the rate-limiting timestamp in redis
      verify(mockRedisClient, times(1)).saveUserActivityRecord(argThat { record ->
        record.userAccountId == userAccountId
                && record.handleLastChanged != null
                && (Instant.now() - Duration.ofMinutes(60)) < record.handleLastChanged
      })
    }

    @Test
    fun changeHandleFailsDueToRateLimit(): Unit = runBlocking {
      // GIVEN
      whenever(mockRedisClient.findUserActivityRecord(eq(userAccountId))).thenReturn(
        UserActivityRecord(
          userAccountId,
          userActivityExpiration,
          Instant.now(),
        )
      )

      // WHEN / THEN
      Assertions.assertThatThrownBy {
        runBlocking { orchestration.changeHandle(sessionId, newHandle) }
      }.isInstanceOf(ApiException::class.java)
        .hasMessageStartingWith("Change handle requested too soon")
        .extracting("apiError").isEqualTo(ApiError.CHANGE_HANDLE_TOO_SOON)
    }
  }

  @Nested
  @DisplayName("Handle Verification Tests")
  inner class HandleVerificationTests {

    private val userAccountId = BigInteger.ONE
    private val providerExternalUserId = BigInteger.ONE
    private val newEmailIdentifier = UserIdentifier("new@email.com", UserIdentifierType.EMAIL)

    @Test
    fun handleVerificationLinkSuccessfully(): Unit = runBlocking {
      val previousUserDetail = UserDetail("+11234567890", UserDetailType.PHONE_NUMBER, 1)
      val newEmailDetail = UserDetail("new@email.com", UserDetailType.EMAIL, 2)

      val previousUserDetailList = listOf(
        ProviderExternalUserDetail(
          providerExternalUserId,
          userAccountId,
          previousUserDetail.value,
          previousUserDetail.type,
          previousUserDetail.priority,
          BigInteger.ZERO,
          BigInteger.ZERO,
          BigInteger.ZERO
        )
      )
      val userData = listOf(
        UserData(
          BigInteger.ONE,
          publicKeyHex,
          "i7598023454hfnap",
          KeyPairType.SR25519,
          "0832htfq43huwapfh4ofi",
          KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
          BigInteger.ONE,
          "someUserId",
          BigInteger.ONE,
          BigInteger.ONE,
          previousUserDetail.value,
          previousUserDetail.type,
          previousUserDetail.priority
        ),
        UserData(
          BigInteger.ONE,
          publicKeyHex,
          "i7598023454hfnap",
          KeyPairType.SR25519,
          "0832htfq43huwapfh4ofi",
          KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
          BigInteger.ONE,
          "someUserId",
          BigInteger.ONE,
          BigInteger.ONE,
          newEmailDetail.value,
          newEmailDetail.type,
          newEmailDetail.priority
        )
      )
      val existingUserKeyData = UserKeyData(
        BigInteger.ONE,
        publicKeyHex,
        "i7598023454hfnap",
        KeyPairType.SR25519,
        "0832htfq43huwapfh4ofi",
        KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
        KeyUsageType.ACCOUNT,
        null,
        BigInteger.ZERO,
        BigInteger.ZERO
      )
      val providerExternalUserDetail = ProviderExternalUserDetail(
        providerExternalUserId,
        userAccountId,
        newEmailDetail.value,
        newEmailDetail.type,
        newEmailDetail.priority,
        BigInteger.ZERO,
        BigInteger.ZERO,
        BigInteger.ZERO
      )
      val websiteSession = WebsiteSession(
        "1",
        null,
        UserIdentifier(previousUserDetail.value, UserIdentifierType.PHONE_NUMBER),
        listOf(BigInteger.ONE),
        authenticationCode,
        BigInteger.ONE,
        providerExternalUserId,
        userAccountId,
        verificationCode,
        sessionId,
        newEmailIdentifier,
        BigInteger.ONE,
        publicKeyHex
      )
      whenever(
        mockLookupService.findWebsiteSessionBySessionIdAndVerificationCode(
          sessionId,
          verificationCode
        )
      ).thenReturn(websiteSession)
      whenever(mockLookupService.getMsaIdByPublicKeyHex(publicKeyHex)).thenReturn(BigInteger.ONE)
      whenever(mockPasswordService.checkPasswordExistsByUserAccountId(BigInteger.ONE)).thenReturn(true)
      whenever(
        mockDatabaseService.findOneProviderExternalUserDetailByUserDetailValueAndUserDetailType(
          previousUserDetail.value,
          previousUserDetail.type
        )
      ).thenReturn(previousUserDetailList[0])
      whenever(
        mockLookupService.findAccountUserKeyDataByProviderMsaIdAndUserDetail(
          BigInteger.ONE,
          UserDetail(previousUserDetail.value, previousUserDetail.type, previousUserDetail.priority)
        )
      ).thenReturn(existingUserKeyData)
      whenever(mockLookupService.findUserDetailsFromUserAccountId(userAccountId)).thenReturn(listOf(previousUserDetail, newEmailDetail))
      val expectedProviderUserInfoList = orchestration.mapUserDataToProviderUserInfo(userData)
      Assertions.assertThat(expectedProviderUserInfoList.size).isEqualTo(1)
      Assertions.assertThat(expectedProviderUserInfoList[0].providerExternalUserDetailList.size).isEqualTo(2)
      whenever(
        mockDatabaseService.findUserDetailsByProviderMsaIdAndKeyPairTypeAndPublicKeyHexAndKeyUsageType(
          BigInteger.ONE,
          KeyPairType.SR25519,
          publicKeyHex,
          accountKeyUsageType
        )
      ).thenReturn(previousUserDetailList)
      whenever(
        mockDatabaseService.saveUserIdentifierAndProviderExternalUserDetail(
          providerExternalUserId,
          userAccountId,
          newEmailDetail
        )
      ).thenReturn(providerExternalUserDetail)
      whenever(mockDatabaseService.findUserDataByUserAccountIds(listOf(userAccountId))).thenReturn(userData)
      val accountUserDetails = listOf(previousUserDetail, newEmailDetail)
      val accountInfo = AccountInfo(accountUserDetails, expectedProviderUserInfoList, false)
      val successfulVerification = AddIdentifierVerificationResponse(true, null, accountInfo)

      val retVal = orchestration.handleAddNewIdentifierVerification(sessionId, verificationCode)
      Assertions.assertThat(retVal).isEqualTo(successfulVerification)
    }

    @Test
    fun handleVerificationLinkFailNoUserAccountIdFound(): Unit = runBlocking {
      val userAccountId = null
      val websiteSession = WebsiteSession(
        "1",
        null,
        emailIdentifier,
        listOf(BigInteger.ONE),
        authenticationCode,
        null,
        providerExternalUserId,
        userAccountId,
        verificationCode,
        sessionId,
        newEmailIdentifier,
        BigInteger.ONE,
        publicKeyHex
      )
      whenever(
        mockLookupService.findWebsiteSessionBySessionIdAndVerificationCode(
          sessionId,
          verificationCode
        )
      ).thenReturn(websiteSession)

      Assertions.assertThatThrownBy {
        runBlocking {
          orchestration.handleAddNewIdentifierVerification(sessionId, verificationCode)
        }
      }.isInstanceOf(ApiException::class.java)
        .hasMessage("No User Account ID found for current web session with session ID $sessionId for user ${emailIdentifier.value}")
        .extracting("apiError").isEqualTo(ApiError.NO_USER_ACCOUNT_ID_FOUND)
    }

    @Test
    fun handleVerificationLinkFailNoExistingUserIdentifierFound(): Unit = runBlocking {
      val websiteSession = WebsiteSession(
        "1",
        null,
        null,
        listOf(BigInteger.ONE),
        authenticationCode,
        null,
        userAccountId,
        providerExternalUserId,
        verificationCode,
        sessionId,
        null,
        BigInteger.ONE,
        publicKeyHex
      )
      whenever(
        mockLookupService.findWebsiteSessionBySessionIdAndVerificationCode(
          sessionId,
          verificationCode
        )
      ).thenReturn(websiteSession)

      Assertions.assertThatThrownBy {
        runBlocking {
          orchestration.handleAddNewIdentifierVerification(sessionId, verificationCode)
        }
      }.isInstanceOf(ApiException::class.java)
        .hasMessage("No existing User Identifier found for current web session with session ID $sessionId")
        .extracting("apiError").isEqualTo(ApiError.NO_USER_IDENTIFIER_FOUND)
    }

    @Test
    fun handleVerificationLinkFailNoNewUserIdentifierFound(): Unit = runBlocking {
      val existingEmailIdentifier = emailIdentifier
      val websiteSession = WebsiteSession(
        "1",
        null,
        existingEmailIdentifier,
        listOf(BigInteger.ONE),
        authenticationCode,
        null,
        userAccountId,
        providerExternalUserId,
        verificationCode,
        sessionId,
        null,
        BigInteger.ONE,
        publicKeyHex
      )
      whenever(
        mockLookupService.findWebsiteSessionBySessionIdAndVerificationCode(
          sessionId,
          verificationCode
        )
      ).thenReturn(websiteSession)

      Assertions.assertThatThrownBy {
        runBlocking {
          orchestration.handleAddNewIdentifierVerification(sessionId, verificationCode)
        }
      }.isInstanceOf(ApiException::class.java)
        .hasMessage("No User Identifier found for current web session with session ID $sessionId for user ${existingEmailIdentifier.value}")
        .extracting("apiError").isEqualTo(ApiError.NO_USER_IDENTIFIER_FOUND)
    }
  }

  @Nested
  @DisplayName("Amplica Access Logout Tests")
  inner class AmplicaAccessLogoutTests {
    //We have no real tests for this endpoint
  }

  @Nested
  @DisplayName("Check Logged In State Tests")
  inner class CheckLoggedInStateTests {
    @Test
    fun checkLoggedInState(): Unit = runBlocking {
      val sessionId = "sessionId"
      val websiteSession =
        WebsiteSession("1", null, emailIdentifier, listOf(BigInteger.ONE), loggedIn = UserState.LOGGED_IN)
      whenever(mockLookupService.findWebsiteSessionBySessionId(sessionId)).thenReturn(websiteSession)

      val retVal = orchestration.checkLoggedInState(sessionId)
      Assertions.assertThat(retVal).isTrue()
    }
  }

  @Nested
  @DisplayName("Create Logged In Session Tests")
  inner class CreateLoggedInSessionTests {
    @Test
    fun createLoggedInSession(): Unit = runBlocking {
      val loginSessionId = "loginSessionId"
      val loggedInSessionId = "loggedInSessionId"
      val loginWebsiteSession = WebsiteSession(
        loginSessionId,
        null,
        emailIdentifier,
        listOf(BigInteger.ONE),
        authenticationCode,
        loggedIn = UserState.LOGGED_OUT
      )
      val newLoggedInWebsiteSession =
        WebsiteSession(null, null, emailIdentifier, listOf(BigInteger.ONE), null, loggedIn = UserState.LOGGED_IN)
      val foundLoggedInWebsiteSession = WebsiteSession(
        loggedInSessionId,
        null,
        emailIdentifier,
        listOf(BigInteger.ONE),
        null,
        loggedIn = UserState.LOGGED_IN
      )

      whenever(
        mockRedisClient.getAndDeleteWebsiteSessionByAuthenticationCode(
          loginSessionId,
          authenticationCode
        )
      ).thenReturn(loginWebsiteSession)
      whenever(mockRedisClient.saveWebsiteSession(newLoggedInWebsiteSession)).thenReturn(loggedInSessionId)
      whenever(mockLookupService.findWebsiteSessionBySessionId(loggedInSessionId)).thenReturn(
        foundLoggedInWebsiteSession
      )

      val newWebsiteSession = orchestration.createLoggedInSession(loginWebsiteSession)
      Assertions.assertThat(newWebsiteSession.id).isNotEqualTo(loginSessionId)
      Assertions.assertThat(newWebsiteSession.id).isEqualTo(loggedInSessionId)
    }
  }

  @Nested
  @DisplayName("Password Login Tests")
  inner class PasswordLoginTests {
    private val password = generateUniquePassword()
    private val passwordDirectLoginRequest = PasswordDirectLoginRequest(emailIdentifier.value, password,null)
    private val websiteSession = WebsiteSession(null, null, emailIdentifier, listOf(BigInteger.ONE), null, null, null, null, null, null, null, null, null, UserState.LOGGED_IN)

    @Test
    fun authenticateUserWithPasswordSuccessfully(): Unit = runBlocking {
      whenever(mockPasswordService.authenticateByContactMethod(
        passwordDirectLoginRequest.username, UserIdentifierType.EMAIL, passwordDirectLoginRequest.password))
        .thenReturn(true)
      whenever(mockLookupService.getExistingAccountIdFromContactMethod(emailIdentifier.value, emailIdentifier.type)).thenReturn(BigInteger.ONE)
      whenever(mockRedisClient.saveWebsiteSession(websiteSession)).thenReturn("1")

      val retVal = orchestration.authenticateUserWithPassword(passwordDirectLoginRequest, UserIdentifierType.EMAIL)
      Assertions.assertThat(retVal).isEqualTo("1")
    }

    @Test
    fun authenticateUserWithPasswordFails(): Unit = runBlocking {
      Assertions.assertThatThrownBy {
        runBlocking {
          whenever(mockPasswordService.authenticateByContactMethod(
            passwordDirectLoginRequest.username, UserIdentifierType.EMAIL, passwordDirectLoginRequest.password))
            .thenReturn(false)
          orchestration.authenticateUserWithPassword(passwordDirectLoginRequest, UserIdentifierType.EMAIL)
        }
      }.isInstanceOf(ApiException::class.java)
        .hasFieldOrPropertyWithValue("apiError", ApiError.INCORRECT_PASSWORD)
    }
  }

  @Test
  fun mapUserIdentifiersAsUserIdentifierResponse() {
    val userIdentifiersResponse = mapUserIdentifiersToUserIdentifiersResponse(userIdentifiers)
    val userIdentifiersResponse2 = mapUserIdentifiersToUserIdentifiersResponse(userIdentifiersNullPriority)
    for (i in userIdentifiersResponse.indices) {
      Assertions.assertThat(userIdentifiersResponse[i].value).isEqualTo(userIdentifiers[i].value)
      Assertions.assertThat(userIdentifiersResponse[i].type).isEqualTo(userIdentifiers[i].type)
    }
    for (i in userIdentifiersResponse2.indices) {
      Assertions.assertThat(userIdentifiersResponse2[i].value).isEqualTo(userIdentifiersNullPriority[i].value)
      Assertions.assertThat(userIdentifiersResponse2[i].type).isEqualTo(userIdentifiersNullPriority[i].type)
    }
  }

  @Test
  fun mapUserIdentifiersAsUserDetailsResponse() {
    val userDetailsResponse = mapUserIdentifiersToUserDetails(userIdentifiers)
    val userDetailsResponse2 = mapUserIdentifiersToUserDetails(userIdentifiersNullPriority)
    for (i in userDetailsResponse.indices) {
      Assertions.assertThat(userDetailsResponse[i].value).isEqualTo(userIdentifiers[i].value)
    }
    for (i in userDetailsResponse2.indices) {
      Assertions.assertThat(userDetailsResponse2[i].value).isEqualTo(userIdentifiersNullPriority[i].value)
      Assertions.assertThat(userDetailsResponse2[i].priority).isEqualTo(1)
    }
  }
}
