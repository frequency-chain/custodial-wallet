package io.amplica.custodial_wallet.util.key_creation

import io.amplica.frequency.util.toHex
import org.assertj.core.api.Assertions
import org.bouncycastle.util.BigIntegers.asUnsignedByteArray
import org.junit.jupiter.api.Test
import org.web3j.crypto.Bip32ECKeyPair

class Secp256K1KeyPairCreatorTest {

  @Test
  fun createKeyPair() {
    // WHEN
    val keyPair = Secp256k1KeyPairCreator.createKeyPair()

    // THEN
    Assertions.assertThat(keyPair.privateKeyBytes).hasSize(32)
    Assertions.assertThat(keyPair.publicKeyBytes).hasSize(64)

    // Validate the public key can be derived from the private key (using Web3j)
    val derivedKeyPair = Bip32ECKeyPair.create(keyPair.privateKeyBytes)
    Assertions.assertThat(toHex(keyPair.publicKeyBytes))
      .isEqualTo(toHex(asUnsignedByteArray(64, derivedKeyPair.publicKey)))
  }

}