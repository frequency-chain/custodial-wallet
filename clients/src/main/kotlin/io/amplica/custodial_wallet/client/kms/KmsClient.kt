package io.amplica.custodial_wallet.client.kms

import com.google.common.collect.FluentIterable
import java.util.*

data class EncryptedKey(
  val encryptedValue: ByteArray,
  val kmsDecryptionKey: KmsDecryptionKey
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is EncryptedKey) return false

    if (!encryptedValue.contentEquals(other.encryptedValue)) return false
    if (kmsDecryptionKey != other.kmsDecryptionKey) return false

    return true
  }

  override fun hashCode(): Int {
    var result = encryptedValue.contentHashCode()
    result = 31 * result + kmsDecryptionKey.hashCode()
    return result
  }
}

data class KmsEncryptionKey(
  val keyAlias: String,
  val kmsEncryptionAlgorithm: KmsEncryptionAlgorithm
)

data class KmsDecryptionKey(
  val decryptionKeyId: String,
  val decryptionAlgorithm: KmsEncryptionAlgorithm
)

data class KeyDescription(
  val keyId: String,
  val description: String,
  val arn: String,
  val keyState: String
)

data class EncryptedData(
  val value: ByteArray,
  val kmsDecryptionKey: KmsDecryptionKey
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is EncryptedData) return false

    if (!value.contentEquals(other.value)) return false
    if (kmsDecryptionKey != other.kmsDecryptionKey) return false

    return true
  }

  override fun hashCode(): Int {
    var result = value.contentHashCode()
    result = 31 * result + kmsDecryptionKey.hashCode()
    return result
  }
}

enum class KmsEncryptionAlgorithm(private val algorithm: String){
  SYMMETRIC_DEFAULT("SYMMETRIC_DEFAULT");

  companion object {
    @Suppress("UnstableApiUsage") //from is marked @Beta but its been beta for years
    private val ALGORITHM_INDEX: Map<String, KmsEncryptionAlgorithm> = FluentIterable.from(values()).uniqueIndex { it.algorithm.uppercase(
      Locale.US) }

    fun fromAlgorithm(algorithm: String): KmsEncryptionAlgorithm {
      return ALGORITHM_INDEX[algorithm.uppercase(Locale.US)]
        ?: throw IllegalArgumentException("KMS Encryption Algorithm=${algorithm} is not recognized")
    }
  }
}

data class ListOfAliasNames(val aliasNames: List<String>)

interface KmsClient {
  suspend fun encryptPrivateKey(privateKey: ByteArray, kmsEncryptionKey: KmsEncryptionKey): EncryptedKey
  suspend fun decryptPrivateKey(encryptedKey: EncryptedKey): ByteArray
  suspend fun encryptData(data: ByteArray, kmsEncryptionKey: KmsEncryptionKey): EncryptedData
  suspend fun decryptData(encryptedData: EncryptedData): ByteArray
  suspend fun listAliasNames(): ListOfAliasNames
  suspend fun describeKey(): KeyDescription
  suspend fun healthcheck(): Boolean
}