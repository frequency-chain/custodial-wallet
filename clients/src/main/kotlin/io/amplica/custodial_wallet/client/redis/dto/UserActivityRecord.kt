package io.amplica.custodial_wallet.client.redis.dto

import java.math.BigInteger
import java.time.Duration
import java.time.Instant

data class UserActivityRecord(
  val userAccountId: BigInteger,
  val expiration: Duration,
  val handleLastChanged: Instant?,
)
