package io.amplica.custodial_wallet.verifiablecredentials.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.ZonedDateTime


data class VerifiableCredential(
  @JsonProperty("@context") val context: List<String>,
  val type: List<CredentialType>,
  val issuer: String,
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  val validFrom: ZonedDateTime,
  val credentialSchema: CredentialSchema,
  val credentialSubject: CredentialSubject,
  val proof: Proof
) {

  companion object {
    fun of(credential: Credential, proof: Proof) = VerifiableCredential(
      credential.context,
      credential.type,
      credential.issuer,
      credential.validFrom,
      credential.credentialSchema,
      credential.credentialSubject,
      proof
    )
  }

  fun withoutProof() = Credential(
    this.context,
    this.type,
    this.issuer,
    this.validFrom,
    this.credentialSchema,
    this.credentialSubject
  )
}
