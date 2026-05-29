package io.amplica.custodial_wallet.dto

import io.amplica.custodial_wallet.client.redis.dto.FrequencyKeyPairDto
import io.amplica.custodial_wallet.client.redis.dto.SubmissionStatus
import io.amplica.custodial_wallet.client.redis.dto.TypedPayloadResponseWithSignature
import io.amplica.custodial_wallet.exception.ApiErrorDto
import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto
import io.amplica.custodial_wallet.verifiablecredentials.dto.VerifiableCredential
import java.math.BigInteger
import java.net.URI

data class UserPayloadsAcceptanceAndDataCommand(
  val handle: String?,
)

data class SiwaPayloadResponse(
  val userPublicKey: PublicKeyDto,
  val userMsaId: BigInteger?,
  // NOTE(Julian, 2024-09-05): Keeping this property for now in order to not break compatibility for MeWe; However,
  // user keys are all now being passed via `credentials`.
  val userKeys: List<FrequencyKeyPairDto>,
  val payloads: List<TypedPayloadResponseWithSignature<*>>,
  val credentials: List<VerifiableCredential>,
  val termsCopy: String,
  val developerTermsLink: URI,
)

data class AsyncSubmissionResponse(
  val id: String,
  val status: SubmissionStatus,
  val error: ApiErrorDto? = null,
)
