package io.amplica.custodial_wallet.dto

import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto

data class DeleteUserResponse(val result: Boolean)
data class DeleteUserByExternalIdRequest(
  val externalUserId: String,
  val publicKey: PublicKeyDto
)