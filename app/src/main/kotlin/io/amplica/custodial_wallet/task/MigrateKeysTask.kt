package io.amplica.custodial_wallet.task

import com.strategyobject.substrateclient.crypto.KeyPair
import com.strategyobject.substrateclient.rpc.api.AccountId
import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.client.redis.CustodialWalletRedisClient
import io.amplica.custodial_wallet.db.repository.KeyUsageType
import io.amplica.custodial_wallet.db.repository.UserKeyData
import io.amplica.custodial_wallet.orchestration.signing.SigningOrchestrationService
import io.amplica.custodial_wallet.service.key.GeneratedKeyPairData
import io.amplica.custodial_wallet.service.key.KeyService
import io.amplica.custodial_wallet.util.decodeValueToBytes
import io.amplica.custodial_wallet.util.key_creation.Encoding
import io.amplica.custodial_wallet.util.key_creation.KeyPairBytes
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import io.amplica.custodial_wallet.util.key_creation.PublicKeyBytes
import io.amplica.custodial_wallet.util.publicKeyToUniversalAddress
import io.amplica.frequency.client.FrequencyClient
import io.amplica.frequency.client.SpRuntimeMultiSignatureType
import io.amplica.frequency.client.pallet.msa.MessageSourceId
import io.amplica.frequency.payload.AddKeyDataPayload
import io.amplica.frequency.util.arrow.getOrThrow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigInteger
import kotlin.system.exitProcess
import io.amplica.frequency.signing_service.AddKeyDataPayload as FrequencyAddKeyDataPayload

data class MigrateKeysProperties(
  val migrationTaskEnabled: Boolean,
  val existingKeyPairType: KeyPairType,
  val keyUsageType: KeyUsageType,
  val missingKeyPairType: KeyPairType,
  val keysPerBatch: Int,
  val capacityCostPerKey: BigInteger,
  val blockExpiration: Long,
)
/*
    MigrateKeysTask is a one-off task to find a batch of keys and migrate them from the given type to the new type
    It can be manually triggered as well be disabling the task and then calling the component directly
 */
