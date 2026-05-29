package io.amplica.custodial_wallet.verifiablecredentials.cryptosuite

import io.amplica.custodial_wallet.verifiablecredentials.canonicalization.TemplateBasedRdfCanonicalizer
import io.amplica.custodial_wallet.verifiablecredentials.codec.Base58MultibaseCodec
import io.amplica.custodial_wallet.verifiablecredentials.crypto.Ed25519SignatureManager
import io.amplica.custodial_wallet.verifiablecredentials.dto.*
import org.apache.commons.codec.binary.Hex
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class EddsaRdfc2022Test {

  private val suite = EddsaRdfc2022(
    TemplateBasedRdfCanonicalizer,
    Base58MultibaseCodec,
    Ed25519SignatureManager,
    Ed25519SignatureManager
  )

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
    val output = suite.sha256Digest(suite.getCanonicalProofConfig(options))

    // THEN
    val expectedBytes = Hex.decodeHex("740341c09d462488151c9d47043ff77bd38d77645f40872b2c19c8ae79cb9cce")
    Assertions.assertThat(output).isEqualTo(expectedBytes)
  }

  @Test
  fun emailCredential() {
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
    val output = suite.sha256Digest(suite.getTransformedDocument(credential))

    // THEN
    val expectedBytes = Hex.decodeHex("f43186671ef7cdd9da4479d18e343707d52afca82b76b241d9992f055e9bfa41")
    Assertions.assertThat(output).isEqualTo(expectedBytes)
  }

}
