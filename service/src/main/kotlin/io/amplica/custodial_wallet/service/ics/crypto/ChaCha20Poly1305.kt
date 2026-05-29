package io.amplica.custodial_wallet.service.ics.crypto

import com.google.crypto.tink.subtle.ChaCha20Poly1305

object ChaCha20Poly1305 : Cipher {
  override fun encrypt(message: ByteArray, secretKey: ByteArray): ByteArray {
    return ChaCha20Poly1305(secretKey).encrypt(message, null)
  }

  override fun decrypt(cipherText: ByteArray, secretKey: ByteArray): ByteArray {
    return ChaCha20Poly1305(secretKey).decrypt(cipherText, null)
  }
}