class MigrateKeysTask(
  private val properties: MigrateKeysProperties,
  private val databaseService: CustodialWalletDatabaseService,
  private val frequencyClient: FrequencyClient,
  private val signingOrchestrationService: SigningOrchestrationService,
  private val redisClient: CustodialWalletRedisClient,
  private val keyService: KeyService,
  private val keyPair: KeyPair,
) {



  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(MigrateKeysTask::class.java)
  }

  init {
    if(properties.migrationTaskEnabled) {
      runBlocking {
        migrateKeys()
      }
    }
  }

  suspend fun migrateKeys() {
    var keysMigrated = 0
    var keysWithoutMsaIds = 0
    val knownKeyCountWithoutMsaIds = redisClient.findMigrationTaskNoMsaCount() ?: 0
    try {
      // Obtain page of candidate users to add keys to
      val keysToMigrate = getKeys(knownKeyCountWithoutMsaIds)
      val numberOfKeys = keysToMigrate.size

      // Check if we (the custodial wallet) have enough Capacity to make the necessary calls for all keys
      checkCapacity(numberOfKeys)

      keysToMigrate.forEach { existingUserKeyData ->

        // Check if they have an MSA (we need them to have an MSA to add a key to one)
        // If they don't skip this loop
        val userPublicKeyBytes = decodeValueToBytes(existingUserKeyData.publicKeyHex, Encoding.HEX)
        val userMsaId = getMsa(userPublicKeyBytes)
        if(userMsaId == null) {
          keysWithoutMsaIds++
          return@forEach
        }

        // Generate Key
        // TODO: Graph KeyPairs only support X25519 currently. If we add functionality to support
        //  other key pairs we can support it here
        val generatedKeyPairData = keyService.generateAccountKeyPair(properties.missingKeyPairType)

        // Call add key to msa
        addPublicKeyToMsa(existingUserKeyData, userMsaId, generatedKeyPairData)

        // Persist new key in the DB
        saveKey(existingUserKeyData, generatedKeyPairData)

        keysMigrated++
      }
    } catch(exception: Exception) {
      redisClient.saveMigrationTaskNoMsaCount(knownKeyCountWithoutMsaIds + keysWithoutMsaIds)
      LOG.error("Migrate Keys Task hit an exception during migration", exception)
      exitWithError(keysMigrated, keysWithoutMsaIds)
    }
    // exit(0) when complete so K8s knows to clean up the container
    redisClient.saveMigrationTaskNoMsaCount(knownKeyCountWithoutMsaIds + keysWithoutMsaIds)
    exitSuccessfully(keysMigrated, keysWithoutMsaIds)
  }


  private suspend fun getKeys(offset: Int): List<UserKeyData> {
    return databaseService.findUserKeyDataMissingKeyType(
      properties.existingKeyPairType,
      properties.keyUsageType,
      properties.missingKeyPairType,
      properties.keysPerBatch,
      offset
    )
  }

  private suspend fun checkCapacity(numberOfKeys: Int) {
    val cwPublicKeyBytes = keyPair.asPublicKey().bytes
    val cwMsaId = frequencyClient.getMsaIdByAccountId(cwPublicKeyBytes).await()
      ?: throw IllegalStateException("No CW MsaId found for $cwPublicKeyBytes")
    val capacityDetails = frequencyClient.getCapacityDetails(cwMsaId).await()
      ?: throw IllegalStateException("No capacity details for $cwMsaId")
    val neededTotalCapacity = properties.capacityCostPerKey.multiply(numberOfKeys.toBigInteger())

    if (capacityDetails.remainingCapacity < neededTotalCapacity) {
      throw IllegalStateException("Not enough remaining Capacity to meat neededTotalCapacity. Remaining Capacity: ${capacityDetails.remainingCapacity}")
    }
  }

  // TODO this can lead to a lock up if every public key doesn't have an msa.
  //  Keys will stack up because we aren't resolving their issues
  private suspend fun getMsa(userPublicKeyBytes: PublicKeyBytes): BigInteger? {
    return frequencyClient.getMsaIdByAccountId(userPublicKeyBytes).await()
  }

  private suspend fun addPublicKeyToMsa(
    existingUserKeyData: UserKeyData,
    userMsaId: BigInteger,
    newKeyPairData: GeneratedKeyPairData<KeyPairBytes>
  ) {

    val currentBlockNumber = frequencyClient.getLastBlockNumber().await()
    val universalAccountIdKey = publicKeyToUniversalAddress(newKeyPairData.publicKey.toPublicKeyBytes(), newKeyPairData.publicKey.type)
    val payload = AddKeyDataPayload(
      userMsaId,
      currentBlockNumber.toLong() + properties.blockExpiration,
      universalAccountIdKey
    )

    val ownerKeyPairBytes = keyService.decryptUserAccountKeyData(existingUserKeyData)
    val ownerPublicKey = decodeValueToBytes(existingUserKeyData.publicKeyHex, Encoding.HEX)
    val ownerSignatureType = getMultiSigType(existingUserKeyData.encryptedPrivateKeyType)
    val ownerSignature = signingOrchestrationService.signPayload(ownerKeyPairBytes, payload, Encoding.HEX).toSignatureBytes()
    val newPublicKeySignatureType = getMultiSigType(newKeyPairData.encryptedKeyData.encryptedPrivateKeyType)
    val newPublicKeySignature =
      signingOrchestrationService.signPayload(newKeyPairData.keyPair, payload, Encoding.HEX).toSignatureBytes()
    val frequencyPayload = FrequencyAddKeyDataPayload(
      MessageSourceId(payload.msaId),
      payload.expiration,
      AccountId.fromBytes(payload.newPublicKey)
    )

    frequencyClient.addPublicKeyToMsaWithCapacity(
      ownerPublicKey,
      ownerSignatureType,
      ownerSignature,
      newPublicKeySignatureType,
      newPublicKeySignature,
      frequencyPayload,
    ).await().getOrThrow()
  }

  private suspend fun saveKey(existingUserKeyData: UserKeyData, newKeyPairData: GeneratedKeyPairData<KeyPairBytes>) {
    val newUserKeyData = UserKeyData.create(
      existingUserKeyData.userAccountId,
      newKeyPairData.keyPair.publicKeyBytes,
      newKeyPairData.encryptedKeyData.encryptedPrivateKey,
      newKeyPairData.encryptedKeyData.encryptedPrivateKeyType,
      newKeyPairData.encryptedKeyData.keyUsageType
    )
    databaseService.saveUserKeyData(newUserKeyData)
  }


  private fun getMultiSigType(keyPairType: KeyPairType): SpRuntimeMultiSignatureType {
    return when (keyPairType) {
      KeyPairType.SR25519 -> SpRuntimeMultiSignatureType.SR25519
      KeyPairType.SECP256K1 -> SpRuntimeMultiSignatureType.ECDSA
      else -> throw IllegalStateException("Invalid keyPairType for creating MultiSigType: $keyPairType")
    }
  }

  // Handling system calls in tests can be very difficult since the deprecation of the SecurityManager
  // As a result the use of methods to wrap system calls for mocking purposes is unfortunately
  // the most common recommended practice even if it requires changing code for tests.
  fun exitSuccessfully(keysMigrated: Int, keysWithoutMsaIds: Int) {
    LOG.info("Migration task completed. Number of keys migrated: $keysMigrated. Number of keys without MsaIds: $keysWithoutMsaIds")
    exitProcess(0)
  }

  fun exitWithError(keysMigrated: Int, keysWithoutMsaIds: Int) {
    LOG.info("Migration task failed. Number of keys migrated before exit: $keysMigrated. Number of keys without MsaIds: $keysWithoutMsaIds")
    exitProcess(1)
  }
}