package io.amplica.custodial_wallet.client.redis

import arrow.core.Either
import io.amplica.custodial_wallet.client.redis.dto.*
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 *These mappers will be used in the DefaultCustodialWalletRedisClient in order to go from inputs to outputs
 */
internal fun loginRequestToRedisLoginRequestMapper(
  loginRequest: LoginRequest,
  sessionId: String?,
  token: String?,
  timeToLive: Long
): RedisLoginRequest {
  return RedisLoginRequest(
    sessionId ?: generateUUID(),
    token,
    loginRequest.userPublicKey,
    loginRequest.externalUserId,
    loginRequest.userIdentifier,
    loginRequest.publicKey,
    loginRequest.signature,
    RedisLoginPayload(
      loginRequest.payload.nonce,
      loginRequest.payload.url?.toString()
    ),
    timeToLive
  )
}

internal fun redisLoginRequestToLoginRequestMapper(redisLoginRequest: RedisLoginRequest): LoginRequest {
  return LoginRequest(
    redisLoginRequest.userPublicKey,
    redisLoginRequest.externalUserId,
    redisLoginRequest.userIdentifier,
    redisLoginRequest.publicKey,
    redisLoginRequest.signature,
    LoginPayload(
      redisLoginRequest.payload.nonce,
      redisLoginRequest.payload.url?.let { URI(it) }
    ),
    redisLoginRequest.token
  )
}


internal fun signUpRequestToRedisSignUpRequestMapper(
  signUpRequest: SignUpRequest,
  sessionId: String?,
  token: String?,
  timeToLive: Long
): RedisSignUpRequest {
  return RedisSignUpRequest(
    sessionId ?: generateUUID(),
    token,
    signUpRequest.externalUserId,
    signUpRequest.userIdentifiers,
    signUpRequest.publicKey,
    signUpRequest.signature,
    RedisAddProviderPayloadRequest(
      signUpRequest.payload.msaId,
      signUpRequest.payload.schemaIds,
      signUpRequest.payload.url?.toString()
    ),
    RedisHandleRequest(
      signUpRequest.handle.signature,
      RedisHandlePayloadRequest(
        signUpRequest.handle.payload.baseHandle
      )
    ),
    timeToLive
  )
}

internal fun redisSignUpRequestToSignUpRequestMapper(redisSignUpRequest: RedisSignUpRequest): SignUpRequest {
  return SignUpRequest(
    redisSignUpRequest.externalUserId,
    redisSignUpRequest.userIdentifiers,
    redisSignUpRequest.publicKey,
    redisSignUpRequest.signature,
    AddProviderPayloadRequest(
      redisSignUpRequest.payload.msaId,
      redisSignUpRequest.payload.schemaIds,
      redisSignUpRequest.payload.url?.let { URI(it) },
    ),
    HandleRequest(
      redisSignUpRequest.handle.signature,
      HandlePayloadRequest(
        redisSignUpRequest.handle.payload.baseHandle
      )
    ),
    redisSignUpRequest.token
  )
}

internal fun sessionInfoToRedisSessionInfo(
  sessionInfo: SessionInfo,
  sessionId: String,
  timeToLive: Long
): RedisSessionInfo {
  return RedisSessionInfo(
    sessionId,
    sessionInfo.tosAgreement,
    sessionInfo.callbackUrl,
    timeToLive,
    sessionInfo.resendTimeInMillis,
    sessionInfo.resendCount,
    sessionInfo.incorrectTokenRetries
  )
}

internal fun redisSessionInfoToSessionInfo(redisSessionInfo: RedisSessionInfo): SessionInfo {
  return SessionInfo(
    redisSessionInfo.tosAgreement,
    redisSessionInfo.callbackUrl,
    redisSessionInfo.resendTimeInMillis,
    redisSessionInfo.resendCount,
    redisSessionInfo.incorrectTokenRetries
  )
}

