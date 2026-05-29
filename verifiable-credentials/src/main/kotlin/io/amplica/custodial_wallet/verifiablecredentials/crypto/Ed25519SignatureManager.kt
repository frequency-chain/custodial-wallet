package io.amplica.custodial_wallet.verifiablecredentials.crypto

import com.google.crypto.tink.subtle.Ed25519Sign
import com.google.crypto.tink.subtle.Ed25519Verify


/**
 * Signs data and verifies signatures using Ed25519.
 *
 * Uses Google's [Tink](https://developers.google.com/tink) library.
 *
 * Links:
 * - [Tink JavaDoc](https://tink-crypto.github.io/tink-java/javadoc/tink/1.15.0/)
 */
object Ed25519SignatureManager : SignatureManager {

  override fun sign(privateKey: ByteArray, data: ByteArray): ByteArray {
    return Ed25519Sign(privateKey).sign(data)
  }

  override fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean {
    val result = kotlin.runCatching {
      Ed25519Verify(publicKey).verify(signature, data)
    }

    return result.isSuccess
  }

  fun newKeyPair(): KeyPair {
    val keyPair = Ed25519Sign.KeyPair.newKeyPair()

    return KeyPair(
      keyPair.publicKey,
      keyPair.privateKey
    )
  }

  fun newKeyPairFromSeed(seed: ByteArray): KeyPair {
    val keyPair = Ed25519Sign.KeyPair.newKeyPairFromSeed(seed)

    return KeyPair(
      keyPair.publicKey,
      keyPair.privateKey
    )
  }

}
