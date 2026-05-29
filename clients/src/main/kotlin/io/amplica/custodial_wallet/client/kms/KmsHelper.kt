package io.amplica.custodial_wallet.client.kms

import software.amazon.awssdk.services.kms.model.EncryptionAlgorithmSpec

data class EncryptRequest(val keyId: String, val plainText: ByteArray, val algorithm: EncryptionAlgorithmSpec) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is EncryptRequest) return false

    if (keyId != other.keyId) return false
    if (!plainText.contentEquals(other.plainText)) return false
    if (algorithm != other.algorithm) return false

    return true
  }

  override fun hashCode(): Int {
    var result = keyId.hashCode()
    result = 31 * result + plainText.contentHashCode()
    result = 31 * result + algorithm.hashCode()
    return result
  }
}

data class EncryptResponse(val keyId: String, val cipherText: ByteArray, val algorithm: EncryptionAlgorithmSpec) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is EncryptResponse) return false

    if (keyId != other.keyId) return false
    if (!cipherText.contentEquals(other.cipherText)) return false
    if (algorithm != other.algorithm) return false

    return true
  }

  override fun hashCode(): Int {
    var result = keyId.hashCode()
    result = 31 * result + cipherText.contentHashCode()
    result = 31 * result + algorithm.hashCode()
    return result
  }
}

data class DecryptRequest(val keyId: String, val cipherText: ByteArray, val algorithm: EncryptionAlgorithmSpec) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DecryptRequest) return false

    if (keyId != other.keyId) return false
    if (!cipherText.contentEquals(other.cipherText)) return false
    if (algorithm != other.algorithm) return false

    return true
  }

  override fun hashCode(): Int {
    var result = keyId.hashCode()
    result = 31 * result + cipherText.contentHashCode()
    result = 31 * result + algorithm.hashCode()
    return result
  }
}

data class DecryptResponse(val keyId: String, val plainText: ByteArray, val algorithm: EncryptionAlgorithmSpec) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DecryptResponse) return false

    if (keyId != other.keyId) return false
    if (!plainText.contentEquals(other.plainText)) return false
    if (algorithm != other.algorithm) return false

    return true
  }

  override fun hashCode(): Int {
    var result = keyId.hashCode()
    result = 31 * result + plainText.contentHashCode()
    result = 31 * result + algorithm.hashCode()
    return result
  }
}