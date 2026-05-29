package io.amplica.custodial_wallet.client.claim

import io.amplica.custodial_wallet.client.redis.dto.TypedPayloadRequestWithSignature
import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto

data class UserAgreeRequest(
  val userPublicKey: PublicKeyDto,
  val payloads: List<TypedPayloadRequestWithSignature<*>>,
)

data class UserAgreeResponse(
  val termsTimestamp: Long,
  val msaId: String?,
)

data class NonceResponse(
  val nonce: String,
  val issuedAt: Long,
  val expirationTime: Long,
  val domain: String,
  val uri: String,
  val chainId: String,
  val statement: String,
)

interface ClaimServiceClient {
  suspend fun userAgree(
    userAgreeRequest: UserAgreeRequest
  ): UserAgreeResponse

  suspend fun getNonce(): NonceResponse
}