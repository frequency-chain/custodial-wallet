package io.amplica.custodial_wallet.service.ics

import io.amplica.custodial_wallet.frequency.FrequencyIntent
import io.amplica.custodial_wallet.util.fromHex
import io.amplica.custodial_wallet.util.stripHexPrefix
import io.amplica.custodial_wallet.util.toHex
import io.amplica.frequency.client.FrequencyClient
import io.amplica.frequency.client.ItemizedStorageResponseResult
import io.projectliberty.icssdk.Ics
import io.projectliberty.icssdk.storages.IcsContextGroupAcl
import kotlinx.coroutines.future.await
import org.bouncycastle.util.BigIntegers
import java.math.BigInteger

class JavaSdkIcsService(
  private val frequencyClient: FrequencyClient,
) : IcsService {

  override fun generateMasterMnemonicSeedPhrase(): String {
    return Ics.generateUserMasterMnemonic()
  }

  override fun deriveMasterSeed(mnemonicPhrase: String): ByteArray {
    return Ics.deriveUserMasterSeed(mnemonicPhrase)
  }

  override fun deriveMasterKeyPair(userMasterSeed: ByteArray): IcsKeyPair {
    val derived = Ics.deriveUserMasterKeyPair(
      userMasterSeed,
      io.projectliberty.icssdk.keys.IcsKeyType.ED25519,
    )
    require(derived.keyType == io.projectliberty.icssdk.keys.IcsKeyType.ED25519) {
      "The ICS SDK returned a different key type than requested: ${derived.keyType}"
    }

    return IcsKeyPair(derived.publicKey, derived.secretKey, IcsKeyType.ED25519)
  }

  override fun deriveUserChainDataSymmetricKey(seed: ByteArray): Derived<ByteArray> {
    val result = Ics.deriveUserChainDataKey(seed)
    return Derived(result.key, result.contextPath)
  }

  override fun deriveContextGroupId(
    userMsaId: BigInteger,
    providerMsaId: BigInteger,
    keyPair: IcsKeyPair, // Typically the user's key pair
    publicKey: IcsPublicKey, // Typically the provider's public key
  ): ByteArray {
    val exchangeSecretKey = convertToExchangeSecretKey(keyPair)
    val exchangePublicKey = convertToExchangePublicKey(publicKey)

    val idHex = Ics.deriveContextGroupIdHex(
      userMsaId.toLong(),
      providerMsaId.toLong(),
      exchangeSecretKey,
      exchangePublicKey,
    )
    return fromHex(idHex)
  }

  override fun deriveContextGroupSymmetricKey(seed: ByteArray, contextGroupId: ByteArray): Derived<ByteArray> {
    // NOTE: The ICS SDK treats hex with `0x` prefix as invalid
    val contextGroupIdHex = stripHexPrefix(toHex(contextGroupId))
    val result = Ics.deriveContextGroupKey(seed, contextGroupIdHex)

    return Derived(result.key, result.contextPath)
  }

  override fun deriveContextItemSymmetricKey(
    seed: ByteArray,
    contextItemId: String,
    contextItemTag: String,
  ): Derived<ByteArray> {
    val result = Ics.deriveContextItemKey(seed, contextItemId, contextItemTag)
    return Derived(result.key, result.contextPath)
  }

  override fun encrypt(
    symmetricKey: ByteArray,
    message: ByteArray,
    nonce: ByteArray,
  ): IcsEncryptionResult {
    val result = Ics.encrypt(symmetricKey, message, nonce)
    return IcsEncryptionResult(result.encryptedData, result.nonce)
  }

  override fun encrypt(symmetricKey: ByteArray, message: ByteArray): IcsEncryptionResult {
    val result = Ics.encrypt(symmetricKey, message)
    return IcsEncryptionResult(result.encryptedData, result.nonce)
  }

  override fun decrypt(symmetricKey: ByteArray, data: ByteArray, nonce: ByteArray): ByteArray {
    return Ics.decrypt(symmetricKey, data, nonce)
  }

  override fun encryptProviderMsaId(userOnChainSymmetricKey: ByteArray, providerMsaId: BigInteger): IcsEncryptionResult {
    val serializedProviderId = BigIntegers.asUnsignedByteArray(Long.SIZE_BYTES, providerMsaId).reversedArray()

    return encrypt(
      userOnChainSymmetricKey,
      serializedProviderId,
    )
  }

  override fun decryptProviderMsaId(userOnChainSymmetricKey: ByteArray, message: IcsEncryptionResult): BigInteger {
    val plainText = decrypt(
      userOnChainSymmetricKey,
      message.data,
      message.nonce,
    )

    val providerMsaId = BigIntegers.fromUnsignedByteArray(plainText.reversedArray())

    return providerMsaId
  }


  override fun serializePublicKey(ed25519PublicKey: ByteArray): ByteArray {
    return Ics.serializePublicKey(ed25519PublicKey)
  }

  override fun deserializePublicKey(publicKeyData: ByteArray): IcsPublicKey {
    val result = Ics.deserializePublicKey(publicKeyData)
    // Only Ed25519 key pairs are registered on chain
    require(result.keyType == io.projectliberty.icssdk.keys.IcsKeyType.ED25519) {
      "Deserializing public key unexpectedly had key type: ${result.keyType}"
    }

    return IcsPublicKey(result.publicKey, IcsKeyType.ED25519)
  }

  override fun serializeContextGroupAcl(contextGroupAcl: ContextGroupAcl): ByteArray {
    val icsData = IcsContextGroupAcl(
      stripHexPrefix(toHex(contextGroupAcl.contextGroupId)),
      contextGroupAcl.keyId,
      contextGroupAcl.nonce,
      contextGroupAcl.encryptedProviderId
    )
    return Ics.serializeContextGroupAcl(icsData)
  }

  override fun deserializeContextGroupAcl(contextGroupAclData: ByteArray): ContextGroupAcl {
    val result = Ics.deserializeContextGroupAcl(contextGroupAclData)
    return ContextGroupAcl(
      fromHex(result.contextGroupIdHex),
      result.keyId,
      result.nonce,
      result.encryptedProviderId,
    )
  }

  private fun toLibKeyType(keyType: IcsKeyType): io.projectliberty.icssdk.keys.IcsKeyType {
    return when (keyType) {
      IcsKeyType.ED25519 -> io.projectliberty.icssdk.keys.IcsKeyType.ED25519
    }
  }

  private fun convertToExchangePublicKey(publicKey: IcsPublicKey): ByteArray {
    val libPublicKey = io.projectliberty.icssdk.keys.IcsPublicKey(
      publicKey.publicKey,
      toLibKeyType(publicKey.type)
    )

    return Ics.convertPublicKeyToX25519(libPublicKey)
  }

  private fun convertToExchangeSecretKey(keyPair: IcsKeyPair): ByteArray {
    val libKeyPair = io.projectliberty.icssdk.keys.IcsKeyPair(
      keyPair.publicKey,
      keyPair.privateKey,
      toLibKeyType(keyPair.type)
    )

    val exchangeKeyPair = Ics.convertKeyPairToX25519(libKeyPair)
    require(exchangeKeyPair.keyType() == io.projectliberty.icssdk.keys.IcsKeyType.X25519) {
      "Key type must be X25519 for performing key exchange"
    }

    return Ics.convertKeyPairToX25519(libKeyPair).secretKey()
  }

  private suspend fun getItemizedStorageForIntent(
    intentName: String,
    msaId: BigInteger,
  ): List<ItemizedStorageResponseResult> {
    val schemaId = getLatestSchemaIdForIntent(intentName)
    return frequencyClient.getItemizedStorage(msaId, schemaId).await().items
  }

  override suspend fun getIcsPublicKeys(msaId: BigInteger): List<IndexedValue<IcsPublicKey>> {
    val results = getItemizedStorageForIntent(FrequencyIntent.ICS_PUBLIC_KEY, msaId)

    return results.map {
      val publicKey = deserializePublicKey(fromHex(it.payload))

      IndexedValue(it.index, publicKey)
    }
  }

  override suspend fun getLatestIcsPublicKey(msaId: BigInteger): IndexedValue<IcsPublicKey>? {
    val results = getItemizedStorageForIntent(FrequencyIntent.ICS_PUBLIC_KEY, msaId)
    val latest = results.maxByOrNull { it.index }

    return latest?.let {
      val publicKey = deserializePublicKey(fromHex(it.payload))

      IndexedValue(it.index, publicKey)
    }
  }

  private suspend fun getLatestSchemaIdForIntent(intentName: String): Int {
    return frequencyClient.getLatestSchemaIdByIntentName(intentName).await().fold(
      { throw IllegalStateException("Unable to get the latest schema ID for intent '$intentName'") },
      { it.value }
    )
  }

  override suspend fun getIcsContextGroupAcls(msaId: BigInteger): List<IndexedValue<ContextGroupAcl>> {
    val schemaId = getLatestSchemaIdForIntent(FrequencyIntent.ICS_CONTEXT_GROUP_ACL)
    val results = frequencyClient.getItemizedStorage(msaId, schemaId).await().items

    return results.map {
      IndexedValue(
        it.index,
        deserializeContextGroupAcl(fromHex(it.payload)))
    }
  }

}