package io.amplica.custodial_wallet.service.ics.crypto

interface Cipher {
  fun encrypt(message: ByteArray, secretKey: ByteArray): ByteArray
  fun decrypt(cipherText: ByteArray, secretKey: ByteArray): ByteArray
}
