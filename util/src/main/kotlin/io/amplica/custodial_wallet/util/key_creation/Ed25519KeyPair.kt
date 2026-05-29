package io.amplica.custodial_wallet.util.key_creation

data class Ed25519KeyPairBytes(
  override val publicKeyBytes: ByteArray,
  override val privateKeyBytes: ByteArray
) : KeyPairBytes(
  publicKeyBytes,
  privateKeyBytes,
  KeyPairType.ED25519,
  KeyPairSignatureAlgorithm.ED25519,
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Ed25519KeyPairBytes) return false
    if (!super.equals(other)) return false

    if (!publicKeyBytes.contentEquals(other.publicKeyBytes)) return false
    if (!privateKeyBytes.contentEquals(other.privateKeyBytes)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + publicKeyBytes.contentHashCode()
    result = 31 * result + privateKeyBytes.contentHashCode()
    return result
  }

}
