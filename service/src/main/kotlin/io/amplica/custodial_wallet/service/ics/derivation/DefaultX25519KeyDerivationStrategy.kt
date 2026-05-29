package io.amplica.custodial_wallet.service.ics.derivation

import io.amplica.custodial_wallet.service.ics.crypto.NaClProvider
import io.amplica.custodial_wallet.util.key_creation.X25519KeyPair


class DefaultX25519KeyDerivationStrategy(private val naClProvider: NaClProvider) : KeyDerivationStrategy<X25519KeyPair> {

  override fun deriveFromSeed(seed: ByteArray): X25519KeyPair {
    val keyPair = naClProvider.cryptoKxSeedKeypair(seed)

    return X25519KeyPair(keyPair.publicKey, keyPair.secretKey)
  }

}