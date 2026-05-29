package io.amplica.custodial_wallet.util.key_creation

import io.amplica.frequency.crypto.provider.Secp256K1CryptoProvider

class Secp256k1KeyPairBytes(publicKeyBytes: ByteArray, privateKeyBytes: ByteArray) :
  KeyPairBytes(publicKeyBytes, privateKeyBytes, KeyPairType.SECP256K1, KeyPairSignatureAlgorithm.ECDSA)

object Secp256k1KeyPairCreator : KeyPairCreator {

  @Deprecated("Use Secp256k1CryptoProvider instead", ReplaceWith("Secp256K1CryptoProvider.createKeyPair()"))
  override fun createKeyPair(): Secp256k1KeyPairBytes {
    val keyPair = Secp256K1CryptoProvider.createKeyPair()

    return Secp256k1KeyPairBytes(keyPair.publicKeyBytes.bytes, keyPair.privateKeyBytes.bytes)
  }
}