internal fun websiteSessionToRedisWebsiteSession(websiteSession: WebsiteSession, timeToLive: Long): RedisWebsiteSession {
  return RedisWebsiteSession(
    websiteSession.id ?: generateUUID(),
    websiteSession.callbackUrl,
    websiteSession.userIdentifier,
    websiteSession.userAccountIds,
    websiteSession.authenticationCode,
    websiteSession.msaId,
    websiteSession.providerExternalUserId,
    websiteSession.userAccountId,
    websiteSession.verificationCode,
    websiteSession.sessionId,
    websiteSession.addIdentifier,
    websiteSession.providerMsaId,
    websiteSession.publicKeyHex,
    websiteSession.loggedIn,
    timeToLive,
    websiteSession.incorrectTokenRetries,
    websiteSession.authorizationCode,
  )
}

internal fun redisWebsiteSessionToWebsiteSession(redisWebsiteSession: RedisWebsiteSession): WebsiteSession {
  return WebsiteSession(
    redisWebsiteSession.id,
    redisWebsiteSession.callbackUrl,
    redisWebsiteSession.userIdentifier,
    redisWebsiteSession.userAccountIds,
    redisWebsiteSession.authenticationCode,
    redisWebsiteSession.msaId,
    redisWebsiteSession.externalProviderUserId,
    redisWebsiteSession.userAccountId,
    redisWebsiteSession.verificationCode,
    redisWebsiteSession.sessionId,
    redisWebsiteSession.addIdentifier,
    redisWebsiteSession.providerMsaId,
    redisWebsiteSession.publicKeyHex,
    redisWebsiteSession.loggedIn,
    redisWebsiteSession.incorrectTokenRetries,
    redisWebsiteSession.authorizationCode,
  )
}

internal fun identifierVerificationToRedisIdentifierVerification(identifierVerification: IdentifierVerification): RedisIdentifierVerification {
  return RedisIdentifierVerification(
    identifierVerification.currentCode,
    identifierVerification.lastSentAt.toEpochMilli(),
    identifierVerification.totalSendCount,
    identifierVerification.incorrectAttemptCount
  )
}

internal fun redisIdentifierVerificationToIdentifierVerification(redisIdentifierVerification: RedisIdentifierVerification): IdentifierVerification {
  return IdentifierVerification(
    redisIdentifierVerification.currentCode,
    Instant.ofEpochMilli(redisIdentifierVerification.lastSentAtMillis),
    redisIdentifierVerification.totalSendCount,
    redisIdentifierVerification.incorrectAttemptCount
  )
}

internal fun siwaSessionToRedisSiwaSession(siwaSession: SiwaSession): RedisSiwaSession {
  return when (siwaSession) {
    is UnauthenticatedSiwaSession -> RedisUnauthenticatedSiwaSession(
      siwaSession.id,
      siwaSession.siwaRequest,
      siwaSession.fullCallbackUrl,
      siwaSession.userKeyPairType,
      siwaSession.flowKind,
      siwaSession.userIdentifier,
      siwaSession.authentication?.let { identifierVerificationToRedisIdentifierVerification(it) },
      siwaSession.prefillUserHandle,
    )

    is AuthenticatedSiwaSession -> RedisAuthenticatedSiwaSession(
      siwaSession.id,
      siwaSession.siwaRequest,
      siwaSession.userIdentifier,
      siwaSession.fullCallbackUrl,
      siwaSession.userKeyPairType,
      siwaSession.flowKind,
      siwaSession.intent,
      siwaSession.userAccountId,
      siwaSession.userInput,
      siwaSession.authorizationCode,
      siwaSession.prefillUserHandle,
    )
  }
}

