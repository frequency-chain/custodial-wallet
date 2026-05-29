package io.amplica.custodial_wallet.verifiablecredentials.cryptosuite


import io.amplica.custodial_wallet.verifiablecredentials.ProofGenerationException
import io.amplica.custodial_wallet.verifiablecredentials.canonicalization.Canonicalizer
import io.amplica.custodial_wallet.verifiablecredentials.codec.Codec
import io.amplica.custodial_wallet.verifiablecredentials.crypto.SignatureCreator
import io.amplica.custodial_wallet.verifiablecredentials.crypto.SignatureVerifier
import io.amplica.custodial_wallet.verifiablecredentials.dto.Credential
import io.amplica.custodial_wallet.verifiablecredentials.dto.ProofOptions
import io.amplica.custodial_wallet.verifiablecredentials.dto.ProofType
import io.amplica.custodial_wallet.verifiablecredentials.dto.VerifiableCredential
import java.nio.charset.StandardCharsets
import java.security.MessageDigest


/**
 * A 'cryptographic suite' for verifying the integrity of data (i.e., creating and verifying digital signatures).
 *
 * This is an implementation of the `eddsa-rdfc-2022` algorithm described in the
 * [W3C EdDSA data integrity specification](https://www.w3.org/TR/vc-di-eddsa/#eddsa-rdfc-2022).
 *
 * The general process is:
 * 1) Transform the `options` and `credential` into 'canonical' (i.e., un-ambiguous and deterministic) strings
 * 2) Serialize to bytes and hash using SHA-256 (for each)
 * 3) Concatenate the `options` hash with the `credential` hash
 * 4) Sign or verify the result using the Ed25519 private/public key
 */
class EddsaRdfc2022(
  private val rdfCanonicalizer: Canonicalizer,
  private val base58MultibaseCodec: Codec<ByteArray, String>,
  private val ed25519SignatureCreator: SignatureCreator,
  private val ed25519SignatureVerifier: SignatureVerifier,
) : CryptoSuite {

  companion object {
    const val NAME = "eddsa-rdfc-2022"
  }

  override val name = NAME

  // See Steps 2-3 https://www.w3.org/TR/vc-di-eddsa/#transformation-eddsa-rdfc-2022
  fun getTransformedDocument(credential: Credential): ByteArray {
    val canonicalDocument = rdfCanonicalizer.serialize(credential)
    return canonicalDocument.toByteArray(StandardCharsets.UTF_8)
  }

  // See https://www.w3.org/TR/vc-di-eddsa/#proof-configuration-eddsa-rdfc-2022
  fun getCanonicalProofConfig(options: ProofOptions): ByteArray {
    if (options.type != ProofType.DataIntegrityProof) {
      throw ProofGenerationException("Proof type must be '${ProofType.DataIntegrityProof.value}'")
    }

    val canonicalDocument = rdfCanonicalizer.serialize(options)
    return canonicalDocument.toByteArray(StandardCharsets.UTF_8)
  }

  // See: https://www.w3.org/TR/vc-di-eddsa/#hashing-eddsa-rdfc-2022
  fun sha256Digest(inputBytes: ByteArray): ByteArray {
    val sha256 = MessageDigest.getInstance("SHA-256")
    return sha256.digest(inputBytes)
  }

  // See: https://www.w3.org/TR/vc-di-eddsa/#hashing-eddsa-rdfc-2022
  private fun getHashData(options: ProofOptions, credential: Credential): ByteArray {
    val proofConfigHash = sha256Digest(getCanonicalProofConfig(options))
    val transformedDocumentHash = sha256Digest(getTransformedDocument(credential))

    // Concatenate the serialized options hash with the serialized document hash
    return proofConfigHash + transformedDocumentHash
  }

  override fun sign(options: ProofOptions, credential: Credential, privateKey: ByteArray): String {
    val hashData = getHashData(options, credential)
    val proofBytes = ed25519SignatureCreator.sign(privateKey, hashData)

    return base58MultibaseCodec.encode(proofBytes)
  }

  override fun verify(verifiableCredential: VerifiableCredential, publicKey: ByteArray): Boolean {
    val credential = verifiableCredential.withoutProof()
    val options = verifiableCredential.proof.asOptions()
    val signature = verifiableCredential.proof.proofValue

    val hashData = getHashData(options, credential)

    return ed25519SignatureVerifier.verify(publicKey, hashData, base58MultibaseCodec.decode(signature))
  }

}
