package io.amplica.custodial_wallet.service.key

import io.amplica.custodial_wallet.EncryptedKeyData
import io.amplica.custodial_wallet.client.kms.EncryptedData
import io.amplica.custodial_wallet.client.kms.EncryptedKey
import io.amplica.custodial_wallet.db.repository.UserKeyData
import io.amplica.custodial_wallet.util.key_creation.KeyPairBytes
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto
import io.amplica.custodial_wallet.util.key_creation.X25519KeyPair


data class GeneratedKeyPairData<out T>(
  val publicKey: PublicKeyDto,
  val keyPair: T,
  val encryptedKeyData: EncryptedKeyData
)

data class GeneratedKeyPairsBundle(
  val account: GeneratedKeyPairData<KeyPairBytes>,
  val graph: GeneratedKeyPairData<X25519KeyPair>?
)

interface KeyService {
  suspend fun generateAccountKeyPair(keyPairType: KeyPairType): GeneratedKeyPairData<KeyPairBytes>
  suspend fun generateGraphKeyPair(): GeneratedKeyPairData<X25519KeyPair>
  suspend fun generateAccountAndGraphKeyPairs(keyPairType: KeyPairType, generateGraphKey: Boolean): GeneratedKeyPairsBundle
  suspend fun encryptData(data: ByteArray): EncryptedData
  suspend fun encryptPrivateKey(privateKey: ByteArray): EncryptedKey
  suspend fun decryptUserAccountKeyData(userKeyData: UserKeyData): KeyPairBytes
  suspend fun decryptUserGraphKeyData(userKeyData: UserKeyData): X25519KeyPair
  suspend fun decryptUserIcsKeyData(userKeyData: UserKeyData): KeyPairBytes
  suspend fun decryptData(encryptedData: EncryptedData): ByteArray
}