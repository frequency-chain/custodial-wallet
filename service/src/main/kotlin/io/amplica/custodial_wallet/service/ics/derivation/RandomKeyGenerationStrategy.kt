package io.amplica.custodial_wallet.service.ics.derivation

/**
 * Generates a (securely) random secret key
 */
interface RandomKeyGenerationStrategy {
  fun generateRandomKey(): ByteArray
}