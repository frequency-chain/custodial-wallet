package io.amplica.custodial_wallet.verifiablecredentials

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.amplica.custodial_wallet.verifiablecredentials.canonicalization.TemplateBasedRdfCanonicalizer
import io.amplica.custodial_wallet.verifiablecredentials.codec.Base58MultibaseCodec
import io.amplica.custodial_wallet.verifiablecredentials.codec.KeyType
import io.amplica.custodial_wallet.verifiablecredentials.codec.MultikeyCodec
import io.amplica.custodial_wallet.verifiablecredentials.crypto.Ed25519SignatureManager
import io.amplica.custodial_wallet.verifiablecredentials.cryptosuite.EddsaRdfc2022
import io.amplica.custodial_wallet.verifiablecredentials.dto.*
import org.apache.commons.codec.binary.Hex
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime


class VerifiableCredentialAuthenticatorTest {

  private val mapper = jacksonObjectMapper().registerModule(JavaTimeModule())
  private val authenticator = VerifiableCredentialAuthenticator(
    listOf(
      EddsaRdfc2022(
        TemplateBasedRdfCanonicalizer,
        Base58MultibaseCodec,
        Ed25519SignatureManager,
        Ed25519SignatureManager
      )
    )
  )

  @Test
  fun selfVerificationRoundTripSucceeds() {
    // GIVEN
    val options = ProofOptions(
      ProofType.DataIntegrityProof,
      "did:web:frequencyaccess.com#z6MkucWENnaqLgYtJn4R1kjJAru1n2mi4ZH2tZQxQkQTUAAi",
      EddsaRdfc2022.NAME,
      ProofPurpose.AssertionMethod,
    )
    val credential = Credential(
      context = listOf(
        "https://www.w3.org/ns/credentials/v2",
        "https://www.w3.org/ns/credentials/undefined-terms/v2"
      ),
      type = listOf(CredentialType.VerifiableCredential, CredentialType.VerifiedEmailAddressCredential),
      issuer = "did:web:frequencyaccess.com",
      validFrom = ZonedDateTime.ofInstant(
        Instant.now(),
        ZoneId.of("UTC")
      ),
      credentialSchema = CredentialSchema.EMAIL_ADDRESS,
      credentialSubject = CredentialSubject.Email(
        "did:key:z6QNucQV4AF1XMQV4kngbmnBHwYa6mVswPEGrkFrUayhttT1",
        "john.doe@example.com",
        ZonedDateTime.ofInstant(
          Instant.now(),
          ZoneId.of("UTC")
        )
      ),
    )

    val seed = Hex.decodeHex("e5be9a5092b81bca64be81d212e7f2f9eba183bb7a90954f7b76361f6edb5c0a")
    val keyPair = Ed25519SignatureManager.newKeyPairFromSeed(seed)

    // WHEN
    val vc = authenticator.sign(options, credential, keyPair.privateKey)
    val vcJson = mapper.writeValueAsString(vc)
    val parsedVc = mapper.readValue(vcJson, VerifiableCredential::class.java)
    val isValid = authenticator.verify(parsedVc, keyPair.publicKey)

    // THEN
    Assertions.assertThat(isValid).isTrue()
  }

  @Test
  fun verificationOfValidCredentialSucceeds() {
    // GIVEN
    // Lifted from https://github.com/ProjectLibertyLabs/siwa
    val vc = mapper.readValue(
      """
      {
        "@context": [
          "https://www.w3.org/ns/credentials/v2",
          "https://www.w3.org/ns/credentials/undefined-terms/v2"
        ],
        "type": [ "VerifiedEmailAddressCredential", "VerifiableCredential" ],
        "issuer": "did:web:frequencyaccess.com",
        "validFrom": "2024-08-21T21:28:08.289+0000",
        "credentialSchema": {
          "type": "JsonSchema",
          "id": "https://schemas.frequencyaccess.com/VerifiedEmailAddressCredential/bciqe4qoczhftici4dzfvfbel7fo4h4sr5grco3oovwyk6y4ynf44tsi.json"
        },
        "credentialSubject": {
          "id": "did:key:z6QNucQV4AF1XMQV4kngbmnBHwYa6mVswPEGrkFrUayhttT1",
          "emailAddress": "john.doe@example.com",
          "lastVerified": "2024-08-21T21:27:59.309+0000"
        },
        "proof": {
          "type": "DataIntegrityProof",
          "verificationMethod": "did:web:frequencyaccess.com#z6MkofWExWkUvTZeXb9TmLta5mBT6Qtj58es5Fqg1L5BCWQD",
          "cryptosuite": "eddsa-rdfc-2022",
          "proofPurpose": "assertionMethod",
          "proofValue": "z4jArnPwuwYxLnbBirLanpkcyBpmQwmyn5f3PdTYnxhpy48qpgvHHav6warjizjvtLMg6j3FK3BqbR2nuyT2UTSWC"
        }
      }
    """.trimIndent(), VerifiableCredential::class.java
    )

    val encodedPublicKey = vc.proof.verificationMethod.split("#")[1]
    val publicKey = MultikeyCodec(KeyType.Ed25519, Base58MultibaseCodec).decode(encodedPublicKey)

    // WHEN
    val isValid = authenticator.verify(vc, publicKey)

    // THEN
    Assertions.assertThat(isValid).isTrue()
  }

  @Test
  fun invalidOptionsThrows() {
    // GIVEN
    val unsupportedCryptoSuite = "peters-spectacular-cryptosuite"
    val options = ProofOptions(
      ProofType.DataIntegrityProof,
      "did:web:frequencyaccess.com#1234",
      unsupportedCryptoSuite,
      ProofPurpose.AssertionMethod
    )
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
    val privateKey = Hex.decodeHex("1288ce31b8c6ad36b2bf77deed0571623493c0303040df38506c5d7fa57b02bf")

    // WHEN
    Assertions.assertThatThrownBy { authenticator.sign(options, credential, privateKey) }
      // THEN
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageStartingWith(unsupportedCryptoSuite)
  }

}
