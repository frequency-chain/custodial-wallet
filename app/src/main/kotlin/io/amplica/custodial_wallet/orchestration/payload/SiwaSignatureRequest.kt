package io.amplica.custodial_wallet.orchestration.payload

import io.amplica.custodial_wallet.orchestration.ScaleSiwaPayload
import io.amplica.frequency.serialization.FrequencySerializable
import io.amplica.frequency.serialization.eip712.Eip712MessageAndTypes
import io.amplica.frequency.serialization.eip712.JsonSchemaTypeValue
import io.amplica.frequency.serialization.eip712.JsonSchemaTypes


data class SiwaSignatureRequest(
  val callback: String,
  val permissions: List<Int>, // List of schema IDs
  val userIdentifierAdminUrl: String?,
) : FrequencySerializable<ScaleSiwaPayload> {

  companion object {
    val eip712Types = mapOf(
      "SiwfSignedRequestPayload" to JsonSchemaTypes.of(
        "callback" to JsonSchemaTypeValue.string,
        "permissions" to "uint16[]",
        "userIdentifierAdminUrl" to JsonSchemaTypeValue.string
      ).list()
    )
  }

  override fun toEip712MessageAndTypes(): Eip712MessageAndTypes<Any> {
    // NOTE: EIP-712 does not support nullable values: https://eips.ethereum.org/EIPS/eip-712#specification
    val message = this.copy(userIdentifierAdminUrl = userIdentifierAdminUrl ?: "")

    return Eip712MessageAndTypes(
      eip712Types,
      "SiwfSignedRequestPayload",
      message,
    )
  }

  override fun toScaleObject(): ScaleSiwaPayload {
    return ScaleSiwaPayload(callback, permissions, userIdentifierAdminUrl)
  }

}
