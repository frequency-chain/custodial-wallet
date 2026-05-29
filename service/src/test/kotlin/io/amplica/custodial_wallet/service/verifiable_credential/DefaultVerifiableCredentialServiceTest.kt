package io.amplica.custodial_wallet.service.verifiable_credential

import io.amplica.custodial_wallet.client.redis.dto.RequestedCredentialType
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifier
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifierType
import io.amplica.custodial_wallet.template.TemplateRenderer
import io.amplica.custodial_wallet.util.key_creation.Sr25519KeyPairBytes
import io.amplica.custodial_wallet.util.key_creation.X25519KeyPair
import io.amplica.custodial_wallet.verifiablecredentials.VerifiableCredentialAuthenticator
import io.amplica.custodial_wallet.verifiablecredentials.codec.Codec
import io.amplica.custodial_wallet.verifiablecredentials.codec.MultikeyCodec
import io.amplica.custodial_wallet.verifiablecredentials.crypto.KeyPair
import io.amplica.custodial_wallet.verifiablecredentials.cryptosuite.EddsaRdfc2022
import io.amplica.custodial_wallet.verifiablecredentials.dto.*
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.codec.Hex
import java.time.Duration
import java.time.ZonedDateTime

class DefaultVerifiableCredentialServiceTest {

  companion object {

    object TestConfig {
      const val HOST_NAME = "example.com"
      val APPLICATION_KEY_PAIR = KeyPair(
        Hex.decode("436e".repeat(16)),
        Hex.decode("8900".repeat(16)),
      )
    }

    object TestData {
      val LAST_VERIFIED: ZonedDateTime = ZonedDateTime.parse("2024-01-01T00:00:00Z")
      const val EXAMPLE_PROOF_VALUE = "example-proof-value"
      const val ENCODED_GRAPH_PUBLIC_KEY = "0x0000000000000000000000000000000000000000000000000000000000000000"
      const val ENCODED_GRAPH_PRIVATE_KEY = "0x1234567812345678123456781234567812345678123456781234567812345678"
      const val ENCODED_APPLICATION_PUBLIC_KEY = "encoded-application-public-key"
      const val ENCODED_USER_PUBLIC_KEY = "encoded-user-public-key"
      const val DID_JSON = "{\"foo\": \"bar\"}"
    }
  }

  private val didJsonTemplateRenderer: TemplateRenderer = mock()
  private val authenticator: VerifiableCredentialAuthenticator = mock()
  private val base58Codec: Codec<ByteArray, String> = mock()
  private val applicationKeyCodec: MultikeyCodec = mock()
  private val userKeyCodec: MultikeyCodec = mock()

