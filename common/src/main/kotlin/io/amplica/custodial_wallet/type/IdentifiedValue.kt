package io.amplica.custodial_wallet.type

data class IdentifiedValue<ID, A>(
  val id: ID,
  val data: A,
)