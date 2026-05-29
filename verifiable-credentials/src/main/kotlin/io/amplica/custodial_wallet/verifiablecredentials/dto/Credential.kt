package io.amplica.custodial_wallet.verifiablecredentials.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import java.time.ZonedDateTime


data class Credential(
  @JsonProperty("@context") val context: List<String>,
  val type: List<CredentialType>,
  val issuer: String,
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  val validFrom: ZonedDateTime,
  val credentialSchema: CredentialSchema,
  val credentialSubject: CredentialSubject
)

enum class CredentialType(@JsonValue val value: String) {
  VerifiableCredential("VerifiableCredential"),
  VerifiedEmailAddressCredential("VerifiedEmailAddressCredential"),
  VerifiedGraphKeyCredential("VerifiedGraphKeyCredential"),
  VerifiedPhoneNumberCredential("VerifiedPhoneNumberCredential"),
}

// For updated values see: https://schemas.frequencyaccess.com
object VerifiedCredentialSchemaId {
  const val EMAIL_ADDRESS = "https://schemas.frequencyaccess.com/VerifiedEmailAddressCredential/bciqe4qoczhftici4dzfvfbel7fo4h4sr5grco3oovwyk6y4ynf44tsi.json"
  const val KEY_PAIR = "https://schemas.frequencyaccess.com/VerifiedGraphKeyCredential/bciqmdvmxd54zve5kifycgsdtoahs5ecf4hal2ts3eexkgocyc5oca2y.json"
  const val PHONE_NUMBER = "https://schemas.frequencyaccess.com/VerifiedPhoneNumberCredential/bciqjspnbwpc3wjx4fewcek5daysdjpbf5xjimz5wnu5uj7e3vu2uwnq.json"
}

data class CredentialSchema(
  val type: CredentialSchemaType,
  val id: String
) {
  companion object {
    val EMAIL_ADDRESS = CredentialSchema(CredentialSchemaType.JsonSchema, VerifiedCredentialSchemaId.EMAIL_ADDRESS)
    val KEY_PAIR = CredentialSchema(CredentialSchemaType.JsonSchema, VerifiedCredentialSchemaId.KEY_PAIR)
    val PHONE_NUMBER = CredentialSchema(CredentialSchemaType.JsonSchema, VerifiedCredentialSchemaId.PHONE_NUMBER)
  }
}

enum class CredentialSchemaType(@JsonValue val type: String) {
  JsonSchema("JsonSchema")
}
