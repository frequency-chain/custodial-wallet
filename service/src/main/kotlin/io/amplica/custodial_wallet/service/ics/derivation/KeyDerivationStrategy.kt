package io.amplica.custodial_wallet.service.ics.derivation

/**
 * Derives a secret key from some input `seed`
 */
interface KeyDerivationStrategy<T> {
  fun deriveFromSeed(seed: ByteArray): T
}