internal fun redisSiwaSessionToSiwaSession(redisSiwaSession: RedisSiwaSession): SiwaSession {
  return when (redisSiwaSession) {
    is RedisUnauthenticatedSiwaSession -> UnauthenticatedSiwaSession(
      redisSiwaSession.siwaRequest,
      redisSiwaSession.id,
      redisSiwaSession.fullCallbackUrl,
      redisSiwaSession.userKeyPairType,
      redisSiwaSession.flowKind,
      redisSiwaSession.userIdentifier,
      redisSiwaSession.authentication?.let { redisIdentifierVerificationToIdentifierVerification(it) },
      redisSiwaSession.prefillUserHandle,
    )

    is RedisAuthenticatedSiwaSession -> AuthenticatedSiwaSession(
      redisSiwaSession.siwaRequest,
      redisSiwaSession.id,
      redisSiwaSession.userIdentifier,
      redisSiwaSession.fullCallbackUrl,
      redisSiwaSession.userKeyPairType,
      redisSiwaSession.flowKind,
      redisSiwaSession.intent,
      redisSiwaSession.userAccountId,
      redisSiwaSession.userInput,
      redisSiwaSession.authorizationCode,
      redisSiwaSession.prefillUserHandle,
    )
  }
}

internal fun userActivityRecordToRedis(record: UserActivityRecord): RedisUserActivityRecord {
  return RedisUserActivityRecord(
    record.userAccountId.toString(),
    record.userAccountId,
    record.expiration.toSeconds(),
    record.handleLastChanged
  )
}

internal fun redisUserActivityRecordToDto(record: RedisUserActivityRecord): UserActivityRecord {
  return UserActivityRecord(
    record.userAccountId,
    Duration.ofSeconds(record.expiration),
    record.handleLastChanged
  )
}

internal fun <RESULT> redisAsyncSubmissionToDto(redisAsyncSubmission: RedisAsyncSubmission<RESULT>): AsyncSubmission<RESULT> {
  val result: Either<Int, RESULT>?
  if (redisAsyncSubmission.apiErrorCode != null) {
    result = Either.Left(redisAsyncSubmission.apiErrorCode)
  } else if (redisAsyncSubmission.result != null) {
    result = Either.Right(redisAsyncSubmission.result)
  } else {
    result = null //Not resolved yet
  }

  return AsyncSubmission(
    redisAsyncSubmission.id,
    redisAsyncSubmission.status,
    result,
  )
}

internal fun <RESULT> asyncSubmissionToRedis(
  asyncSubmission: AsyncSubmission<RESULT>,
  timeToLive: Long
): RedisAsyncSubmission<RESULT> {
  var apiErrorCode: Int? = null
  var result: RESULT? = null
  asyncSubmission.result
    ?.tap { result = it }
    ?.tapLeft { apiErrorCode = it }
  
  return RedisAsyncSubmission(
    asyncSubmission.id,
    asyncSubmission.status,
    apiErrorCode,
    result,
    timeToLive,
  )
}

fun generateUUID(): String {
  return UUID.randomUUID().toString()
}

object AddProviderPayloadRequestDtoToRedis : DtoToRedisObjectMapper<AddProviderPayloadRequest, RedisAddProviderPayloadRequest> {
  override fun mapToRedisObject(dto: AddProviderPayloadRequest): RedisAddProviderPayloadRequest {
    return RedisAddProviderPayloadRequest(dto.msaId, dto.schemaIds, dto.url.toString())
  }
}

object AddProviderPayloadRequestRedisToDto : RedisObjectToDtoMapper<AddProviderPayloadRequest, RedisAddProviderPayloadRequest> {
  override fun mapFromRedisObject(redisObject: RedisAddProviderPayloadRequest): AddProviderPayloadRequest {
    return AddProviderPayloadRequest(redisObject.msaId, redisObject.schemaIds, redisObject.url?.let { URI(it) })
  }
}

object HandlePayloadRequestDtoToRedis : DtoToRedisObjectMapper<HandlePayloadRequest, RedisHandlePayloadRequest> {
  override fun mapToRedisObject(dto: HandlePayloadRequest): RedisHandlePayloadRequest {
    return RedisHandlePayloadRequest(dto.baseHandle)
  }
}

object HandlePayloadRequestRedisToDto : RedisObjectToDtoMapper<HandlePayloadRequest, RedisHandlePayloadRequest> {
  override fun mapFromRedisObject(redisObject: RedisHandlePayloadRequest): HandlePayloadRequest {
    return HandlePayloadRequest(redisObject.baseHandle)
  }
}
