package io.amplica.custodial_wallet.client.redis

import arrow.core.Either
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.exception.RedisClientError
import io.amplica.custodial_wallet.exception.RedisClientException
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.redis.core.ReactiveRedisOperations
import java.math.BigInteger
import java.time.Duration
import kotlin.reflect.KClass


private class NamespacedRedisId<T : RedisValue>(expiringRedisValueClass: KClass<out T>, id: String) {
  val id = "${expiringRedisValueClass.simpleName ?: expiringRedisValueClass.toString()}_${id}"

  constructor(expiringRedisValue: T) : this(expiringRedisValue::class, expiringRedisValue.id)
}

class ReactiveCustodialWalletRedisClient(
  reactiveRedisOperations: ReactiveRedisOperations<String, Any>,
  override val timeToLive: Duration
) : CustodialWalletRedisClient {
  companion object {
    const val AUTHORIZATION_CODE_TO_SESSION_LOOKUP_PREFIX = "io.amplica.custodial_wallet.client.redis.authorizationCode"
    const val AUTHORIZATION_CODE_DELIMITER = "_"
  }

  private val opsForValue = reactiveRedisOperations.opsForValue()

  private val dtoToRedisObjectMapperRegistry = mutableMapOf<KClass<out Any>, DtoToRedisObjectMapper<out Any, Any>>()
  private val redisObjectToDtoMapperRegistry = mutableMapOf<KClass<out Any>, RedisObjectToDtoMapper<Any, out Any>>()

  init {
    dtoToRedisObjectMapperRegistry.putAll(
      listOf(
        Pair(AddProviderPayloadRequest::class, AddProviderPayloadRequestDtoToRedis),
        Pair(HandlePayloadRequest::class, HandlePayloadRequestDtoToRedis),
        Pair(
          BatchPayloadToSignRequest::class,
          BatchPayloadToSignDtoToRedis(dtoToRedisObjectMapperRegistry, timeToLive.seconds)
        ),
        Pair(TypedPayloadRequestWithSignature::class, TypedPayloadRequestWithSignatureDtoToRedis(dtoToRedisObjectMapperRegistry)),
      )
    )
    redisObjectToDtoMapperRegistry.putAll(
      listOf(
        Pair(RedisAddProviderPayloadRequest::class, AddProviderPayloadRequestRedisToDto),
        Pair(RedisHandlePayloadRequest::class, HandlePayloadRequestRedisToDto),
        Pair(RedisBatchPayloadToSignRequest::class, BatchPayloadToSignRedisToDto(redisObjectToDtoMapperRegistry)),
        Pair(RedisTypedPayloadWithSignature::class, TypedPayloadRequestWithSignatureRedisToDto(redisObjectToDtoMapperRegistry))
      )
    )
  }

  /** Helper method that uses the `timeToLive` from the instance and automatically generates a NamespacedRedisId  */
  private suspend fun <T : ExpiringRedisValue> saveRedisObject(
    redisObject: T,
  ) {
    saveRedisObject(redisObject, Duration.ofSeconds(redisObject.expiration))
  }

  /** Helper method that automatically generates a NamespacedRedisId based on the `redisObject`'s (instance) class */
  private suspend fun <T : RedisValue> saveRedisObject(
    redisObject: T,
    timeToLive: Duration,
  ) {
    saveRedisObject(NamespacedRedisId(redisObject), redisObject, timeToLive)
  }

  private suspend fun <T : RedisValue> saveRedisObject(
    namespacedRedisId: NamespacedRedisId<T>,
    redisObject: T,
    timeToLive: Duration?,
  ) {
    if(timeToLive == null) {
      opsForValue.set(namespacedRedisId.id, redisObject).awaitSingle()
    } else {
      opsForValue.set(namespacedRedisId.id, redisObject, timeToLive).awaitSingle()
    }
  }

  private suspend fun <T : RedisValue> findByNamespacedRedisId(
    namespacedRedisId: NamespacedRedisId<T>,
  ): T? {
    @Suppress("unchecked_cast")
    return opsForValue.get(namespacedRedisId.id).awaitSingleOrNull() as T?
  }

  private suspend fun <T : RedisValue> deleteByNamespacedRedisId(
    namespacedRedisId: NamespacedRedisId<T>,
  ): Boolean {
    return opsForValue.delete(namespacedRedisId.id).awaitSingle()
  }


  /** Generates a new sessionId and saves the login request data to that key */
  override suspend fun saveLoginRequest(loginRequest: LoginRequest): String {
    val redisLoginRequest = loginRequestToRedisLoginRequestMapper(loginRequest, null, null, timeToLive.seconds)
    saveRedisObject(redisLoginRequest)

    return redisLoginRequest.id
  }

  /** Saves a login request with specified token. If no sessionId is provided, a new random UUID is generated. */
  override suspend fun saveLoginRequestByToken(loginRequest: LoginRequest, token: String, sessionId: String?): String {
    val redisLoginRequest = loginRequestToRedisLoginRequestMapper(loginRequest, sessionId, token, timeToLive.seconds)
    saveRedisObject(redisLoginRequest)

    return redisLoginRequest.id
  }

  override suspend fun findLoginRequestByToken(sessionId: String, token: String): LoginRequest? {
    val loginRequest = findLoginRequestBySessionId(sessionId)
    return if (loginRequest?.token == token) { // Ensure the token matches
      loginRequest
    } else null
  }

  override suspend fun findLoginRequestBySessionId(sessionId: String): LoginRequest? {
    val loginRequest = findByNamespacedRedisId(NamespacedRedisId(RedisLoginRequest::class, sessionId))
    return if (loginRequest != null) {
      redisLoginRequestToLoginRequestMapper(loginRequest)
    } else null
  }

  override suspend fun replaceLoginRequestToken(sessionId: String, newToken: String) {
    val redisLoginRequest = findByNamespacedRedisId(NamespacedRedisId(RedisLoginRequest::class, sessionId))

    if (redisLoginRequest != null) {
      if (redisLoginRequest.token == null) {
        throw RedisClientException(RedisClientError.NO_LOGIN_TOKEN_FOUND, "No Token Found for SessionId=$sessionId")
      }
    } else {
      throw RedisClientException(RedisClientError.NO_LOGIN_REQUEST_FOUND, "No Request Found for SessionId=$sessionId")
    }

    val updatedRedisLoginRequest = redisLoginRequest.copy(token = newToken, expiration = timeToLive.seconds)
    saveRedisObject(updatedRedisLoginRequest)
  }

  /** Sets the token to null in redis (i.e., 'revokes' the token) */
  override suspend fun deleteLoginRequestByToken(sessionId: String, token: String) {
    val redisLoginRequest = findByNamespacedRedisId(NamespacedRedisId(RedisLoginRequest::class, sessionId))

    if (redisLoginRequest?.token == token) {
      val updatedRedisLoginRequest = redisLoginRequest.copy(token = null, expiration = timeToLive.seconds)

      saveRedisObject(updatedRedisLoginRequest)
    }
  }

  override suspend fun saveSignUpRequest(signUpRequest: SignUpRequest): String {
    val redisSignUpRequest = signUpRequestToRedisSignUpRequestMapper(signUpRequest, null, null, timeToLive.seconds)
    saveRedisObject(redisSignUpRequest)

    return redisSignUpRequest.id
  }

  override suspend fun saveSignUpRequestByToken(
    signUpRequest: SignUpRequest,
    token: String,
    sessionId: String?
  ): String {
    val redisSignUpRequest = signUpRequestToRedisSignUpRequestMapper(signUpRequest, sessionId, token, timeToLive.seconds)
    saveRedisObject(redisSignUpRequest)

    return redisSignUpRequest.id
  }

  override suspend fun findSignUpRequestByToken(sessionId: String, token: String): SignUpRequest? {
    val signUpRequest = findSignUpRequestBySessionId(sessionId)

    // Ensure the stored token matches the provided token
    return if (signUpRequest?.token == token) {
      signUpRequest
    } else null
  }

  override suspend fun findSignUpRequestBySessionId(sessionId: String): SignUpRequest? {
    val signUpRequest = findByNamespacedRedisId(NamespacedRedisId(RedisSignUpRequest::class, sessionId))
    return if (signUpRequest != null) {
      redisSignUpRequestToSignUpRequestMapper(signUpRequest)
    } else null
  }

  override suspend fun replaceSignUpRequestToken(sessionId: String, newToken: String) {
    val redisSignUpRequest = findByNamespacedRedisId(NamespacedRedisId(RedisSignUpRequest::class, sessionId))

    if (redisSignUpRequest != null) {
      if (redisSignUpRequest.token == null) {
        throw RedisClientException(RedisClientError.NO_SIGNUP_TOKEN_FOUND, "No Token Found for SessionId=$sessionId")
      }
    } else {
      throw RedisClientException(RedisClientError.NO_SIGNUP_REQUEST_FOUND, "No Request Found for SessionId=$sessionId")
    }

    val updatedRedisSignUpRequest = redisSignUpRequest.copy(token = newToken, expiration = timeToLive.seconds)
    saveRedisObject(updatedRedisSignUpRequest)
  }

  override suspend fun deleteSignUpRequestByToken(sessionId: String, token: String) {
    val redisSignUpRequest = findByNamespacedRedisId(NamespacedRedisId(RedisSignUpRequest::class, sessionId))

    if (redisSignUpRequest?.token == token) {
      val updatedRedisSignUpRequest = redisSignUpRequest.copy(token = null, expiration = timeToLive.seconds)

      saveRedisObject(updatedRedisSignUpRequest)
    }
  }

  override suspend fun saveSessionInfoBySessionId(sessionId: String, sessionInfo: SessionInfo) {
    val redisSessionInfo = sessionInfoToRedisSessionInfo(sessionInfo, sessionId, timeToLive.seconds)
    saveRedisObject(redisSessionInfo)
  }

  override suspend fun findSessionInfoBySessionId(sessionId: String): SessionInfo? {
    val sessionInfo = findByNamespacedRedisId(NamespacedRedisId(RedisSessionInfo::class, sessionId))
    return if (sessionInfo != null) {
      redisSessionInfoToSessionInfo(sessionInfo)
    } else null
  }

  override suspend fun saveWebsiteSession(websiteSession: WebsiteSession): String {
    val redisWebsiteSession = websiteSessionToRedisWebsiteSession(websiteSession, timeToLive.seconds)
    saveRedisObject(redisWebsiteSession)

    return redisWebsiteSession.id
  }

  override suspend fun saveWebsiteSessionByAuthenticationCode(
    authenticationCode: String,
    websiteSession: WebsiteSession
  ): String {
    return saveWebsiteSession(websiteSession.copy(authenticationCode = authenticationCode))
  }

  override suspend fun saveWebsiteSessionByAuthorizationCode(
    authorizationCode: String,
    websiteSession: WebsiteSession
  ): String {
    return saveWebsiteSession(websiteSession.copy(authorizationCode = authorizationCode))
  }

  override suspend fun saveWebsiteSessionByVerificationCode(
    verificationCode: String,
    websiteSession: WebsiteSession
  ): String {
    return saveWebsiteSession(websiteSession.copy(verificationCode = verificationCode))
  }

  override suspend fun findWebsiteSessionBySessionId(
    sessionId: String,
  ): WebsiteSession? {
    val redisWebsiteSession = findByNamespacedRedisId(NamespacedRedisId(RedisWebsiteSession::class, sessionId))
    return if (redisWebsiteSession != null) {
      redisWebsiteSessionToWebsiteSession(redisWebsiteSession)
    } else null
  }

  override suspend fun findWebsiteSessionBySessionIdAndAuthenticationCode(
    sessionId: String,
    authenticationCode: String
  ): WebsiteSession? {
    val websiteSession = findWebsiteSessionBySessionId(sessionId)

    return if (websiteSession?.authenticationCode == authenticationCode) {
      websiteSession
    } else null
  }

  override suspend fun findWebsiteSessionBySessionIdAndAuthorizationCode(
    sessionId: String,
    authorizationCode: String
  ): WebsiteSession? {
    val websiteSession = findWebsiteSessionBySessionId(sessionId)

    return if (websiteSession?.authorizationCode == authorizationCode) {
      websiteSession
    } else null
  }

  override suspend fun findWebsiteSessionBySessionIdAndVerificationCode(
    sessionId: String,
    verificationCode: String
  ): WebsiteSession? {
    val redisWebsiteSession = findWebsiteSessionBySessionId(sessionId)

    return if (redisWebsiteSession?.verificationCode == verificationCode) {
      redisWebsiteSession
    } else null
  }

  override suspend fun getAndDeleteWebsiteSessionByAuthenticationCode(
    sessionId: String,
    authenticationCode: String
  ): WebsiteSession? {
    val namespacedRedisId = NamespacedRedisId(RedisWebsiteSession::class, sessionId)
    val redisWebsiteSession = findByNamespacedRedisId(namespacedRedisId)

    return if (redisWebsiteSession != null && redisWebsiteSession.authenticationCode == authenticationCode) {
      deleteByNamespacedRedisId(namespacedRedisId)
      redisWebsiteSessionToWebsiteSession(redisWebsiteSession)
    } else null
  }

  override suspend fun deleteWebsiteSessionBySessionId(sessionId: String) {
    deleteByNamespacedRedisId(NamespacedRedisId(RedisWebsiteSession::class, sessionId))
  }

  override suspend fun <T : Any> savePayloadToSign(payloadToSign: PayloadToSignRequest<T>): String {
    @Suppress("unchecked_cast")
    val mapper = dtoToRedisObjectMapperRegistry[payloadToSign.payload::class] as DtoToRedisObjectMapper<T, *>?
      ?: throw ApiException(
        ApiError.PAYLOAD_TYPE_NOT_SUPPORTED,
        "Payload type not supported, UserIdentifier=${payloadToSign.userIdentifier.value}"
      )
    val redisObject = mapper.mapToRedisObject(payloadToSign.payload) ?: throw ApiException(
      ApiError.PAYLOAD_TYPE_NOT_SUPPORTED,
      "Payload type not supported, UserIdentifier=${payloadToSign.userIdentifier.value}\""
    )

    val redisPayloadToSignRequest = RedisPayloadToSignRequest(
      payloadToSign.id ?: generateUUID(),
      payloadToSign.externalUserId,
      payloadToSign.userIdentifier,
      payloadToSign.publicKey,
      payloadToSign.signature,
      payloadToSign.callback,
      redisObject,
      timeToLive.seconds,
      payloadToSign.authenticationCode,
      payloadToSign.authorizationCode,
    )

    saveRedisObject(redisPayloadToSignRequest)

    return redisPayloadToSignRequest.id
  }

  override suspend fun <T : Any> savePayloadToSignByAuthenticationCode(
    authenticationCode: String,
    payloadToSign: PayloadToSignRequest<T>
  ): String {
    return savePayloadToSign(payloadToSign.copy(authenticationCode = authenticationCode))
  }

  override suspend fun <T : Any> savePayloadToSignByAuthorizationCode(
    authorizationCode: String,
    payloadToSign: PayloadToSignRequest<T>
  ): String {
    return savePayloadToSign(payloadToSign.copy(authorizationCode = authorizationCode))
  }

  override suspend fun <T> findPayloadToSignBySessionId(sessionId: String): PayloadToSignRequest<T>? {
    val redisObject = findByNamespacedRedisId(NamespacedRedisId(RedisPayloadToSignRequest::class, sessionId))
    return if (redisObject != null) {
      @Suppress("unchecked_cast")
      val mapper = redisObjectToDtoMapperRegistry[redisObject.payload::class] as RedisObjectToDtoMapper<*, Any>?
        ?: throw ApiException(
          ApiError.PAYLOAD_TYPE_NOT_SUPPORTED,
          "Payload type not supported, sessionId=$sessionId and userIdentifier=${redisObject.userIdentifier}"
        )

      @Suppress("unchecked_cast")
      val dtoPayload = mapper.mapFromRedisObject(redisObject.payload) as T? ?: throw ApiException(
        ApiError.PAYLOAD_TYPE_NOT_SUPPORTED,
        "Payload type not supported, sessionId=$sessionId and userIdentifier=${redisObject.userIdentifier}"
      )
      PayloadToSignRequest(
        redisObject.id,
        redisObject.externalUserId,
        redisObject.userIdentifier,
        redisObject.publicKey,
        redisObject.signature,
        redisObject.callback,
        dtoPayload,
        redisObject.authenticationCode,
        redisObject.authorizationCode
      )
    } else null
  }

  override suspend fun <T> findPayloadToSignBySessionIdAndAuthorizationCode(
    sessionId: String,
    authorizationCode: String
  ): PayloadToSignRequest<T>? {
    val payloadToSignRequest = findPayloadToSignBySessionId<T>(sessionId)

    return if (payloadToSignRequest?.authorizationCode == authorizationCode) {
      payloadToSignRequest
    } else null
  }

  override suspend fun <T> findPayloadToSignBySessionIdAndAuthenticationCode(
    sessionId: String,
    authenticationCode: String
  ): PayloadToSignRequest<T>? {
    val payloadToSignRequest = findPayloadToSignBySessionId<T>(sessionId)

    return if (payloadToSignRequest?.authenticationCode == authenticationCode) {
      payloadToSignRequest
    } else null
  }

  override suspend fun deletePayloadToSignBySessionId(sessionId: String) {
    deleteByNamespacedRedisId(NamespacedRedisId(RedisPayloadToSignRequest::class, sessionId))
  }

  override suspend fun saveBatchPayloadToSignRequest(batchPayloadToSign: BatchPayloadToSignRequest): String {
    @Suppress("unchecked_cast")
    val mapper =
      dtoToRedisObjectMapperRegistry[BatchPayloadToSignRequest::class] as DtoToRedisObjectMapper<BatchPayloadToSignRequest, *>?
        ?: throw ApiException(ApiError.PAYLOAD_TYPE_NOT_SUPPORTED, "Payload type not supported")
    val redisBatchPayloadToSignRequest = mapper.mapToRedisObject(batchPayloadToSign) as RedisBatchPayloadToSignRequest
    saveRedisObject(redisBatchPayloadToSignRequest)
    return redisBatchPayloadToSignRequest.id
  }

  override suspend fun saveBatchPayloadToSignRequestByAuthenticationCode(
    authenticationCode: String,
    batchPayloadToSign: BatchPayloadToSignRequest
  ): String {
    return saveBatchPayloadToSignRequest(batchPayloadToSign.copy(authenticationCode = authenticationCode))
  }

  override suspend fun saveBatchPayloadToSignRequestByAuthorizationCode(
    authorizationCode: String,
    batchPayloadToSign: BatchPayloadToSignRequest
  ): String {
    return saveBatchPayloadToSignRequest(batchPayloadToSign.copy(authorizationCode = authorizationCode))
  }

  override suspend fun findBatchPayloadToSignRequestBySessionId(sessionId: String): BatchPayloadToSignRequest? {
    val redisBatchPayloadToSignRequest =
      findByNamespacedRedisId(NamespacedRedisId(RedisBatchPayloadToSignRequest::class, sessionId))
    return if (redisBatchPayloadToSignRequest != null) {
      @Suppress("unchecked_cast")
      val mapper =
        redisObjectToDtoMapperRegistry[RedisBatchPayloadToSignRequest::class] as RedisObjectToDtoMapper<*, Any>?
          ?: throw ApiException(
            ApiError.PAYLOAD_TYPE_NOT_SUPPORTED,
            "Payload type not supported, sessionId=$sessionId and userIdentifier=${redisBatchPayloadToSignRequest.userIdentifier}"
          )
      mapper.mapFromRedisObject(redisBatchPayloadToSignRequest) as BatchPayloadToSignRequest? ?: throw ApiException(
        ApiError.PAYLOAD_TYPE_NOT_SUPPORTED,
        "Payload type not supported, sessionId=$sessionId"
      )

    } else null
  }

  override suspend fun findBatchPayloadToSignRequestBySessionIdAndAuthenticationCode(
    sessionId: String,
    authenticationCode: String
  ): BatchPayloadToSignRequest? {
    val batchPayloadToSignRequest = findBatchPayloadToSignRequestBySessionId(sessionId)

    return if (batchPayloadToSignRequest?.authenticationCode == authenticationCode) {
      batchPayloadToSignRequest
    } else null
  }

  override suspend fun findBatchPayloadToSignRequestBySessionIdAndAuthorizationCode(
    sessionId: String,
    authorizationCode: String
  ): BatchPayloadToSignRequest? {
    val batchPayloadToSignRequest = findBatchPayloadToSignRequestBySessionId(sessionId)

    return if (batchPayloadToSignRequest?.authorizationCode == authorizationCode) {
      batchPayloadToSignRequest
    } else null
  }

  override suspend fun deleteBatchPayloadToSignRequestBySessionId(sessionId: String) {
    deleteByNamespacedRedisId(NamespacedRedisId(RedisBatchPayloadToSignRequest::class, sessionId))
  }

  override suspend fun saveSiwaSession(siwaSession: SiwaSession) {
    val redisBatchPayloadToSignRequest = siwaSessionToRedisSiwaSession(siwaSession)
    // We want to generalize across the different concrete implementations of `SiwaSession` but the `siwaSession`
    // instance will be one of the concrete variants at run-time, so we need to specify the interface.
    // Note that Jackson will discriminate based on the instance class for serialization/deserialization.
    val namespacedRedisId = NamespacedRedisId(RedisSiwaSession::class, redisBatchPayloadToSignRequest.id)

    saveRedisObject(namespacedRedisId, redisBatchPayloadToSignRequest, timeToLive)
  }

  override suspend fun findSiwaSessionBySessionId(sessionId: String): SiwaSession? {
    val maybeRedisSiwaSession = findByNamespacedRedisId(NamespacedRedisId(RedisSiwaSession::class, sessionId))

    return maybeRedisSiwaSession?.let { redisSiwaSession ->
      redisSiwaSessionToSiwaSession(redisSiwaSession)
    }
  }

  override suspend fun findSiwaSessionByAuthorizationCode(authorizationCode: String): SiwaSession? {
    val sessionId = this.findSiwaSessionIdByAuthorizationCode(authorizationCode) ?: return null

    return findSiwaSessionBySessionId(sessionId)
  }

  override suspend fun findSiwaSessionIdByAuthorizationCode(authorizationCode: String): String? {
    return opsForValue.get(
      arrayOf(AUTHORIZATION_CODE_TO_SESSION_LOOKUP_PREFIX, authorizationCode).joinToString(
        AUTHORIZATION_CODE_DELIMITER
      )
    ).awaitSingleOrNull() as String?
  }

  override suspend fun saveSiwaSessionIdByAuthorizationCode(sessionId: String, authorizationCode: String): Boolean {
    return opsForValue.set(
      arrayOf(AUTHORIZATION_CODE_TO_SESSION_LOOKUP_PREFIX, authorizationCode).joinToString(
        AUTHORIZATION_CODE_DELIMITER
      ), sessionId, timeToLive
    ).awaitSingle()
  }

  override suspend fun deleteSiwaSessionBySessionId(sessionId: String) {
    deleteByNamespacedRedisId(NamespacedRedisId(RedisSiwaSession::class, sessionId))
  }

  override suspend fun saveSesTemplates(sesTemplates: Set<SesTemplate>) {
    val redisSesTemplates = SesTemplateDtoToRedisMapper.mapToRedisObject(sesTemplates)
    //passing null for TTL because I want it to never expire, just be overwritten over time
    saveRedisObject(NamespacedRedisId(redisSesTemplates), redisSesTemplates, null)
  }

  override suspend fun findAllSesTemplates(): Set<SesTemplate> {
    val redisSesTemplates =
      findByNamespacedRedisId(NamespacedRedisId(RedisSesTemplates::class, RedisSesTemplates.DEFAULT_ID_VALUE))
    return if (redisSesTemplates != null) {
      SesTemplateRedisToDtoMapper.mapFromRedisObject(redisSesTemplates)
    } else {
      emptySet()
    }
  }

  override suspend fun saveProviderCount(providerCount: ProviderCount) {
    saveRedisObject(providerCount)
  }

  override suspend fun findProviderCount(key: String): ProviderCount? {
    val namespacedRedisId = NamespacedRedisId(ProviderCount::class, key)
    return findByNamespacedRedisId(namespacedRedisId)
  }

  override suspend fun saveMigrationTaskNoMsaCount(noMsaCount: Int) {
    val migrationTaskNoMsaCount = MigrationTaskNoMsaCount(noMsaCount)
    saveRedisObject(NamespacedRedisId(migrationTaskNoMsaCount), migrationTaskNoMsaCount, null)
  }

  override suspend fun findMigrationTaskNoMsaCount(): Int? {
    return findByNamespacedRedisId(NamespacedRedisId(MigrationTaskNoMsaCount::class, MigrationTaskNoMsaCount.DEFAULT_ID_VALUE))?.count
  }

  override suspend fun deleteMigrationTaskNoMsaCount() {
    deleteByNamespacedRedisId(NamespacedRedisId(MigrationTaskNoMsaCount::class, MigrationTaskNoMsaCount.DEFAULT_ID_VALUE))
  }

  override suspend fun saveUserActivityRecord(record: UserActivityRecord) {
    saveRedisObject(userActivityRecordToRedis(record))
  }

  override suspend fun findUserActivityRecord(userAccountId: BigInteger): UserActivityRecord? {
    return findByNamespacedRedisId(
      NamespacedRedisId(RedisUserActivityRecord::class, userAccountId.toString())
    )?.let { redisUserActivityRecordToDto(it) }
  }

  override suspend fun <RESULT> saveAsyncSubmission(asyncSubmission: AsyncSubmission<RESULT>): AsyncSubmission<RESULT> {
    val redisObject = asyncSubmissionToRedis(asyncSubmission, timeToLive.seconds)
    saveRedisObject(redisObject)
    return asyncSubmission.copy(id = redisObject.id)
  }

  override suspend fun <RESULT> findAsyncSubmission(id: String): AsyncSubmission<RESULT>? {
    @Suppress("unchecked_cast")
    return findByNamespacedRedisId(
      NamespacedRedisId(RedisAsyncSubmission::class, id)
    )?.let { redisAsyncSubmissionToDto(it as RedisAsyncSubmission<RESULT>) }
  }
}