  @ParameterizedTest
  @CsvSource(
    value = [
      "VerifiedEmailAddressCredential, foo@gmail.com, , ",
      "VerifiedGraphKeyCredential, , 0000000000000000000000000000000000000000000000000000000000000000, 1234567812345678123456781234567812345678123456781234567812345678",
      "VerifiedPhoneNumberCredential, +15001115555, , ",
    ]
  )
  fun createCredential(
    credentialType: RequestedCredentialType, userIdentifierValue: String?, publicKeyHex: String?, privateKeyHex: String?
  ) {
    // GIVEN
    val userIdentifier = userIdentifierValue?.let { value ->
      when (credentialType) {
        RequestedCredentialType.VerifiedEmailAddressCredential -> UserIdentifier(value, UserIdentifierType.EMAIL)
        RequestedCredentialType.VerifiedGraphKeyCredential -> throw IllegalArgumentException("'userIdentifier' must not be defined")
        RequestedCredentialType.VerifiedPhoneNumberCredential -> UserIdentifier(
          value, UserIdentifierType.PHONE_NUMBER
        )
      }
    }

    val graphKey = publicKeyHex?.let { pubHex ->
      val pkHex = privateKeyHex ?: throw IllegalArgumentException("Private key must be defined")
      X25519KeyPair(Hex.decode(pubHex), Hex.decode(pkHex))
    }

    val userKeyPair = Sr25519KeyPairBytes(
      Hex.decode("aa45".repeat(16)),
      Hex.decode("0777".repeat(16)),
    )

    // WITH MOCKS
    whenever(authenticator.sign(any(), any(), eq(TestConfig.APPLICATION_KEY_PAIR.privateKey))).then { invocation ->
      val options = invocation.arguments[0] as ProofOptions
      val credential = invocation.arguments[1] as Credential

      VerifiableCredential.of(credential, Proof.of(options, TestData.EXAMPLE_PROOF_VALUE))
    }

    if (credentialType == RequestedCredentialType.VerifiedGraphKeyCredential) {
      graphKey ?: throw IllegalStateException("'graphKey' undefined")
      // Encoding the graph public / private keys
      whenever(base58Codec.encode(eq(graphKey.publicKeyBytes))).thenReturn(TestData.ENCODED_GRAPH_PUBLIC_KEY)
      whenever(base58Codec.encode(eq(graphKey.privateKeyBytes))).thenReturn(TestData.ENCODED_GRAPH_PRIVATE_KEY)
    }

    whenever(applicationKeyCodec.encode(TestConfig.APPLICATION_KEY_PAIR.publicKey)).thenReturn(TestData.ENCODED_APPLICATION_PUBLIC_KEY)

    whenever(userKeyCodec.encode(userKeyPair.publicKeyBytes)).thenReturn(TestData.ENCODED_USER_PUBLIC_KEY)

    // WHEN
    val service = DefaultVerifiableCredentialService(
      didJsonTemplateRenderer,
      TestConfig.HOST_NAME,
      TestConfig.APPLICATION_KEY_PAIR,
      authenticator,
      applicationKeyCodec,
      userKeyCodec,
      userKeyCodec,
      Duration.ofSeconds(0)
    )
    val verifiableCredential = when (credentialType) {
      RequestedCredentialType.VerifiedGraphKeyCredential -> service.createVerifiableCredential(userKeyPair, graphKey!!)
      else -> service.createVerifiableCredential(userKeyPair, userIdentifier!!, TestData.LAST_VERIFIED)
    }

    // THEN
    Assertions.assertThat(verifiableCredential.context)
      .containsExactly("https://www.w3.org/ns/credentials/v2", "https://www.w3.org/ns/credentials/undefined-terms/v2")
    Assertions.assertThat(verifiableCredential.issuer)
      .isEqualTo("did:web:${TestConfig.HOST_NAME}")
    Assertions.assertThat(verifiableCredential.validFrom)
      .isBetween(ZonedDateTime.now().minusSeconds(5), ZonedDateTime.now())

    Assertions.assertThat(verifiableCredential.proof).isEqualTo(
      Proof(
        ProofType.DataIntegrityProof,
        "did:web:${TestConfig.HOST_NAME}#${TestData.ENCODED_APPLICATION_PUBLIC_KEY}",
        EddsaRdfc2022.NAME,
        ProofPurpose.AssertionMethod,
        TestData.EXAMPLE_PROOF_VALUE
      )
    )

    val expectedId = "${DidMethod.KEY}:${TestData.ENCODED_USER_PUBLIC_KEY}"
    when (credentialType) {
      RequestedCredentialType.VerifiedEmailAddressCredential -> {
        Assertions.assertThat(verifiableCredential.type).containsExactly(
          CredentialType.VerifiedEmailAddressCredential, CredentialType.VerifiableCredential
        )
        Assertions.assertThat(verifiableCredential.credentialSchema).isEqualTo(CredentialSchema.EMAIL_ADDRESS)
        Assertions.assertThat(verifiableCredential.credentialSubject)
          .isEqualTo(
            CredentialSubject.Email(
              expectedId,
              userIdentifier!!.value,
              TestData.LAST_VERIFIED,
            )
          )
      }

      RequestedCredentialType.VerifiedGraphKeyCredential -> {
        Assertions.assertThat(verifiableCredential.type).containsExactly(
          CredentialType.VerifiedGraphKeyCredential, CredentialType.VerifiableCredential
        )
        Assertions.assertThat(verifiableCredential.credentialSchema).isEqualTo(CredentialSchema.KEY_PAIR)
        Assertions.assertThat(verifiableCredential.credentialSubject)
          .isEqualTo(
            CredentialSubject.KeyPair(
              expectedId,
              TestData.ENCODED_GRAPH_PUBLIC_KEY,
              TestData.ENCODED_GRAPH_PRIVATE_KEY,
              KeyPairEncoding.BASE_16,
              KeyPairFormat.BARE,
              KeyPairType.X25519,
              DsnpKeyType.PublicKeyKeyAgreement
            )
          )
      }

      RequestedCredentialType.VerifiedPhoneNumberCredential -> {
        Assertions.assertThat(verifiableCredential.type).containsExactly(
          CredentialType.VerifiedPhoneNumberCredential, CredentialType.VerifiableCredential
        )
        Assertions.assertThat(verifiableCredential.credentialSchema).isEqualTo(CredentialSchema.PHONE_NUMBER)
        Assertions.assertThat(verifiableCredential.credentialSubject)
          .isEqualTo(
            CredentialSubject.PhoneNumber(
              expectedId,
              userIdentifier!!.value,
              TestData.LAST_VERIFIED,
            )
          )
      }
    }
  }

  @Test
  fun getDidJson() {
    // GIVEN
    whenever(applicationKeyCodec.encode(TestConfig.APPLICATION_KEY_PAIR.publicKey)).thenReturn(TestData.ENCODED_APPLICATION_PUBLIC_KEY)

    whenever(didJsonTemplateRenderer.render(argThat { context ->
      Assertions.assertThat(context["hostName"]).isEqualTo(TestConfig.HOST_NAME)
      Assertions.assertThat(context["encodedPublicKey"]).isEqualTo(TestData.ENCODED_APPLICATION_PUBLIC_KEY)
      true
    })).thenReturn(TestData.DID_JSON)

    // WHEN
    val service = DefaultVerifiableCredentialService(
      didJsonTemplateRenderer,
      TestConfig.HOST_NAME,
      TestConfig.APPLICATION_KEY_PAIR,
      authenticator,
      applicationKeyCodec,
      userKeyCodec,
      userKeyCodec,
      Duration.ofSeconds(0)
    )
    val didJson = service.getIssuerDidJson()

    // THEN
    Assertions.assertThat(didJson).isEqualTo(TestData.DID_JSON)
  }

}
