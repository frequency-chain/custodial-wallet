package io.amplica.custodial_wallet.client.redis.dto

import java.math.BigInteger

class AddIdentifierRequest(
  val userAccountId: BigInteger,
  val newIdentifier: String
)
