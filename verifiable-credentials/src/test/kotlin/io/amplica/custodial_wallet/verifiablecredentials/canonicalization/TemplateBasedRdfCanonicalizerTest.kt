package io.amplica.custodial_wallet.verifiablecredentials.canonicalization

import io.amplica.custodial_wallet.verifiablecredentials.dto.*
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class TemplateBasedRdfCanonicalizerTest {

  @ParameterizedTest
  @CsvSource(value = [
    "2024-08-21T21:28:08.289Z, 2024-08-21T21:28:08.289+0000",
    "2024-08-21T21:28:08.289-07:00[America/Los_Angeles], 2024-08-22T04:28:08.289+0000"
  ])
  fun serializeDateTime(input: String, expected: String) {
    // GIVEN
    val time = ZonedDateTime.parse(input)

    // WHEN
    val output = TemplateBasedRdfCanonicalizer.serializeDateTime(time)

    // THEN
    Assertions.assertThat(output).isEqualTo(expected)
  }

  @Test
  fun options() {
    // GIVEN
    val options = ProofOptions(
      ProofType.DataIntegrityProof,
      "did:web:frequencyaccess.com#z6MkucWENnaqLgYtJn4R1kjJAru1n2mi4ZH2tZQxQkQTUAAi",
      "eddsa-rdfc-2022",
      ProofPurpose.AssertionMethod,
    )

    // WHEN
    val canonicalDocument = TemplateBasedRdfCanonicalizer.serialize(options)

    // THEN
    val expectedDocument = (
      "_:c14n0 <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://w3id.org/security#DataIntegrityProof> .\n" +
      "_:c14n0 <https://w3id.org/security#cryptosuite> \"eddsa-rdfc-2022\"^^<https://w3id.org/security#cryptosuiteString> .\n" +
      "_:c14n0 <https://w3id.org/security#proofPurpose> <https://w3id.org/security#assertionMethod> .\n" +
      "_:c14n0 <https://w3id.org/security#verificationMethod> <did:web:frequencyaccess.com#z6MkucWENnaqLgYtJn4R1kjJAru1n2mi4ZH2tZQxQkQTUAAi> .\n"
    )
    Assertions.assertThat(canonicalDocument).isEqualTo(expectedDocument)
  }

  @Test
  fun email() {
    // GIVEN
    val credential = Credential(
      context = listOf(
        "https://www.w3.org/ns/credentials/v2",
        "https://www.w3.org/ns/credentials/undefined-terms/v2"
      ),
      type = listOf(CredentialType.VerifiableCredential, CredentialType.VerifiedEmailAddressCredential),
      issuer = "did:web:frequencyaccess.com",
      validFrom = ZonedDateTime.of(
        LocalDateTime.of(2024, 8, 21, 21, 28, 8, 289000000),
        ZoneId.of("UTC"),
      ),
      credentialSchema = CredentialSchema.EMAIL_ADDRESS,
      credentialSubject = CredentialSubject.Email(
        "did:key:z6QNucQV4AF1XMQV4kngbmnBHwYa6mVswPEGrkFrUayhttT1",
        "john.doe@example.com",
        ZonedDateTime.of(
          LocalDateTime.of(2024, 8, 21, 21, 27, 59, 309000000),
          ZoneId.of("UTC")
        )
      ),
    )

    // WHEN
    val canonicalDocument = TemplateBasedRdfCanonicalizer.serialize(credential)

    // THEN
    val expectedDocument = (
      "<did:key:z6QNucQV4AF1XMQV4kngbmnBHwYa6mVswPEGrkFrUayhttT1> <https://www.w3.org/ns/credentials/undefined-term#emailAddress> \"john.doe@example.com\" .\n" +
      "<did:key:z6QNucQV4AF1XMQV4kngbmnBHwYa6mVswPEGrkFrUayhttT1> <https://www.w3.org/ns/credentials/undefined-term#lastVerified> \"2024-08-21T21:27:59.309+0000\" .\n" +
      "<https://schemas.frequencyaccess.com/VerifiedEmailAddressCredential/bciqe4qoczhftici4dzfvfbel7fo4h4sr5grco3oovwyk6y4ynf44tsi.json> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://www.w3.org/2018/credentials#JsonSchema> .\n" +
      "_:c14n0 <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://www.w3.org/2018/credentials#VerifiableCredential> .\n" +
      "_:c14n0 <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://www.w3.org/ns/credentials/undefined-term#VerifiedEmailAddressCredential> .\n" +
      "_:c14n0 <https://www.w3.org/2018/credentials#credentialSchema> <https://schemas.frequencyaccess.com/VerifiedEmailAddressCredential/bciqe4qoczhftici4dzfvfbel7fo4h4sr5grco3oovwyk6y4ynf44tsi.json> .\n" +
      "_:c14n0 <https://www.w3.org/2018/credentials#credentialSubject> <did:key:z6QNucQV4AF1XMQV4kngbmnBHwYa6mVswPEGrkFrUayhttT1> .\n" +
      "_:c14n0 <https://www.w3.org/2018/credentials#issuer> <did:web:frequencyaccess.com> .\n" +
      "_:c14n0 <https://www.w3.org/2018/credentials#validFrom> \"2024-08-21T21:28:08.289+0000\"^^<http://www.w3.org/2001/XMLSchema#dateTime> .\n"
    )
    Assertions.assertThat(canonicalDocument).isEqualTo(expectedDocument)
  }

  @Test
  fun keyPair() {
    // GIVEN
    val credential = Credential(
      context = listOf(
        "https://www.w3.org/ns/credentials/v2",
        "https://www.w3.org/ns/credentials/undefined-terms/v2"
      ),
      type = listOf(CredentialType.VerifiableCredential, CredentialType.VerifiedGraphKeyCredential),
      issuer = "did:web:frequencyaccess.com",
      validFrom = ZonedDateTime.of(
        LocalDateTime.of(2024, 8, 21, 21, 28, 8, 289000000),
        ZoneId.of("UTC"),
      ),
      credentialSchema = CredentialSchema.KEY_PAIR,
      credentialSubject = CredentialSubject.KeyPair(
        "did:key:z6QNucQV4AF1XMQV4kngbmnBHwYa6mVswPEGrkFrUayhttT1",
        "encoded-public-key",
        "encoded-private-key",
        KeyPairEncoding.BASE_16,
        KeyPairFormat.BARE,
        KeyPairType.X25519,
        DsnpKeyType.PublicKeyKeyAgreement
      ),
    )

    // WHEN
    val canonicalDocument = TemplateBasedRdfCanonicalizer.serialize(credential)

    // THEN
    val expectedDocument = (
      "<did:key:z6QNucQV4AF1XMQV4kngbmnBHwYa6mVswPEGrkFrUayhttT1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://www.w3.org/ns/credentials/undefined-term#X25519> .\n" +
      "<did:key:z6QNucQV4AF1XMQV4kngbmnBHwYa6mVswPEGrkFrUayhttT1> <https://www.w3.org/ns/credentials/undefined-term#encodedPrivateKeyValue> \"encoded-private-key\" .\n" +
      "<did:key:z6QNucQV4AF1XMQV4kngbmnBHwYa6mVswPEGrkFrUayhttT1> <https://www.w3.org/ns/credentials/undefined-term#encodedPublicKeyValue> \"encoded-public-key\" .\n" +
      "<did:key:z6QNucQV4AF1XMQV4kngbmnBHwYa6mVswPEGrkFrUayhttT1> <https://www.w3.org/ns/credentials/undefined-term#encoding> \"base16\" .\n" +
      "<did:key:z6QNucQV4AF1XMQV4kngbmnBHwYa6mVswPEGrkFrUayhttT1> <https://www.w3.org/ns/credentials/undefined-term#format> \"bare\" .\n" +
      "<did:key:z6QNucQV4AF1XMQV4kngbmnBHwYa6mVswPEGrkFrUayhttT1> <https://www.w3.org/ns/credentials/undefined-term#keyType> \"dsnp.public-key-key-agreement\" .\n" +
      "<https://schemas.frequencyaccess.com/VerifiedGraphKeyCredential/bciqmdvmxd54zve5kifycgsdtoahs5ecf4hal2ts3eexkgocyc5oca2y.json> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://www.w3.org/2018/credentials#JsonSchema> .\n" +
      "_:c14n0 <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://www.w3.org/2018/credentials#VerifiableCredential> .\n" +
      "_:c14n0 <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://www.w3.org/ns/credentials/undefined-term#VerifiedGraphKeyCredential> .\n" +
      "_:c14n0 <https://www.w3.org/2018/credentials#credentialSchema> <https://schemas.frequencyaccess.com/VerifiedGraphKeyCredential/bciqmdvmxd54zve5kifycgsdtoahs5ecf4hal2ts3eexkgocyc5oca2y.json> .\n" +
      "_:c14n0 <https://www.w3.org/2018/credentials#credentialSubject> <did:key:z6QNucQV4AF1XMQV4kngbmnBHwYa6mVswPEGrkFrUayhttT1> .\n" +
      "_:c14n0 <https://www.w3.org/2018/credentials#issuer> <did:web:frequencyaccess.com> .\n" +
      "_:c14n0 <https://www.w3.org/2018/credentials#validFrom> \"2024-08-21T21:28:08.289+0000\"^^<http://www.w3.org/2001/XMLSchema#dateTime> .\n"
    )
    Assertions.assertThat(canonicalDocument).isEqualTo(expectedDocument)
  }

  @Test
  fun phoneNumber() {
    // GIVEN
    val credential = Credential(
      context = listOf(
        "https://www.w3.org/ns/credentials/v2",
        "https://www.w3.org/ns/credentials/undefined-terms/v2"
      ),
      type = listOf(CredentialType.VerifiableCredential, CredentialType.VerifiedPhoneNumberCredential),
      issuer = "did:web:frequencyaccess.com",
      validFrom = ZonedDateTime.of(
        LocalDateTime.of(2024, 8, 21, 21, 28, 8, 289000000),
        ZoneId.of("UTC"),
      ),
      credentialSchema = CredentialSchema.PHONE_NUMBER,
      credentialSubject = CredentialSubject.PhoneNumber(
        "did:key:z6QNucQV4AF1XMQV4kngbmnBHwYa6mVswPEGrkFrUayhttT1",
        "+12345678910",
        ZonedDateTime.of(
          LocalDateTime.of(2024, 8, 21, 21, 27, 59, 309000000),
          ZoneId.of("UTC")
        )
      ),
    )

    // WHEN
    val canonicalDocument = TemplateBasedRdfCanonicalizer.serialize(credential)

    // THEN
    val expectedDocument = (
      "<did:key:z6QNucQV4AF1XMQV4kngbmnBHwYa6mVswPEGrkFrUayhttT1> <https://www.w3.org/ns/credentials/undefined-term#lastVerified> \"2024-08-21T21:27:59.309+0000\" .\n" +
      "<did:key:z6QNucQV4AF1XMQV4kngbmnBHwYa6mVswPEGrkFrUayhttT1> <https://www.w3.org/ns/credentials/undefined-term#phoneNumber> \"+12345678910\" .\n" +
      "<https://schemas.frequencyaccess.com/VerifiedPhoneNumberCredential/bciqjspnbwpc3wjx4fewcek5daysdjpbf5xjimz5wnu5uj7e3vu2uwnq.json> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://www.w3.org/2018/credentials#JsonSchema> .\n" +
      "_:c14n0 <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://www.w3.org/2018/credentials#VerifiableCredential> .\n" +
      "_:c14n0 <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://www.w3.org/ns/credentials/undefined-term#VerifiedPhoneNumberCredential> .\n" +
      "_:c14n0 <https://www.w3.org/2018/credentials#credentialSchema> <https://schemas.frequencyaccess.com/VerifiedPhoneNumberCredential/bciqjspnbwpc3wjx4fewcek5daysdjpbf5xjimz5wnu5uj7e3vu2uwnq.json> .\n" +
      "_:c14n0 <https://www.w3.org/2018/credentials#credentialSubject> <did:key:z6QNucQV4AF1XMQV4kngbmnBHwYa6mVswPEGrkFrUayhttT1> .\n" +
      "_:c14n0 <https://www.w3.org/2018/credentials#issuer> <did:web:frequencyaccess.com> .\n" +
      "_:c14n0 <https://www.w3.org/2018/credentials#validFrom> \"2024-08-21T21:28:08.289+0000\"^^<http://www.w3.org/2001/XMLSchema#dateTime> .\n"
    )
    Assertions.assertThat(canonicalDocument).isEqualTo(expectedDocument)
  }

}
