package io.amplica.custodial_wallet.orchestration.payload

import io.amplica.frequency.serialization.FrequencySerializable
import io.amplica.frequency.serialization.eip712.Eip712MessageAndTypes
import io.amplica.frequency.serialization.eip712.JsonSchemaTypeValue
import io.amplica.frequency.serialization.eip712.JsonSchemaTypes
import io.amplica.custodial_wallet.orchestration.ScaleHandlePayloadRequest


data class HandleRequest(val baseHandle: String) : FrequencySerializable<ScaleHandlePayloadRequest> {

  companion object {
    val eip712Types = mapOf(
      "HandleRequest" to JsonSchemaTypes.of(
        "baseHandle" to JsonSchemaTypeValue.string,
      ).list(),
    )
  }

  override fun toEip712MessageAndTypes(): Eip712MessageAndTypes<Any> {
    return Eip712MessageAndTypes(
      eip712Types,
      "HandleRequest",
      this,
    )
  }

  override fun toScaleObject(): ScaleHandlePayloadRequest {
    return ScaleHandlePayloadRequest(baseHandle)
  }

}
