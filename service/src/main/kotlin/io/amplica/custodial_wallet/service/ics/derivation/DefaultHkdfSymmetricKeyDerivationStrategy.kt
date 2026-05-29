package io.amplica.custodial_wallet.service.ics.derivation

import com.google.crypto.tink.subtle.Hkdf

/**
 * Transparent wrapper around Tink's `Hkdf`
 */
class DefaultHkdfSymmetricKeyDerivationStrategy(private val size: Int) : KeyPathDerivationStrategy<ByteArray> {

  override fun deriveFromSeedForPath(seed: ByteArray, path: String): ByteArray {
    return Hkdf.computeHkdf(
      "HMACSHA256",
      seed,
      null,
      path.toByteArray(Charsets.UTF_8),
      size
    )
  }

}