package io.amplica.custodial_wallet.client.redis.dto

import java.time.Instant

data class IdentifierVerification(
  val currentCode: String,
  val lastSentAt: Instant,
  val totalSendCount: Int,
  val incorrectAttemptCount: Int,
)