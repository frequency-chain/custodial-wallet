package io.amplica.custodial_wallet.client.redis

import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto
import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.TimeToLive
import org.springframework.data.redis.core.index.Indexed
import java.math.BigInteger
import java.time.Instant


// NOTE(Julian, 2024-07-30): Do not move or rename these classes! Changing their path or name in any way
// will break compatibility with existing values stored in Redis.

interface RedisValue {
  val id: String
}

interface ExpiringRedisValue: RedisValue {
  val expiration: Long
}

interface NonExpiringRedisValue: RedisValue

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
open class RedisAddProviderPayloadRequest(
  val msaId: BigInteger,
  val schemaIds: List<Int>,
  val url: String?,
)

open class RedisHandleRequest(
  val signature: Signature,
  val payload: RedisHandlePayloadRequest
)

open class RedisHandlePayloadRequest(
  val baseHandle: String
)

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
data class RedisSignUpRequest(
  @Id override val id: String,
  @Indexed val token: String?,
  val externalUserId: String,
  val userIdentifiers: List<UserIdentifier>,
  val publicKey: PublicKeyDto,
  val signature: Signature,
  val payload: RedisAddProviderPayloadRequest,
  val handle: RedisHandleRequest,
  @TimeToLive override var expiration: Long
) : ExpiringRedisValue


data class RedisLoginPayload(
  val nonce: String,
  val url: String?
)

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
data class RedisLoginRequest(
  @Id override val id: String,
  val token: String?,
  val userPublicKey: PublicKeyDto?,
  val externalUserId: String?,
  val userIdentifier: UserIdentifier,
  val publicKey: PublicKeyDto,
  val signature: Signature,
  val payload: RedisLoginPayload,
  override var expiration: Long
) : ExpiringRedisValue

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
data class RedisSessionInfo(
  @Id override val id: String,
  val tosAgreement: Boolean,
  val callbackUrl: String?,
  @TimeToLive override var expiration: Long,
  var resendTimeInMillis: Long,
  var resendCount: Int,
  var incorrectTokenRetries: Int
) : ExpiringRedisValue

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
data class RedisWebsiteSession(
  @Id override val id: String,
  val callbackUrl: String?,
  val userIdentifier: UserIdentifier?,
  val userAccountIds: List<BigInteger>?,
  val authenticationCode: String?,
  val msaId: BigInteger?,
  val externalProviderUserId: BigInteger?,
  val userAccountId: BigInteger?,
  val verificationCode: String?,
  val sessionId: String?,
  val addIdentifier: UserIdentifier?,
  val providerMsaId: BigInteger?,
  val publicKeyHex: String?,
  val loggedIn: UserState,
  @TimeToLive override var expiration: Long,
  val incorrectTokenRetries: Int,
  val authorizationCode: String?,
) : ExpiringRedisValue

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
data class RedisPayloadToSignRequest<T : Any>(
  @Id override val id: String,
  val externalUserId: String,
  val userIdentifier: UserIdentifier,
  val publicKey: PublicKeyDto,
  val signature: Signature,
  val callback: String,
  @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
  val payload: T,
  override var expiration: Long,
  val authenticationCode: String?,
  val authorizationCode: String?,
) : ExpiringRedisValue

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
data class RedisBatchPayloadToSignRequest(
  @Id override val id: String,
  val externalUserId: String,
  val userIdentifier: UserIdentifier,
  val publicKey: PublicKeyDto,
  val callback: String,
  val payloads: List<RedisTypedPayloadWithSignature<*>>,
  override var expiration: Long,
  val authenticationCode: String?,
  val authorizationCode: String?,
) : ExpiringRedisValue

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
data class ProviderCount(
  @Id override val id: String,
  val count: Int,
  override var expiration: Long,
) : ExpiringRedisValue

/**
 * This type applies to 'PayloadRequest's but the name has not been updated for compatibility reasons
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
open class RedisTypedPayloadWithSignature<T : Any>(
  val signature: Signature,
  val type: String,
  @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
  val payload: T
)

sealed interface RedisSiwaSession : RedisValue

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
data class RedisUnauthenticatedSiwaSession(
  override val id: String,
  val siwaRequest: SiwaRequest,
  val fullCallbackUrl: String,
  val userKeyPairType: KeyPairType = KeyPairType.SR25519,
  val flowKind: SiwaFlowKind = SiwaFlowKind.SOCIAL, // Backwards compatibility with existing sessions
  val userIdentifier: UserIdentifier?,
  val authentication: RedisIdentifierVerification?,
  val prefillUserHandle: String?,
) : RedisSiwaSession

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
data class RedisAuthenticatedSiwaSession(
  override val id: String,
  val siwaRequest: SiwaRequest,
  val userIdentifier: UserIdentifier,
  val fullCallbackUrl: String,
  val userKeyPairType: KeyPairType = KeyPairType.SR25519,
  val flowKind: SiwaFlowKind = SiwaFlowKind.SOCIAL, // Backwards compatibility with existing sessions
  val intent: SiwaIntent?,
  val userAccountId: BigInteger?,
  val userInput: SiwaPayloadsUserInput?,
  val authorizationCode: String?,
  val prefillUserHandle: String?,
) : RedisSiwaSession

data class RedisIdentifierVerification(
  val currentCode: String,
  val lastSentAtMillis: Long,
  val totalSendCount: Int,
  val incorrectAttemptCount: Int
)

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
data class RedisSesTemplates(
  val redisSesTemplates: List<RedisSesTemplate>,
  override val id: String = DEFAULT_ID_VALUE,
) : NonExpiringRedisValue {
  companion object {
    const val DEFAULT_ID_VALUE = "SES_TEMPLATES"
  }
}

data class RedisSesTemplate(val sesTemplateName: String)

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
data class MigrationTaskNoMsaCount(
  val count: Int,
  override val id: String = DEFAULT_ID_VALUE,
) : NonExpiringRedisValue {
  companion object{
    const val DEFAULT_ID_VALUE = "MIGRATION_TASK_NO_MSA_COUNT"
  }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
data class RedisUserActivityRecord(
  override val id: String,
  val userAccountId: BigInteger,
  override val expiration: Long,
  val handleLastChanged: Instant?,
) : ExpiringRedisValue

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
data class RedisAsyncSubmission<RESULT>(
  override val id: String,
  val status: SubmissionStatus,
  val apiErrorCode: Int?,
  @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
  val result: RESULT?,
  override val expiration: Long,
) : ExpiringRedisValue
