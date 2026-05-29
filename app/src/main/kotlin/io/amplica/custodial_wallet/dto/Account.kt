package io.amplica.custodial_wallet.dto

data class UserChangeHandleRequest(
  val newHandle: String
)

data class UserChangeHandleResponse(
  val claimedHandle: String
)
