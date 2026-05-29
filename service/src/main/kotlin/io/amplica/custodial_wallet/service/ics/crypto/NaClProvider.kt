package io.amplica.custodial_wallet.service.ics.crypto

@Suppress("ArrayInDataClass")
data class AuthenticatedCipherText(
  val cipherText: ByteArray,
  val authenticationTag: ByteArray
)

@Suppress("ArrayInDataClass")
data class KeyPair(
  val publicKey: ByteArray,
  val secretKey: ByteArray
)

/**
 * A tiny subset of the 'NaCl' library API necessary for implementing PRId derivation
 */
interface NaClProvider {
  /**
   * Establishes a shared secret key from 2 X25519 key pairs where only one secret key is known.
   *
   * Performs X25519 exchange to get a shared secret, then derives a key from that shared secret using HSalsa20.
   *
   * See: https://libsodium.gitbook.io/doc/public-key_cryptography/authenticated_encryption#precalculation-interface
   */
  fun cryptoBoxBeforeNm(secretKey: ByteArray, publicKey: ByteArray): ByteArray

  /**
   *  Derives a 'sub-key' of the 'master key' with the given 'id', taking into account the context (i.e., domain)
   *
   *  `context` is an 8-character string describing what the key is going to be used for, intended to mitigate
   *  bugs by separating domains. Contexts don’t have to be secret and can have a low entropy.
   *
   *  NOTE: Given the master key and a key identifier, a subkey can be deterministically computed.
   *  However, given a subkey, an attacker cannot compute the master key nor any other subkeys.
   *
   *  See: https://libsodium.gitbook.io/doc/key_derivation#deriving-keys-from-a-single-high-entropy-key
   */
  fun cryptoKdfDeriveFromKey(masterKey: ByteArray, context: ByteArray, subKeyId: Long, subKeyLength: Int): ByteArray

  // Convenience for when `context` is a `String`
  fun cryptoKdfDeriveFromKey(masterKey: ByteArray, context: String, subKeyId: Long, subKeyLength: Int): ByteArray {
    return cryptoKdfDeriveFromKey(masterKey, context.toByteArray(Charsets.UTF_8), subKeyId, subKeyLength)
  }

  /**
   * Encrypts the `message` using the `nonce` and (secret) `key`.
   *
   * See: https://doc.libsodium.org/secret-key_cryptography/secretbox#detached-mode
   */
  fun cryptoSecretBoxDetached(message: ByteArray, nonce: ByteArray, key: ByteArray): AuthenticatedCipherText

  /**
   * Returns a pair containing
   */
  fun cryptoKxSeedKeypair(seed: ByteArray): KeyPair

  /**
   * Converts an Ed25519 public key to an X25519 public key
   */
  fun cryptoSignEd25519PkToCurve25519(ed25519PublicKey: ByteArray): ByteArray

  /**
   * Converts an Ed25519 secret key to an X25519 secret key
   */
  fun cryptoSignEd25519SkToCurve25519(ed25519SecretKey: ByteArray): ByteArray
}
