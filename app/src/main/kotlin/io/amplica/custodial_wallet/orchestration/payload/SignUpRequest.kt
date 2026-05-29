package io.amplica.custodial_wallet.orchestration.payload

import io.amplica.custodial_wallet.orchestration.ScaleAddProviderPayloadRequest
import io.amplica.frequency.serialization.FrequencySerializable
import io.amplica.frequency.serialization.eip712.Eip712MessageAndTypes
import io.amplica.frequency.serialization.eip712.JsonSchemaTypeValue
import io.amplica.frequency.serialization.eip712.JsonSchemaTypes
import java.math.BigInteger


/** Used by the webviews for providers to request login on behalf of a user */
data class SignUpRequest(
  val msaId: BigInteger,
  val schemaIds: List<Int>,
  val url: String?
) : FrequencySerializable<ScaleAddProviderPayloadRequest> {

  companion object {
    val eip712Types = mapOf(
      "SignupRequest" to JsonSchemaTypes.of(
        "msaId" to JsonSchemaTypeValue.uint64,
        "schemaIds" to JsonSchemaTypeValue.uint16Array,
        // NOTE: EIP-712 does not support nullable values: https://eips.ethereum.org/EIPS/eip-712#specification
        "url" to JsonSchemaTypeValue.string,
      ).list(),
    )
  }

  override fun toEip712MessageAndTypes(): Eip712MessageAndTypes<Any> {
    // NOTE: EIP-712 does not support nullable values: https://eips.ethereum.org/EIPS/eip-712#specification
    val message = this.copy(url = url ?: "")

    return Eip712MessageAndTypes(
      eip712Types,
      "SignupRequest",
      message,
    )
  }

  override fun toScaleObject(): ScaleAddProviderPayloadRequest {
    return ScaleAddProviderPayloadRequest(msaId, schemaIds, url)
  }

}
