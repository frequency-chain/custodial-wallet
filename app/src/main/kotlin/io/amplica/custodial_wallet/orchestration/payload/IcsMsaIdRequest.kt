package io.amplica.custodial_wallet.orchestration.payload

import io.amplica.custodial_wallet.orchestration.ScaleIcsMsaIdRequest
import io.amplica.frequency.serialization.FrequencySerializable
import io.amplica.frequency.serialization.eip712.Eip712MessageAndTypes
import io.amplica.frequency.serialization.eip712.JsonSchemaTypeValue
import io.amplica.frequency.serialization.eip712.JsonSchemaTypes
import java.math.BigInteger

data class IcsMsaIdRequest(
  val msaId: BigInteger,
  val nonce: String
) : FrequencySerializable<ScaleIcsMsaIdRequest> {
  companion object {
    val eip712Types = mapOf(
      "IcsMsaIdRequest" to JsonSchemaTypes.of(
        "msaId" to JsonSchemaTypeValue.uint64,
        "nonce" to JsonSchemaTypeValue.string,
      ).list()
    )
  }

  override fun toEip712MessageAndTypes(): Eip712MessageAndTypes<Any> {
    return Eip712MessageAndTypes(eip712Types, "IcsMsaIdRequest", this)
  }

  override fun toScaleObject(): ScaleIcsMsaIdRequest {
    return ScaleIcsMsaIdRequest(msaId, nonce)
  }
}