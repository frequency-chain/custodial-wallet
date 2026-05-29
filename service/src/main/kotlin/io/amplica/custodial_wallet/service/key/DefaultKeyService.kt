package io.amplica.custodial_wallet.service.key

import com.strategyobject.substrateclient.crypto.ss58.SS58AddressFormat
import io.amplica.custodial_wallet.EncryptedKeyData
import io.amplica.custodial_wallet.client.kms.*
import io.amplica.custodial_wallet.db.repository.KeyUsageType
import io.amplica.custodial_wallet.db.repository.UserKeyData
import io.amplica.custodial_wallet.util.fromHex
import io.amplica.custodial_wallet.util.key_creation.*
import io.amplica.frequency.util.GraphHelper

class DefaultKeyService(
  private val kmsClient: KmsClient,
  private val graphHelper: GraphHelper,
  private val ss58AddressFormat: SS58AddressFormat,
  kmsKeyAlias: String,
) : KeyService {

  private val kmsEncryptionKey = KmsEncryptionKey(kmsKeyAlias, KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)

  override suspend fun generateAccountKeyPair(keyPairType: KeyPairType): GeneratedKeyPairData<KeyPairBytes> {
    val accountKeyPairBytes = when (keyPairType) {
      KeyPairType.SR25519 -> Sr25519KeyPairCreator.createKeyPair()
      KeyPairType.SECP256K1 -> Secp256k1KeyPairCreator.createKeyPair()
      else -> throw IllegalArgumentException("KeyPairType $keyPairType is not supported as an account key")
    }
    val publicKeyDto = accountKeyPairBytes.toPublicKeyDto(ss58AddressFormat)

    return GeneratedKeyPairData(
      publicKeyDto,
      accountKeyPairBytes,
      EncryptedKeyData(
        accountKeyPairBytes.publicKeyBytes,
        kmsClient.encryptPrivateKey(accountKeyPairBytes.privateKeyBytes, kmsEncryptionKey),
        accountKeyPairBytes.keyPairType,
        KeyUsageType.ACCOUNT,
      )
    )
  }

  override suspend fun generateGraphKeyPair(): GeneratedKeyPairData<X25519KeyPair> {
    val graphKeyPair = X25519KeyPairCreator.createKeyPair()

    return GeneratedKeyPairData(
      X25519KeyPairCreator.createX25519PublicKeyDtoDsnpFormat(graphHelper, graphKeyPair),
      graphKeyPair,
      EncryptedKeyData(
        graphKeyPair.publicKeyBytes,
        kmsClient.encryptPrivateKey(graphKeyPair.privateKeyBytes, kmsEncryptionKey),
        KeyPairType.X25519,
        KeyUsageType.GRAPH
      )
    )
  }

  override suspend fun generateAccountAndGraphKeyPairs(
    keyPairType: KeyPairType,
    generateGraphKey: Boolean
  ): GeneratedKeyPairsBundle {
    val graphKeyPairData = if (generateGraphKey) {
      generateGraphKeyPair()
    } else null

    return GeneratedKeyPairsBundle(generateAccountKeyPair(keyPairType), graphKeyPairData)
  }

  override suspend fun encryptData(data: ByteArray): EncryptedData {
    return kmsClient.encryptData(data, kmsEncryptionKey)
  }

  override suspend fun encryptPrivateKey(privateKey: ByteArray): EncryptedKey {
    return kmsClient.encryptPrivateKey(privateKey, kmsEncryptionKey)
  }

  private suspend fun decryptUserKeyData(userKeyData: UserKeyData): KeyPair {
    val publicKey = fromHex(userKeyData.publicKeyHex)

    val encryptedPrivateKey = EncryptedKey(
      fromHex(userKeyData.encryptedPrivateKeyHex),
      KmsDecryptionKey(userKeyData.kmsEncryptionKeyId, userKeyData.kmsEncryptionKeyIdType),
    )
    val privateKey = kmsClient.decryptPrivateKey(encryptedPrivateKey)

    return KeyPair(publicKey, privateKey)
  }

  override suspend fun decryptUserAccountKeyData(userKeyData: UserKeyData): KeyPairBytes {
    if (userKeyData.keyUsageType != KeyUsageType.ACCOUNT) {
      throw IllegalArgumentException("UserKeyData must have keyUsageType=KeyUsageType.ACCOUNT")
    }

    val decryptedKeyPair = decryptUserKeyData(userKeyData)

    return when (userKeyData.encryptedPrivateKeyType) {
      KeyPairType.SR25519 -> Sr25519KeyPairBytes(decryptedKeyPair.publicKey, decryptedKeyPair.privateKey)
      KeyPairType.SECP256K1 -> Secp256k1KeyPairBytes(decryptedKeyPair.publicKey, decryptedKeyPair.privateKey)
      else -> throw IllegalArgumentException("KeyPairType not supported: ${userKeyData.encryptedPrivateKeyType}")
    }
  }

  override suspend fun decryptUserGraphKeyData(userKeyData: UserKeyData): X25519KeyPair {
    if (userKeyData.keyUsageType != KeyUsageType.GRAPH) {
      throw IllegalArgumentException("UserKeyData must have keyUsageType=KeyUsageType.GRAPH")
    }

    val decryptedKeyPair = decryptUserKeyData(userKeyData)

    return X25519KeyPair(decryptedKeyPair.publicKey, decryptedKeyPair.privateKey)
  }

  override suspend fun decryptUserIcsKeyData(userKeyData: UserKeyData): KeyPairBytes {
    if (userKeyData.keyUsageType != KeyUsageType.ICS) {
      throw IllegalArgumentException("UserKeyData must have keyUsageType=KeyUsageType.ICS")
    }

    val (publicKey, privateKey) = decryptUserKeyData(userKeyData)

    return when (userKeyData.encryptedPrivateKeyType) {
      KeyPairType.ED25519 -> Ed25519KeyPairBytes(publicKey, privateKey)
      else -> throw IllegalArgumentException("KeyPairType not supported: ${userKeyData.encryptedPrivateKeyType}")
    }
  }

  override suspend fun decryptData(encryptedData: EncryptedData): ByteArray {
    return kmsClient.decryptData(encryptedData)
  }
}