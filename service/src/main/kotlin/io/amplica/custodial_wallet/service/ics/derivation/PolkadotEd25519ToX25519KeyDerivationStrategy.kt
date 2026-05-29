package io.amplica.custodial_wallet.service.ics.derivation

import io.amplica.custodial_wallet.service.ics.crypto.NaClProvider
import io.amplica.custodial_wallet.util.key_creation.X25519KeyPair

class PolkadotEd25519ToX25519KeyDerivationStrategy(
  private val naClProvider: NaClProvider
) : KeyDerivationStrategy<X25519KeyPair> {

  override fun deriveFromSeed(seed: ByteArray): X25519KeyPair {
    val ed25519SecretKey = TODO("Not yet implemented: Awaiting algorithm definition from Aramik")
    val ed25519PublicKey = TODO("Not yet implemented")

    val x25519SecretKey = naClProvider.cryptoSignEd25519SkToCurve25519(ed25519SecretKey)
    val x25519PublicKey = naClProvider.cryptoSignEd25519PkToCurve25519(ed25519PublicKey)

    return X25519KeyPair(x25519SecretKey, x25519PublicKey)
  }

}
