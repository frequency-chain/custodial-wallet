package io.amplica.custodial_wallet.orchestration

import com.strategyobject.substrateclient.crypto.ss58.SS58AddressFormat
import com.strategyobject.substrateclient.crypto.ss58.SS58Codec
import io.amplica.custodial_wallet.client.kms.KmsEncryptionAlgorithm
import io.amplica.custodial_wallet.client.notification.NotificationServiceClient
import io.amplica.custodial_wallet.client.notification.SendSmsRequest
import io.amplica.custodial_wallet.client.redis.CustodialWalletRedisClient
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.db.repository.KeyUsageType
import io.amplica.custodial_wallet.db.repository.UserDetail
import io.amplica.custodial_wallet.db.repository.UserKeyData
import io.amplica.custodial_wallet.email.EmailService
import io.amplica.custodial_wallet.email.client.conf.AwsSesProperties
import io.amplica.custodial_wallet.internationalization.MessageFactory
import io.amplica.custodial_wallet.orchestration.payload.HandleRequest
import io.amplica.custodial_wallet.orchestration.payload.SignUpRequest
import io.amplica.custodial_wallet.orchestration.signing.SigningOrchestrationService
import io.amplica.custodial_wallet.service.provider_metadata.ProviderMetadata
import io.amplica.custodial_wallet.util.fromHex
import io.amplica.custodial_wallet.util.key_creation.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.*
import java.math.BigInteger
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.*

class DefaultSignedPayloadOrchestrationServiceTest {
  private lateinit var mockSigningOrchestrationService: SigningOrchestrationService
  private lateinit var mockRedisClient: CustodialWalletRedisClient
  private lateinit var mockLookupService: LookupOrchestrationService
  private lateinit var mockNotificationServiceClient: NotificationServiceClient
  private lateinit var mockMessageFactory: MessageFactory
  private lateinit var mockEmailService: EmailService
  private lateinit var signedPayloadOrchestrationService: DefaultSignedPayloadOrchestrationService


  companion object {
    private val currentBlockNumber = 5L
    private val providerMsaId = BigInteger.ONE
    private val providerShortCode = "example-provider"
    private val providerDisplayName = "Example Provider"
    private val providerMetadata = ProviderMetadata(providerDisplayName, providerShortCode, emptyList(), emptyMap())

    private val sessionId = "A-Session-Id"
    private val externalUserId = "someUserId"
    private val userIdentifier = UserIdentifier("some@user.com", UserIdentifierType.EMAIL)
    private val phoneUserIdentifier = UserIdentifier("+3131234568", UserIdentifierType.PHONE_NUMBER)

    private val authenticationCode = "123456"
    private val authorizationCode = "dshgfrs-dfgdfg-dfgd-dfgdfgfdgdf"

    private val privateKey = "This privat key will be 32bytes!"
    private val privateKeyBytes = privateKey.toByteArray(StandardCharsets.US_ASCII)
    private val publicKey = "This public key will be 32bytes!"
    private val publicKeyBytes = publicKey.toByteArray(StandardCharsets.US_ASCII)
    private val publicKeyDto = PublicKeyDto(
      SS58Codec.encode(publicKeyBytes, SS58AddressFormat.SUBSTRATE_ACCOUNT),
      Encoding.BASE_58,
      PublicKeyFormat.SS58,
      KeyPairType.SR25519
    )

    private val userKeyData = UserKeyData(
      BigInteger.ONE,
      "dfgfd",
      "dfgfdg",
      KeyPairType.SR25519,
      "dfsdsf",
      KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
      KeyUsageType.ACCOUNT,
      null,
      BigInteger.ONE,
      BigInteger.ONE
    )

    private lateinit var keyPair: Sr25519KeyPairBytes
    private val genericSignature = Signature(SignatureKeyPairType.SR25519, Encoding.HEX, "0x3259258c")


    private val callback = "https://amplicaaccess.com"

    private val addProviderSignature = Signature(SignatureKeyPairType.SR25519, Encoding.HEX, "0x3259258c")
    private val addProviderType = PayloadType.ADD_PROVIDER
    private val addProviderPayload = AddProviderPayloadRequest(providerMsaId, listOf(1, 2), null)
    private val typedAddProviderPayload = TypedPayloadRequestWithSignature(
      addProviderSignature,
      addProviderType,
      addProviderPayload,
    )

    private val claimHandleSignature = Signature(SignatureKeyPairType.SR25519, Encoding.HEX, "0x3259258c")
    private val claimHandleType = PayloadType.CLAIM_HANDLE
    private val claimHandlePayload = HandlePayloadRequest("sampleHandle")
    private val typedClaimHandlePayload = TypedPayloadRequestWithSignature(
      claimHandleSignature,
      claimHandleType,
      claimHandlePayload,
    )

    private val batchPayloadToSignRequest = BatchPayloadToSignRequest(
      null,
      externalUserId,
      userIdentifier,
      publicKeyDto,
      callback,
      listOf(typedAddProviderPayload, typedClaimHandlePayload),
      null,
      null,
    )

    private val batchPayloadToSignRequestSms = BatchPayloadToSignRequest(
      null,
      externalUserId,
      phoneUserIdentifier,
      publicKeyDto,
      callback,
      listOf(typedAddProviderPayload, typedClaimHandlePayload),
      null,
      null,
    )

    val otpExpiration: Duration = Duration.ofMinutes(20)
    const val hostName = "http://localhost:8080"

    const val awsSesSourceName = "Teddy Willard"
    const val awsSesSourceEmail = "teddywillard@gmail.com"
    const val awsSesLoginTemplateName = "FrequencyAccessLoginTemplate"
    const val awsSesSignupTemplateName = "FrequencyAccessSignupTemplate"
    const val awsSesDirectLoginTemplateName = "FrequencyAccessDirectLogin"
    const val smsSourceNumber = "+15128675309"
    const val awsSesAddIdentifierTemplateName = "addIdentifier"
    const val awsSesTokensReceivedTemplateName = "CommunityRewardsTokensReceived"
    const val smsDirectLoginTemplateName = "frequencyAccessDirectLogin"
    const val smsAddIdentifieTemplateName = "addIdentifie"
    const val smsWebviewSignUpTemplateName = "frequencyAccessWebviewSignUp"
    const val smsWebviewLoginTemplateName = "frequencyAccessWebviewLogin"
    const val smsMessageBody = "Your Frequency Access login code is: 123456"

  }


