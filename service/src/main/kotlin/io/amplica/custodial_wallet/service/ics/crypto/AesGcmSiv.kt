package io.amplica.custodial_wallet.service.ics.crypto

import com.codahale.aesgcmsiv.AEAD

object AesGcmSiv : CipherWithNonce {
  override fun encrypt(nonce: ByteArray, message: ByteArray, secretKey: ByteArray): ByteArray {
    return AEAD(secretKey).seal(message, nonce)
  }

  override fun decrypt(nonce: ByteArray, cipherText: ByteArray, secretKey: ByteArray): ByteArray {
    return AEAD(secretKey).open(cipherText, nonce)
      .orElseThrow { Exception("AES-GCM-SIV decrypt failed") }
  }
}
