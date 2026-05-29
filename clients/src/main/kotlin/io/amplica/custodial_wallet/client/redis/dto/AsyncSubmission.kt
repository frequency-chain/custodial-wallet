package io.amplica.custodial_wallet.client.redis.dto

import arrow.core.Either
import java.math.BigInteger

enum class SubmissionStatus {
  SUBMITTED,
  SUCCESS,
  FAILED,
}

data class AsyncSubmission<RESULT>(
  val id: String,
  val status: SubmissionStatus,
  // Either an ApiError ID or the resulting value
  val result: Either<Int, RESULT>? = null,
)

data class DelegationGranted(
  val providerMsaId: BigInteger,
  val delegatorMsaId: BigInteger,
)