  @BeforeEach
  fun setUp() {
    mockSigningOrchestrationService = mock()
    mockRedisClient = mock()
    mockLookupService = mock()
    mockNotificationServiceClient = mock()
    mockMessageFactory = mock()
    mockEmailService = mock()
    signedPayloadOrchestrationService = DefaultSignedPayloadOrchestrationService(
      DefaultSignedPayloadOrchestrationServiceProperties(
        6,
        otpExpiration,
        hostName,
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
        )
      ),
      mockSigningOrchestrationService,
      mockRedisClient,
      mockLookupService,
      mockNotificationServiceClient,
      mockMessageFactory,
      mockEmailService
    )

    val algo = KeyPairSignatureAlgorithm.fromAlgorithm(userKeyData.encryptedPrivateKeyType.type)
    val publicKeyHex = apiNormalizeToHex(publicKeyDto)
    keyPair = Sr25519KeyPairBytes(fromHex(publicKeyHex), privateKeyBytes, algo)

    whenever(
      mockMessageFactory.createMessage(
        any(),
        any(),
        eq(null),
        any()
      )
    ).thenReturn(smsMessageBody)

    runBlocking {
      whenever(
        mockLookupService.retrieveMsaId(
          batchPayloadToSignRequest.publicKey.format,
          batchPayloadToSignRequest.publicKey.encodedValue
        )
      ).thenReturn(
        providerMsaId
      )
    }
  }

  @Test
  fun persistBatchPayloadToSign(): Unit = runBlocking {
    //GIVEN
    whenever(mockSigningOrchestrationService.verifySignedPayload(eq(publicKeyDto), isA<SignUpRequest>(), eq(addProviderSignature))).thenReturn(true)
    whenever(mockSigningOrchestrationService.verifySignedPayload(eq(publicKeyDto), isA<HandleRequest>(), eq(claimHandleSignature))).thenReturn(true)
    whenever(mockLookupService.retrieveMsaId(publicKeyDto.format, publicKeyDto.encodedValue)).thenReturn(providerMsaId)
    whenever(
      mockLookupService.findAccountUserKeyDataByProviderMsaIdAndUserDetail(
        providerMsaId,
        UserDetail.fromUserIdentifier(batchPayloadToSignRequest.userIdentifier)
      )
    )
      .thenReturn(userKeyData)
    whenever(mockRedisClient.saveBatchPayloadToSignRequest(batchPayloadToSignRequest)).thenReturn(sessionId)

    //WHEN
    val returnedSessionId = signedPayloadOrchestrationService.persistBatchPayloadToSign(batchPayloadToSignRequest)

    //THEN
    Assertions.assertThat(sessionId).isEqualTo(returnedSessionId)
  }

  @Test
  fun sendAuthenticationCodeEmail(): Unit = runBlocking {
    // GIVEN
    whenever(mockRedisClient.findBatchPayloadToSignRequestBySessionId(sessionId)).thenReturn(batchPayloadToSignRequest)
    whenever(
      mockRedisClient.saveBatchPayloadToSignRequestByAuthenticationCode(
        any(),
        eq(batchPayloadToSignRequest)
      )
    ).thenReturn("1")
    whenever(mockLookupService.retrieveMsaId(publicKeyDto.format, publicKeyDto.encodedValue)).thenReturn(providerMsaId)
    whenever(mockLookupService.getProviderMetaDataOrThrow(providerMsaId)).thenReturn(providerMetadata)

    // WHEN
    val returnedUserIdentifier = signedPayloadOrchestrationService.sendAuthenticationCode(sessionId, Locale.US)

    // THEN
    Mockito.verify(mockEmailService).sendSigningAuthenticationCodeEmail(
      same(userIdentifier.value),
      any(),
      same(sessionId),
      same(Locale.US),
      any()
    )
    Assertions.assertThat(returnedUserIdentifier).isEqualTo(userIdentifier)
  }

  @Test
  fun sendAuthenticationCodeSms(): Unit = runBlocking {
    whenever(mockRedisClient.findBatchPayloadToSignRequestBySessionId(sessionId)).thenReturn(
      batchPayloadToSignRequestSms
    )
    whenever(
      mockRedisClient.saveBatchPayloadToSignRequestByAuthenticationCode(
        any(),
        eq(batchPayloadToSignRequestSms)
      )
    ).thenReturn("1")
    val retVal = signedPayloadOrchestrationService.sendAuthenticationCode(sessionId, Locale.US)
    Mockito.verify(mockNotificationServiceClient, Mockito.times(1))
      .sendSms(
        SendSmsRequest(phoneUserIdentifier.value, smsMessageBody, null, null),
        null,
        null,
        null,
      )
    Assertions.assertThat(retVal).isEqualTo(phoneUserIdentifier)
  }

  @Test
  fun generateAuthorizationCode(): Unit = runBlocking {
    //GIVEN
    whenever(
      mockLookupService.findBatchPayloadToSignRequestBySessionIdAndAuthenticationCode(
        sessionId,
        authenticationCode
      )
    ).thenReturn(
      batchPayloadToSignRequest
    )

    //WHEN
    val authorizationCodeAndCallback =
      signedPayloadOrchestrationService.generateAuthorizationCode(sessionId, authenticationCode)

    //THEN
    Assertions.assertThat(authorizationCodeAndCallback.authorizationCode).isNotNull()
    Assertions.assertThat(batchPayloadToSignRequest.callback).isEqualTo(authorizationCodeAndCallback.callback)
  }

  @Test
  fun retrieveBatchSignedPayload(): Unit = runBlocking {
    //GIVEN
    whenever(
      mockLookupService.findBatchPayloadToSignRequestBySessionIdAndAuthorizationCode(
        sessionId,
        authorizationCode
      )
    )
      .thenReturn(batchPayloadToSignRequest)
    whenever(mockLookupService.retrieveMsaId(publicKeyDto.format, publicKeyDto.encodedValue)).thenReturn(providerMsaId)
    whenever(
      mockLookupService.findAccountUserKeyDataByProviderMsaIdAndUserDetail(
        providerMsaId,
        UserDetail.fromUserIdentifier(batchPayloadToSignRequest.userIdentifier)
      )
    )
      .thenReturn(userKeyData)
    whenever(mockLookupService.getDecryptedPrivateKey(userKeyData)).thenReturn(privateKeyBytes)
    whenever(mockLookupService.retrieveCurrentBlockNumber()).thenReturn(currentBlockNumber)

    whenever(
      mockSigningOrchestrationService.signPayload(
        eq(keyPair),
        any(),
        eq(Encoding.HEX)
      )
    ).thenReturn(
      genericSignature
    )

    //WHEN
    val signedPayloadResponse =
      signedPayloadOrchestrationService.retrieveBatchSignedPayload(sessionId, authorizationCode)

    //THEN
    Assertions.assertThat(signedPayloadResponse).isNotNull()
    Assertions.assertThat(signedPayloadResponse.externalUserId).isEqualTo(batchPayloadToSignRequest.externalUserId)
    Assertions.assertThat(signedPayloadResponse.payloads[0].payload)
      .isEqualTo(batchPayloadToSignRequest.payloads[0].payload)
    Assertions.assertThat(signedPayloadResponse.payloads[1].payload)
      .isEqualTo(batchPayloadToSignRequest.payloads[1].payload)
  }

  @Test
  fun signGenericPayloadTestAddProvider() {
    // GIVEN
    val payloadToSign = AddProviderPayloadRequest(1.toBigInteger(), listOf(5, 6, 7, 8), URI.create("google.com"))
    val sr25519KeyPair = Sr25519KeyPairCreator.createKeyPair()

    val expectedSignature = Signature(
      SignatureKeyPairType.SR25519,
      Encoding.HEX,
      "0xdcd5602189658ae9a1e9a72ab6c887937fba047e23bb81d5c321507e233ce65f19f01cdb7cefc62bf1ac7503a0f86d4b91511e930780297e69660ac6065eed8a",
    )

    whenever(
      mockSigningOrchestrationService.signPayload(
        eq(sr25519KeyPair),
        eq(SignUpRequest(1.toBigInteger(), listOf(5, 6, 7, 8), "google.com")),
        eq(Encoding.HEX)
      )
    ).thenReturn(expectedSignature)

    // WHEN
    val signature = signedPayloadOrchestrationService.signGenericPayload(
      sr25519KeyPair,
      GenericPayloadRequest(payloadToSign)
    )

    // THEN
    Assertions.assertThat(signature).isEqualTo(expectedSignature)
  }

  @Test
  fun signGenericPayloadTestHandle() {
    // GIVEN
    val payloadToSign = HandlePayloadRequest("sampleHandle")
    val sr25519KeyPair = Sr25519KeyPairCreator.createKeyPair()

    val expectedSignature = Signature(
      SignatureKeyPairType.SR25519,
      Encoding.HEX,
      "0xdcd5602189658ae9a1e9a72ab6c887937fba047e23bb81d5c321507e233ce65f19f01cdb7cefc62bf1ac7503a0f86d4b91511e930780297e69660ac6065eed8a",
    )

    whenever(
      mockSigningOrchestrationService.signPayload(
        eq(sr25519KeyPair),
        eq(HandleRequest("sampleHandle")),
        eq(Encoding.HEX)
      )
    ).thenReturn(expectedSignature)

    // WHEN
    val signature = signedPayloadOrchestrationService.signGenericPayload(
      sr25519KeyPair,
      GenericPayloadRequest(payloadToSign)
    )

    // THEN
    Assertions.assertThat(signature).isEqualTo(expectedSignature)
  }

  @Test
  fun verifyPayloadRequestSuccess() {
    // GIVEN
    val publicKey = PublicKeyDto(
      "0xb847ae7a839df51bceb1906cfe2cf3009282f01cc653a267b5dc43f49659c407",
      Encoding.HEX,
      PublicKeyFormat.BARE,
      KeyPairType.SR25519,
    )
    val signature = Signature(
      SignatureKeyPairType.SR25519,
      Encoding.HEX,
      "0xdcd5602189658ae9a1e9a72ab6c887937fba047e23bb81d5c321507e233ce65f19f01cdb7cefc62bf1ac7503a0f86d4b91511e930780297e69660ac6065eed8a",
    )
    val typedAddProviderPayload = TypedPayloadRequestWithSignature(
      signature,
      PayloadType.ADD_PROVIDER,
      AddProviderPayloadRequest(BigInteger.ONE, listOf(1, 2), null),
    )

    whenever(
      mockSigningOrchestrationService.verifySignedPayload(
        eq(publicKey),
        eq(SignUpRequest(BigInteger.ONE, listOf(1, 2), null)),
        eq(signature)
      ),
    ).thenReturn(true)


    // WHEN
    val verifyResult = signedPayloadOrchestrationService.verifyPayloadRequest(publicKey, typedAddProviderPayload)

    // THEN
    Assertions.assertThat(verifyResult).isTrue()
  }

}
