package io.amplica.custodial_wallet.service.ics.derivation

/**
 * Derives a secret key from some input `seed` and a `path`
 */
interface KeyPathDerivationStrategy<T> {
  fun deriveFromSeedForPath(seed: ByteArray, path: String): T
}