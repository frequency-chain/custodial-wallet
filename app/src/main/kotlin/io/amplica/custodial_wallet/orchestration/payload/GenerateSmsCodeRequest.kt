package io.amplica.custodial_wallet.orchestration.payload

import io.amplica.custodial_wallet.orchestration.ScaleGenerateSmsCodePayload
import io.amplica.frequency.serialization.FrequencySerializable
import io.amplica.frequency.serialization.eip712.Eip712MessageAndTypes
import io.amplica.frequency.serialization.eip712.JsonSchemaTypeValue
import io.amplica.frequency.serialization.eip712.JsonSchemaTypes


data class GenerateSmsCodeRequest(val sessionId: String) : FrequencySerializable<ScaleGenerateSmsCodePayload> {

  companion object {
    val eip712Types = mapOf(
      "GenerateSmsCodeRequest" to JsonSchemaTypes.of(
        "sessionId" to JsonSchemaTypeValue.string,
      ).list(),
    )
  }

  override fun toEip712MessageAndTypes(): Eip712MessageAndTypes<Any> {
    return Eip712MessageAndTypes(
      eip712Types,
      "GenerateSmsCodeRequest",
      this,
    )
  }

  override fun toScaleObject(): ScaleGenerateSmsCodePayload {
    return ScaleGenerateSmsCodePayload(sessionId)
  }

}
