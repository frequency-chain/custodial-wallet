package io.amplica.custodial_wallet.verifiablecredentials.canonicalization

import io.amplica.custodial_wallet.verifiablecredentials.dto.Credential
import io.amplica.custodial_wallet.verifiablecredentials.dto.CredentialSubject
import io.amplica.custodial_wallet.verifiablecredentials.dto.CredentialType
import io.amplica.custodial_wallet.verifiablecredentials.dto.ProofOptions
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter


/**
 * Transforms credential and proof documents into a canonical form based on the
 * [RDF Canonicalization (N-Quads) spec](https://www.w3.org/TR/rdf-canon/#canonical-quads) (lines sorted ascending).
 */
object TemplateBasedRdfCanonicalizer : Canonicalizer {

  // Uppercase 'Z' indicates no colon in the offset (e.g., '+0000')
  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

  fun serializeDateTime(time: ZonedDateTime): String {
    return time.withZoneSameInstant(ZoneOffset.UTC).format(dateTimeFormatter)
  }

  override fun serialize(options: ProofOptions): String {
    return """
      _:c14n0 <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://w3id.org/security#${options.type}> .
      _:c14n0 <https://w3id.org/security#cryptosuite> "${options.cryptosuite}"^^<https://w3id.org/security#cryptosuiteString> .
      _:c14n0 <https://w3id.org/security#proofPurpose> <https://w3id.org/security#${options.proofPurpose.value}> .
      _:c14n0 <https://w3id.org/security#verificationMethod> <${options.verificationMethod}> .
      
    """.trimIndent()
  }

  override fun serialize(credential: Credential): String {
    return when (val subject = credential.credentialSubject) {
      is CredentialSubject.Email -> """
        <${subject.id}> <https://www.w3.org/ns/credentials/undefined-term#emailAddress> "${subject.emailAddress}" .
        <${subject.id}> <https://www.w3.org/ns/credentials/undefined-term#lastVerified> "${serializeDateTime(subject.lastVerified)}" .
        <${credential.credentialSchema.id}> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://www.w3.org/2018/credentials#${credential.credentialSchema.type}> .
        _:c14n0 <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://www.w3.org/2018/credentials#${CredentialType.VerifiableCredential.value}> .
        _:c14n0 <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://www.w3.org/ns/credentials/undefined-term#${CredentialType.VerifiedEmailAddressCredential.value}> .
        _:c14n0 <https://www.w3.org/2018/credentials#credentialSchema> <${credential.credentialSchema.id}> .
        _:c14n0 <https://www.w3.org/2018/credentials#credentialSubject> <${subject.id}> .
        _:c14n0 <https://www.w3.org/2018/credentials#issuer> <${credential.issuer}> .
        _:c14n0 <https://www.w3.org/2018/credentials#validFrom> "${serializeDateTime(credential.validFrom)}"^^<http://www.w3.org/2001/XMLSchema#dateTime> .
      
      """.trimIndent()

      is CredentialSubject.KeyPair -> """
        <${subject.id}> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://www.w3.org/ns/credentials/undefined-term#${subject.type}> .
        <${subject.id}> <https://www.w3.org/ns/credentials/undefined-term#encodedPrivateKeyValue> "${subject.encodedPrivateKeyValue}" .
        <${subject.id}> <https://www.w3.org/ns/credentials/undefined-term#encodedPublicKeyValue> "${subject.encodedPublicKeyValue}" .
        <${subject.id}> <https://www.w3.org/ns/credentials/undefined-term#encoding> "${subject.encoding.value}" .
        <${subject.id}> <https://www.w3.org/ns/credentials/undefined-term#format> "${subject.format.value}" .
        <${subject.id}> <https://www.w3.org/ns/credentials/undefined-term#keyType> "${subject.keyType.value}" .
        <${credential.credentialSchema.id}> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://www.w3.org/2018/credentials#${credential.credentialSchema.type}> .
        _:c14n0 <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://www.w3.org/2018/credentials#${CredentialType.VerifiableCredential.value}> .
        _:c14n0 <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://www.w3.org/ns/credentials/undefined-term#${CredentialType.VerifiedGraphKeyCredential.value}> .
        _:c14n0 <https://www.w3.org/2018/credentials#credentialSchema> <${credential.credentialSchema.id}> .
        _:c14n0 <https://www.w3.org/2018/credentials#credentialSubject> <${subject.id}> .
        _:c14n0 <https://www.w3.org/2018/credentials#issuer> <${credential.issuer}> .
        _:c14n0 <https://www.w3.org/2018/credentials#validFrom> "${serializeDateTime(credential.validFrom)}"^^<http://www.w3.org/2001/XMLSchema#dateTime> .
        
      """.trimIndent()

      is CredentialSubject.PhoneNumber -> """
        <${subject.id}> <https://www.w3.org/ns/credentials/undefined-term#lastVerified> "${serializeDateTime(subject.lastVerified)}" .
        <${subject.id}> <https://www.w3.org/ns/credentials/undefined-term#phoneNumber> "${subject.phoneNumber}" .
        <${credential.credentialSchema.id}> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://www.w3.org/2018/credentials#${credential.credentialSchema.type}> .
        _:c14n0 <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://www.w3.org/2018/credentials#${CredentialType.VerifiableCredential.value}> .
        _:c14n0 <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://www.w3.org/ns/credentials/undefined-term#${CredentialType.VerifiedPhoneNumberCredential.value}> .
        _:c14n0 <https://www.w3.org/2018/credentials#credentialSchema> <${credential.credentialSchema.id}> .
        _:c14n0 <https://www.w3.org/2018/credentials#credentialSubject> <${subject.id}> .
        _:c14n0 <https://www.w3.org/2018/credentials#issuer> <${credential.issuer}> .
        _:c14n0 <https://www.w3.org/2018/credentials#validFrom> "${serializeDateTime(credential.validFrom)}"^^<http://www.w3.org/2001/XMLSchema#dateTime> .
      
      """.trimIndent()
    }
  }

}
