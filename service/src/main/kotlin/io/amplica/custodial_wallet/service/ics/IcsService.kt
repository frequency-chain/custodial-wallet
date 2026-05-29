package io.amplica.custodial_wallet.service.ics

import io.amplica.custodial_wallet.util.toHex
import java.math.BigInteger

data class Derived<V>(
  val value: V,
  val path: String,
)

enum class IcsKeyType {
  ED25519
}

data class IcsKeyPair(
  val publicKey: ByteArray,
  val privateKey: ByteArray,
  val type: IcsKeyType,
) {
  fun toPublicKey(): IcsPublicKey = IcsPublicKey(this.publicKey, this.type)
}

data class IcsPublicKey(
  val publicKey: ByteArray,
  val type: IcsKeyType,
)

data class IcsEncryptionResult(
  val data: ByteArray,
  val nonce: ByteArray,
)

data class ContextGroupAcl(
  val contextGroupId: ByteArray,
  val keyId: Int,
  val nonce: ByteArray,
  val encryptedProviderId: ByteArray
)

interface IcsService {
  fun generateMasterMnemonicSeedPhrase(): String

  fun deriveMasterSeed(mnemonicPhrase: String): ByteArray

  fun deriveMasterKeyPair(userMasterSeed: ByteArray): IcsKeyPair

  fun deriveUserChainDataSymmetricKey(seed: ByteArray): Derived<ByteArray>

  fun deriveContextGroupId(
    userMsaId: BigInteger,
    providerMsaId: BigInteger,
    keyPair: IcsKeyPair,
    publicKey: IcsPublicKey,
  ): ByteArray

  // Convenience implementation
  fun deriveContextGroupIdHex(
    userMsaId: BigInteger,
    providerMsaId: BigInteger,
    keyPair: IcsKeyPair,
    publicKey: IcsPublicKey,
  ): String {
    return toHex(
      deriveContextGroupId(userMsaId, providerMsaId, keyPair, publicKey)
    )
  }

  fun deriveContextGroupSymmetricKey(
    seed: ByteArray,
    contextGroupId: ByteArray,
  ): Derived<ByteArray>

  fun deriveContextItemSymmetricKey(
    seed: ByteArray,
    contextItemId: String,
    contextItemTag: String
  ): Derived<ByteArray>

  fun encrypt(
    symmetricKey: ByteArray,
    message: ByteArray,
    nonce: ByteArray,
  ): IcsEncryptionResult

  fun encrypt(
    symmetricKey: ByteArray,
    message: ByteArray,
  ): IcsEncryptionResult

  fun decrypt(
    symmetricKey: ByteArray,
    data: ByteArray,
    nonce: ByteArray
  ): ByteArray

  fun encryptProviderMsaId(userOnChainSymmetricKey: ByteArray, providerMsaId: BigInteger): IcsEncryptionResult

  fun decryptProviderMsaId(userOnChainSymmetricKey: ByteArray, message: IcsEncryptionResult): BigInteger

  fun serializePublicKey(ed25519PublicKey: ByteArray): ByteArray

  fun deserializePublicKey(publicKeyData: ByteArray): IcsPublicKey

  fun serializeContextGroupAcl(contextGroupAcl: ContextGroupAcl): ByteArray

  fun deserializeContextGroupAcl(contextGroupAclData: ByteArray): ContextGroupAcl

  suspend fun getIcsPublicKeys(msaId: BigInteger): List<IndexedValue<IcsPublicKey>>

  suspend fun getLatestIcsPublicKey(msaId: BigInteger): IndexedValue<IcsPublicKey>?

  suspend fun getIcsContextGroupAcls(msaId: BigInteger): List<IndexedValue<ContextGroupAcl>>
}
