package io.amplica.custodial_wallet.orchestration.payload

import io.amplica.custodial_wallet.orchestration.ScaleIcsContextItemKeyPayload
import io.amplica.frequency.serialization.FrequencySerializable
import io.amplica.frequency.serialization.eip712.Eip712MessageAndTypes
import io.amplica.frequency.serialization.eip712.JsonSchemaTypeValue
import io.amplica.frequency.serialization.eip712.JsonSchemaTypes
import java.math.BigInteger

data class IcsContextItemKeyRequest(
  val msaId: BigInteger,
  val contextItemId: String,
  val nonce: String
) : FrequencySerializable<ScaleIcsContextItemKeyPayload> {
  companion object {
    val eip712Types = mapOf(
      "IcsContextItemKeyRequest" to JsonSchemaTypes.of(
        "userIdentifierValue" to JsonSchemaTypeValue.string,
        "userIdentifierType" to JsonSchemaTypeValue.string,
        "contextItemId" to JsonSchemaTypeValue.string,
        "nonce" to JsonSchemaTypeValue.string,
      ).list()
    )
  }

  override fun toEip712MessageAndTypes(): Eip712MessageAndTypes<Any> {
    return Eip712MessageAndTypes(eip712Types, "IcsContextItemKeyRequest", this)
  }

  override fun toScaleObject(): ScaleIcsContextItemKeyPayload {
    return ScaleIcsContextItemKeyPayload(msaId, contextItemId, nonce)
  }
}