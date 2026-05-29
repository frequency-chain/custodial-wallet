package io.amplica.custodial_wallet.service.ics.crypto

interface CipherWithNonce {
  fun encrypt(nonce: ByteArray, message: ByteArray, secretKey: ByteArray): ByteArray
  fun decrypt(nonce: ByteArray, cipherText: ByteArray, secretKey: ByteArray): ByteArray
}
