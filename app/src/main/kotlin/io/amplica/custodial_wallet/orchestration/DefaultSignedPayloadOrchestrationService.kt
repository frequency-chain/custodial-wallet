package io.amplica.custodial_wallet.orchestration

import io.amplica.custodial_wallet.client.notification.NotificationServiceClient
import io.amplica.custodial_wallet.client.notification.SendSmsRequest
import io.amplica.custodial_wallet.client.redis.CustodialWalletRedisClient
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.client.redis.generateUUID
import io.amplica.custodial_wallet.db.repository.UserDetail
import io.amplica.custodial_wallet.email.EmailService
import io.amplica.custodial_wallet.email.client.conf.AwsSesProperties
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.internationalization.MessageFactory
import io.amplica.custodial_wallet.orchestration.signing.SigningOrchestrationService
import io.amplica.custodial_wallet.util.fromHex
import io.amplica.custodial_wallet.util.toHex
import io.amplica.custodial_wallet.web.ContextLoggerHelper
import io.amplica.custodial_wallet.orchestration.payload.HandleRequest
import io.amplica.custodial_wallet.orchestration.payload.SignUpRequest
import io.amplica.custodial_wallet.util.key_creation.*
import io.amplica.frequency.serialization.FrequencySerializable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.*
import kotlin.reflect.KClass

data class DefaultSignedPayloadOrchestrationServiceProperties(
  val payloadBlockExpiration: Long,
  val otpExpiration: Duration,
  val hostName: String,
  val awsSesProperties: AwsSesProperties,
  val sms: SmsProperties
)

