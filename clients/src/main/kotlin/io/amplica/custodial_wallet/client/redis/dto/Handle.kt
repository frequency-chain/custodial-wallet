package io.amplica.custodial_wallet.client.redis.dto

import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto

data class ChangeHandleRequest(
  val userPublicKey: PublicKeyDto,
  val providerPublicKey: PublicKeyDto,
  val handle: HandleRequest,
)

data class ChangeHandleResponse(
  val userPublicKey: PublicKeyDto,
  val handle: HandleResponse,
)