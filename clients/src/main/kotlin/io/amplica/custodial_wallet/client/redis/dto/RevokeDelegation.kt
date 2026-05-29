package io.amplica.custodial_wallet.client.redis.dto

import java.math.BigInteger

data class RevokeDelegationRequest(
  val providerMsaId: BigInteger,
  val userPublicKeyHex: String,
)