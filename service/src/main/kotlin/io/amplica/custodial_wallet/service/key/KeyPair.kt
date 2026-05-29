package io.amplica.custodial_wallet.service.key

import io.amplica.custodial_wallet.util.key_creation.PrivateKeyBytes
import io.amplica.custodial_wallet.util.key_creation.PublicKeyBytes

data class KeyPair(
  val publicKey: PublicKeyBytes,
  val privateKey: PrivateKeyBytes
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is KeyPair) return false

    if (!publicKey.contentEquals(other.publicKey)) return false
    if (!privateKey.contentEquals(other.privateKey)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = publicKey.contentHashCode()
    result = 31 * result + privateKey.contentHashCode()
    return result
  }
}
