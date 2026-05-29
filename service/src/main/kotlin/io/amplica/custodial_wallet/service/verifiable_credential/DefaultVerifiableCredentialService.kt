package io.amplica.custodial_wallet.service.verifiable_credential

import io.amplica.custodial_wallet.client.redis.dto.UserIdentifier
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifierType
import io.amplica.custodial_wallet.template.TemplateRenderer
import io.amplica.custodial_wallet.util.key_creation.KeyPairBytes
import io.amplica.custodial_wallet.util.key_creation.KeyPairSignatureAlgorithm
import io.amplica.custodial_wallet.util.key_creation.X25519KeyPair
import io.amplica.custodial_wallet.util.toHex
import io.amplica.custodial_wallet.verifiablecredentials.VerifiableCredentialAuthenticator
import io.amplica.custodial_wallet.verifiablecredentials.codec.MultikeyCodec
import io.amplica.custodial_wallet.verifiablecredentials.crypto.KeyPair
import io.amplica.custodial_wallet.verifiablecredentials.cryptosuite.EddsaRdfc2022
import io.amplica.custodial_wallet.verifiablecredentials.dto.*
import io.amplica.frequency.crypto.provider.Secp256K1CryptoProvider
import io.amplica.frequency.crypto.toPublicKeyBytes
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

object DidMethod {
  const val WEB = "did:web"
  const val KEY = "did:key"
}

class DefaultVerifiableCredentialService(
  private val didJsonTemplateRenderer: TemplateRenderer,
  private val hostName: String,
  private val applicationEd25519KeyPair: KeyPair,
  private val verifiableCredentialAuthenticator: VerifiableCredentialAuthenticator,
  ed25519MultikeyCodec: MultikeyCodec,
  private val sr25519MultikeyCodec: MultikeyCodec,
  private val ecdsaMultikeyCodec: MultikeyCodec,
  private val validFromDelay: Duration,
) : VerifiableCredentialService {

  companion object {
    val CONTEXT = listOf(
      "https://www.w3.org/ns/credentials/v2",
      "https://www.w3.org/ns/credentials/undefined-terms/v2"
    )
  }

  private val issuer = "${DidMethod.WEB}:$hostName"

  private val encodedApplicationPublicKey = ed25519MultikeyCodec.encode(applicationEd25519KeyPair.publicKey)
  private val verificationMethod = "$issuer#$encodedApplicationPublicKey"
  private val proofOptions = ProofOptions(
    ProofType.DataIntegrityProof,
    verificationMethod,
    EddsaRdfc2022.NAME,
    ProofPurpose.AssertionMethod
  )

  private fun createSubjectId(accountKeyPair: KeyPairBytes): String {
    val encodedAccountPublicKey = when (accountKeyPair.algo) {
      KeyPairSignatureAlgorithm.SR25519 -> sr25519MultikeyCodec.encode(accountKeyPair.publicKeyBytes)
      KeyPairSignatureAlgorithm.ECDSA -> {
        val compressedKeyPairBytes = Secp256K1CryptoProvider.compressPublicKey(
          accountKeyPair.publicKeyBytes.toPublicKeyBytes()
        ).bytes
        
        ecdsaMultikeyCodec.encode(compressedKeyPairBytes)
      }
      else -> throw IllegalArgumentException("KeyPairSignatureAlgorithm not supported: ${accountKeyPair.algo}")
    }

    return "${DidMethod.KEY}:$encodedAccountPublicKey"
  }

  override fun getIssuerDidJson(): String {
    val context = mapOf(
      "hostName" to hostName,
      "encodedPublicKey" to encodedApplicationPublicKey,
    )

    return didJsonTemplateRenderer.render(context)
  }

  override fun createVerifiableCredential(
    accountKeyPair: KeyPairBytes,
    graphKeyPair: X25519KeyPair
  ): VerifiableCredential {
    return createCredential(
      CredentialSubject.KeyPair(
        id = createSubjectId(accountKeyPair),
        toHex(graphKeyPair.publicKeyBytes),
        toHex(graphKeyPair.privateKeyBytes),
        KeyPairEncoding.BASE_16,
        KeyPairFormat.BARE,
        KeyPairType.X25519,
        DsnpKeyType.PublicKeyKeyAgreement,
      ),
      listOf(
        CredentialType.VerifiedGraphKeyCredential,
        CredentialType.VerifiableCredential
      )
    )
  }

  override fun createVerifiableCredential(
    accountKeyPair: KeyPairBytes,
    userIdentifier: UserIdentifier,
    lastVerified: ZonedDateTime
  ): VerifiableCredential {
    return when (userIdentifier.type) {
      UserIdentifierType.PHONE_NUMBER -> createCredential(
        CredentialSubject.PhoneNumber(
          id = createSubjectId(accountKeyPair),
          phoneNumber = userIdentifier.value,
          lastVerified = lastVerified
        ),
        listOf(
          CredentialType.VerifiedPhoneNumberCredential,
          CredentialType.VerifiableCredential
        )
      )

      UserIdentifierType.EMAIL -> createCredential(
        CredentialSubject.Email(
          id = createSubjectId(accountKeyPair),
          emailAddress = userIdentifier.value,
          lastVerified = lastVerified
        ), listOf(
          CredentialType.VerifiedEmailAddressCredential,
          CredentialType.VerifiableCredential
        )
      )
    }
  }

  private fun createCredential(credentialSubject: CredentialSubject, type: List<CredentialType>): VerifiableCredential {
    val credentialSchema = when (credentialSubject) {
      is CredentialSubject.Email -> CredentialSchema.EMAIL_ADDRESS
      is CredentialSubject.KeyPair -> CredentialSchema.KEY_PAIR
      is CredentialSubject.PhoneNumber -> CredentialSchema.PHONE_NUMBER
    }

    val credential = Credential(
      context = CONTEXT,
      type = type,
      issuer = issuer,
      validFrom = ZonedDateTime.ofInstant(Instant.now().minus(validFromDelay), ZoneId.of("UTC")),
      credentialSchema = credentialSchema,
      credentialSubject = credentialSubject,
    )

    return verifiableCredentialAuthenticator.sign(proofOptions, credential, applicationEd25519KeyPair.privateKey)
  }

}