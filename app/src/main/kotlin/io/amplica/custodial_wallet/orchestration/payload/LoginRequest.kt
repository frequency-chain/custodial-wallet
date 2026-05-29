package io.amplica.custodial_wallet.orchestration.payload

import io.amplica.custodial_wallet.orchestration.LoginPayload
import io.amplica.frequency.serialization.FrequencySerializable
import io.amplica.frequency.serialization.eip712.Eip712MessageAndTypes
import io.amplica.frequency.serialization.eip712.JsonSchemaTypeValue
import io.amplica.frequency.serialization.eip712.JsonSchemaTypes


/** Used by the webviews for providers to request login on behalf of a user */
data class LoginRequest(
  val nonce: String,
  val url: String?
) : FrequencySerializable<LoginPayload> {

  companion object {
    val eip712Types = mapOf(
      "LoginRequest" to JsonSchemaTypes.of(
        "nonce" to JsonSchemaTypeValue.string,
        // NOTE: EIP-712 does not support nullable values: https://eips.ethereum.org/EIPS/eip-712#specification
        "url" to JsonSchemaTypeValue.string,
      ).list()
    )
  }

  override fun toEip712MessageAndTypes(): Eip712MessageAndTypes<Any> {
    // NOTE: EIP-712 does not support nullable values: https://eips.ethereum.org/EIPS/eip-712#specification
    val message = this.copy(url = url ?: "")

    return Eip712MessageAndTypes(
      eip712Types,
      "LoginRequest",
      message,
    )
  }

  override fun toScaleObject(): LoginPayload {
    return LoginPayload(nonce, url)
  }

}