class DefaultSignedPayloadOrchestrationService(
  private val properties: DefaultSignedPayloadOrchestrationServiceProperties,
  private val signingOrchestrationService: SigningOrchestrationService,
  private val redisClient: CustodialWalletRedisClient,
  private val lookupService: LookupOrchestrationService,
  private val notificationServiceClient: NotificationServiceClient,
  private val messageFactory: MessageFactory,
  private val emailService: EmailService,
  ) : SignedPayloadOrchestrationService {
  private val typedPayloadToComponentWithContextMapperRegistry: Map<KClass<out Any>, TypedPayloadToComponentWithContextMapper<out Any>> = mapOf(
    AddProviderPayloadRequest::class to AddProviderPayloadToComponentWithContext(),
    HandlePayloadRequest::class to ClaimHandlePayloadToComponentWithContext(),
  )

  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(DefaultSignedPayloadOrchestrationService::class.java)

    val WRAPPED_SIGNATURE_PREFIX = "<Bytes>".toByteArray(StandardCharsets.UTF_8)
    val WRAPPED_SIGNATURE_SUFFIX = "</Bytes>".toByteArray(StandardCharsets.UTF_8)
  }

  override suspend fun persistBatchPayloadToSign(batchPayloadToSignRequest: BatchPayloadToSignRequest): String {
    //validate signature of incoming PayloadToSignRequest
    val publicKey = batchPayloadToSignRequest.publicKey
    batchPayloadToSignRequest.payloads.forEach { payloadToSign ->
      if(!verifyPayloadRequest(publicKey, payloadToSign)) {
        throw ApiException(ApiError.INVALID_SIGNATURE, "Signature on payload of type ${payloadToSign.type} was invalid.")
      }
    }

    //Validate the presented msaId matches
    val providerMsaId = lookupService.retrieveMsaId(publicKey.format,publicKey.encodedValue)
    lookupService.verifyWhitelistedProviderMsaId(providerMsaId)

    //Confirm the user account keys data exists
    lookupService.findAccountUserKeyDataByProviderMsaIdAndUserDetail(providerMsaId, UserDetail.fromUserIdentifier(batchPayloadToSignRequest.userIdentifier))


    //Persist this payload in redis
    //Return the sessionId of the persisted payload
    return redisClient.saveBatchPayloadToSignRequest(batchPayloadToSignRequest)
  }

  override suspend fun getPermissionsContextForBatchPayloadToSign(sessionId: String, locale: Locale): List<ComponentWithContext> {
    val batchPayloadToSign = redisClient.findBatchPayloadToSignRequestBySessionId(sessionId)
      ?: throw ApiException(ApiError.NO_BATCH_TO_SIGN_FOR_SESSION, "No Batch associated with sessionId: $sessionId")
    val componentWithContextList = mutableListOf<ComponentWithContext>()
    for(typedPayloadToSign in batchPayloadToSign.payloads) {
      val componentWithContext = getComponentWithContext(typedPayloadToSign)
      componentWithContextList.add(componentWithContext)
    }

    return componentWithContextList
  }

  private fun getComponentWithContext(typedPayloadRequestWithSignature: TypedPayloadRequestWithSignature<PayloadRequest>): ComponentWithContext {
    val typedPayload = typedPayloadRequestWithSignature.payload
    @Suppress("unchecked_cast")
    val mapper = typedPayloadToComponentWithContextMapperRegistry[typedPayload::class] as TypedPayloadToComponentWithContextMapper<Any>?
      ?: throw ApiException(ApiError.PAYLOAD_TYPE_NOT_SUPPORTED, "Payload type=${typedPayload::class} not supported")
    return mapper.mapToComponentWithContext(typedPayload)
  }

  override suspend fun sendAuthenticationCode(sessionId: String, locale: Locale): UserIdentifier {
    // Generate an authenticationCode
    val authenticationCode = generateToken()
    // Persist payload by authentication code and sessionId
    val batchPayloadToSignRequest = fetchBatchPayloadToSignRequestBySessionId(sessionId)
    val providerMsaId = lookupService.retrieveMsaId(batchPayloadToSignRequest.publicKey.format, batchPayloadToSignRequest.publicKey.encodedValue)
    val providerMetadata = lookupService.getProviderMetaDataOrThrow(providerMsaId)
    redisClient.saveBatchPayloadToSignRequestByAuthenticationCode(authenticationCode, batchPayloadToSignRequest)

    val userIdentifier = batchPayloadToSignRequest.userIdentifier
    if (userIdentifier.type == UserIdentifierType.EMAIL) {
      emailService.sendSigningAuthenticationCodeEmail(
        userIdentifier.value,
        authenticationCode,
        sessionId,
        locale,
        providerMetadata
      )
    } else {
      sendAuthenticationCodeSms(authenticationCode, userIdentifier, locale, providerMsaId)
    }

    return batchPayloadToSignRequest.userIdentifier
  }

  private suspend fun sendAuthenticationCodeSms(authenticationCode: String, userIdentifier: UserIdentifier, locale: Locale, providerMsaId: BigInteger) {
    val msaMetaData = lookupService.getProviderMetaData(providerMsaId)
    val smsMessage = messageFactory.createMessage(properties.sms.loginTemplateName, mapOf(Pair("token", authenticationCode)), msaMetaData?.shortcode, locale)
    val verifiedMillis = lookupService.getVerifiedMillisForPhone(userIdentifier)

    ContextLoggerHelper.withMdcContext { sessionId, xSessionId, xTraceId ->
      notificationServiceClient.sendSms(
        SendSmsRequest(
          userIdentifier.value,
          smsMessage,
          verifiedMillis,
          null,
        ),
        sessionId,
        xSessionId,
        xTraceId,
      )
    }
  }

  override suspend fun generateAuthorizationCode(sessionId: String, authenticationCode: String): AuthorizationCodeAndCallback {
    val payloadToSignRequest = lookupService.findBatchPayloadToSignRequestBySessionIdAndAuthenticationCode(sessionId, authenticationCode)
    val authorizationCode = generateUUID()
    redisClient.saveBatchPayloadToSignRequestByAuthorizationCode(authorizationCode, payloadToSignRequest)
    return AuthorizationCodeAndCallback(authorizationCode, payloadToSignRequest.callback)
  }

  override suspend fun retrieveBatchSignedPayload(sessionId: String, authorizationCode: String): BatchSignedPayloadResponse {

    //fetch payload by sessionId and authorizationCode
    val batchPayloadToSignRequest = lookupService.findBatchPayloadToSignRequestBySessionIdAndAuthorizationCode(sessionId, authorizationCode)

    //Grab the UserKeyData by userIdentifier and providerMsaId
    val publicKey = batchPayloadToSignRequest.publicKey
    val providerMsaId = lookupService.retrieveMsaId(publicKey.format,publicKey.encodedValue)
    val userKeyData = lookupService.findAccountUserKeyDataByProviderMsaIdAndUserDetail(providerMsaId, UserDetail.fromUserIdentifier(batchPayloadToSignRequest.userIdentifier))

    //call kms to decrypt the private key
    val publicKeyHex = apiNormalizeToHex(publicKey)
    val privateKey = lookupService.getDecryptedPrivateKey(userKeyData)
    val algo = KeyPairSignatureAlgorithm.fromAlgorithm(userKeyData.encryptedPrivateKeyType.type)
    val keyPair = Sr25519KeyPairBytes(fromHex(publicKeyHex), privateKey, algo)

    //sign the payload
    val signedPayloadResponses = batchPayloadToSignRequest.payloads.map { typedPayload ->
      val genericPayloadRequest = GenericPayloadRequest(typedPayload.payload)
      val payloadSignature = signGenericPayload(keyPair, genericPayloadRequest)
      TypedPayloadRequestWithSignature(payloadSignature, typedPayload.type, typedPayload.payload)
    }

    //return the SignedPayloadResponse
    return BatchSignedPayloadResponse(
      batchPayloadToSignRequest.externalUserId,
      batchPayloadToSignRequest.userIdentifier,
      signedPayloadResponses
    )
  }

  private suspend fun fetchBatchPayloadToSignRequestBySessionId(sessionId: String): BatchPayloadToSignRequest {
    return redisClient.findBatchPayloadToSignRequestBySessionId(sessionId) ?: throw ApiException(ApiError.NO_PAYLOAD_FOUND_ERROR, "No Payload was found for sessionId=$sessionId")
  }

  fun signGenericPayload(
    keyPairBytes: KeyPairBytes,
    genericPayloadRequest: GenericPayloadRequest<PayloadRequest>
  ): Signature {
    val frequencyPayload = payloadRequestToFrequencyPayload(genericPayloadRequest.payloadRequest)

    return signingOrchestrationService.signPayload(keyPairBytes, frequencyPayload, Encoding.HEX)
  }

  fun <T : PayloadRequest> verifyPayloadRequest(
    publicKeyDto: PublicKeyDto,
    typedPayloadRequestWithSignature: TypedPayloadRequestWithSignature<T>
  ): Boolean {
    val frequencyPayload = payloadRequestToFrequencyPayload(typedPayloadRequestWithSignature.payload)

    return signingOrchestrationService.verifySignedPayload(
      publicKeyDto,
      frequencyPayload,
      getUnwrappedSignature(typedPayloadRequestWithSignature.signature)
    )
  }

  private fun payloadRequestToFrequencyPayload(payload: PayloadRequest): FrequencySerializable<Any> {
    return when (payload) {
      is AddProviderPayloadRequest -> SignUpRequest(payload.msaId, payload.schemaIds, payload.url?.toString())
      is HandlePayloadRequest -> HandleRequest(payload.baseHandle)
      is LoginPayloadRequest -> throw UnsupportedOperationException("Unsupported payload type: ${payload.javaClass.name}")
    }
  }

  private fun getUnwrappedSignature(signature: Signature): Signature {
    val signatureBytes = signature.toSignatureBytes()
    val isWrapped = signatureBytes.size > 14 &&
            signatureBytes.copyOfRange(0, 7).contentEquals(WRAPPED_SIGNATURE_PREFIX) &&
            signatureBytes.copyOfRange(signatureBytes.size - 8, signatureBytes.size).contentEquals(WRAPPED_SIGNATURE_SUFFIX)

    val unwrappedBytes = if (isWrapped) {
      signatureBytes.copyOfRange(7, signatureBytes.size - 8)
    } else {
      signatureBytes
    }

    return Signature(
      signature.algo,
      Encoding.HEX,
      toHex(unwrappedBytes),
    )
  }

}
