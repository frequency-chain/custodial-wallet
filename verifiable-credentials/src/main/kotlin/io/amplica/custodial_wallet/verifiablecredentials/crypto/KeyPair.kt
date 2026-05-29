package io.amplica.custodial_wallet.verifiablecredentials.crypto

import com.google.crypto.tink.subtle.Hex


class KeyPair(
  val publicKey: ByteArray,
  val privateKey: ByteArray,
) {
  override fun toString(): String {
    return "KeyPair(publicKey=${Hex.encode(publicKey)}, privateKey=${Hex.encode(privateKey)})"
  }
}
