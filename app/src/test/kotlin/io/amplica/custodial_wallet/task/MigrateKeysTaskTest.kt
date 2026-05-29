package io.amplica.custodial_wallet.task

import com.google.common.base.Throwables
import com.strategyobject.substrateclient.transport.ws.RpcException
import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.client.redis.CustodialWalletRedisClient
import io.amplica.custodial_wallet.container.CustodialWalletE2ETestStack
import io.amplica.custodial_wallet.db.repository.*
import io.amplica.custodial_wallet.orchestration.signing.SigningOrchestrationService
import io.amplica.custodial_wallet.service.key.KeyService
import io.amplica.custodial_wallet.util.CustodialWalletE2ESpringTestConfiguration
import io.amplica.custodial_wallet.util.decodeValueToBytes
import io.amplica.custodial_wallet.util.key_creation.Encoding
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import io.amplica.custodial_wallet.util.publicKeyToUniversalAddress
import io.amplica.frequency.client.DelegationGranted
import io.amplica.frequency.client.SpRuntimeMultiSignatureType
import io.amplica.frequency.util.arrow.getOrThrow
import io.amplica.frequency.payload.AddProviderPayload
import io.amplica.custodial_wallet.util.key_creation.KeyPairBytes
import io.amplica.custodial_wallet.util.key_creation.PublicKeyBytes
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import java.math.BigInteger
import io.amplica.frequency.signing_service.AddProviderPayload as FrequencyAddProviderPayload


@CustodialWalletE2ESpringTestConfiguration
@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
class MigrateKeysTaskTest() {
  @Autowired
  lateinit var userKeyDataRepository: ReactiveUserKeyDataRepository

  @Autowired
  lateinit var userAccountRepository: ReactiveUserAccountRepository

  @Autowired
  lateinit var databaseService: CustodialWalletDatabaseService

  @Autowired
  lateinit var keyService: KeyService

  @Autowired
  lateinit var signingOrchestrationService: SigningOrchestrationService

  @Autowired
  lateinit var redisClient: CustodialWalletRedisClient

  @Autowired
  lateinit var migrateKeysTask: MigrateKeysTask


  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(MigrateKeysTaskTest::class.java)

    // NOTE(Julian, 2024-08-20): These values are not correct. 7-10 are the graph schemas and there is no ID greater than 11.
    val DEFAULT_SCHEMA_IDS = listOf(5, 7, 8, 9, 10)
    const val DEFAULT_EXPIRATION = 10L

    @Container
    val containers = CustodialWalletE2ETestStack()

    @DynamicPropertySource
    @JvmStatic
    fun registerContainerProperties(registry: DynamicPropertyRegistry) {
      containers.registerDynamicProperties(registry)
    }
  }


  @BeforeEach
  fun setUp(): Unit = runBlocking {

    // First we add the DB UserKeyData


    val userAccount1 = userAccountRepository.save(UserAccount.create()).awaitSingle()
    val userAccount2 = userAccountRepository.save(UserAccount.create()).awaitSingle()
    val userAccount3 = userAccountRepository.save(UserAccount.create()).awaitSingle()
    val userAccount4 = userAccountRepository.save(UserAccount.create()).awaitSingle()
    val userAccount5 = userAccountRepository.save(UserAccount.create()).awaitSingle()
    val userKey1 = keyService.generateAccountKeyPair(KeyPairType.SR25519)
    val userKey2 = keyService.generateAccountKeyPair(KeyPairType.SR25519)
    val userKey3 = keyService.generateAccountKeyPair(KeyPairType.SR25519)
    val userKey4 = keyService.generateAccountKeyPair(KeyPairType.SR25519)
    val userKey5 = keyService.generateAccountKeyPair(KeyPairType.SR25519)

    val userKeyData1NoMsa = UserKeyData.create(
      userAccount1.id!!,
      userKey1.keyPair.publicKeyBytes,
      userKey1.encryptedKeyData.encryptedPrivateKey,
      userKey1.encryptedKeyData.encryptedPrivateKeyType,
      KeyUsageType.ACCOUNT
    )
    val userKeyData2NoMsa = UserKeyData.create(
      userAccount2.id!!,
      userKey2.keyPair.publicKeyBytes,
      userKey2.encryptedKeyData.encryptedPrivateKey,
      userKey2.encryptedKeyData.encryptedPrivateKeyType,
      KeyUsageType.ACCOUNT
    )
    val userKeyData3 = UserKeyData.create(
      userAccount3.id!!,
      userKey3.keyPair.publicKeyBytes,
      userKey3.encryptedKeyData.encryptedPrivateKey,
      userKey3.encryptedKeyData.encryptedPrivateKeyType,
      KeyUsageType.ACCOUNT
    )
    val userKeyData4 = UserKeyData.create(
      userAccount4.id!!,
      userKey4.keyPair.publicKeyBytes,
      userKey4.encryptedKeyData.encryptedPrivateKey,
      userKey4.encryptedKeyData.encryptedPrivateKeyType,
      KeyUsageType.ACCOUNT
    )
    val userKeyData5 = UserKeyData.create(
      userAccount5.id!!,
      userKey5.keyPair.publicKeyBytes,
      userKey5.encryptedKeyData.encryptedPrivateKey,
      userKey5.encryptedKeyData.encryptedPrivateKeyType,
      KeyUsageType.ACCOUNT
    )

    databaseService.saveUserKeyData(userKeyData1NoMsa)
    databaseService.saveUserKeyData(userKeyData2NoMsa)
    databaseService.saveUserKeyData(userKeyData3)
    databaseService.saveUserKeyData(userKeyData4)
    databaseService.saveUserKeyData(userKeyData5)

    // two bad keys, 3 SR keys
    Assertions.assertEquals(5, userKeyDataRepository.findAll().collectList().awaitSingle().size)

    // Next we add the data to the chain
    val userMsa3 = createUserInFrequency(userKey3.keyPair)
    val userMsa4 = createUserInFrequency(userKey4.keyPair)
    val userMsa5 = createUserInFrequency(userKey5.keyPair)

    val retrievedMsaId3 = getFrequencyUserMsaId(userKey3.keyPair.publicKeyBytes)
    val retrievedMsaId4 = getFrequencyUserMsaId(userKey4.keyPair.publicKeyBytes)
    val retrievedMsaId5 = getFrequencyUserMsaId(userKey5.keyPair.publicKeyBytes)

    Assertions.assertEquals(userMsa3, retrievedMsaId3)
    Assertions.assertEquals(userMsa4, retrievedMsaId4)
    Assertions.assertEquals(userMsa5, retrievedMsaId5)

  }

  @AfterEach
  fun tearDown(): Unit = runBlocking {
    userKeyDataRepository.deleteAll().awaitSingleOrNull()
  }

  @Test
  fun migrateKeysTaskWorksSuccessfully() {
    val migrateKeysTaskSpy = spy(migrateKeysTask)
    doNothing().`when`(migrateKeysTaskSpy).exitSuccessfully(any(),any())
    doNothing().`when`(migrateKeysTaskSpy).exitWithError(any(), any())

    runBlocking {
      migrateKeysTaskSpy.migrateKeys() // Process two accounts (both are no msa)
      Assertions.assertEquals(2, redisClient.findMigrationTaskNoMsaCount())

      migrateKeysTaskSpy.migrateKeys() // Process the next two, should now skip the no msa accounts
      val midwayUserKeyData = userKeyDataRepository.findAll().collectList().awaitSingle()
      Assertions.assertEquals(7, midwayUserKeyData.size) // 2 bad keys, 3 SR keys, 2 new SECP keys

      migrateKeysTaskSpy.migrateKeys() // process the final account

      verify(migrateKeysTaskSpy, Mockito.times(3)).exitSuccessfully(any(), any())

      //Look for errors in logs if test fails on exitWithErrors
      verify(migrateKeysTaskSpy, Mockito.times(0)).exitWithError(any(), any())

      val finalUserKeyData = userKeyDataRepository.findAll().collectList().awaitSingle()
      Assertions.assertEquals(8, finalUserKeyData.size) // 2 bad keys, 3 SR keys, and the 3 new SECP keys
      finalUserKeyData
        .filter { it.encryptedPrivateKeyType == KeyPairType.SECP256K1 }
        .forEach { ethKeyData ->
          val ethPublicKeyBytes = decodeValueToBytes(ethKeyData.publicKeyHex, Encoding.HEX)
          val universalAccountIdKey = publicKeyToUniversalAddress(ethPublicKeyBytes, ethKeyData.encryptedPrivateKeyType)
          val retrievedMsaId = getFrequencyUserMsaId(universalAccountIdKey)
          Assertions.assertNotNull(retrievedMsaId)
        }
    }
  }

  fun createUserInFrequency(
    userKeyPairBytes: KeyPairBytes
  ): BigInteger {
    val aliceFrequencyClient = containers.frequency.aliceProviderClient
    val aliceProviderMsaId = containers.frequency.aliceProviderMsaId

    val addProviderPayload = AddProviderPayload(
      aliceProviderMsaId,
      DEFAULT_SCHEMA_IDS,
      DEFAULT_EXPIRATION
    )
    val signature =
      signingOrchestrationService.signPayload(userKeyPairBytes, addProviderPayload, Encoding.HEX).toSignatureBytes()

    val frequencyAddProviderPayload = FrequencyAddProviderPayload(
      addProviderPayload.authorizedMsaId,
      addProviderPayload.intentIds,
      addProviderPayload.expiration,
    )

    var createdSponsoredAccount: DelegationGranted? = null
    try {
      createdSponsoredAccount = aliceFrequencyClient.createSponsoredAccountWithDelegationWithCapacity(
        userKeyPairBytes.publicKeyBytes,
        SpRuntimeMultiSignatureType.SR25519,
        signature,
        frequencyAddProviderPayload
      ).join().getOrThrow()

      Assertions.assertEquals(createdSponsoredAccount.provider?.value, aliceProviderMsaId)

    } catch (x: Exception) {
      val rootCause = Throwables.getRootCause(x)
      var additionalData = ""
      if (rootCause is RpcException) {
        additionalData = "RpcException is detected, code=${rootCause.code} data=${rootCause.data}"
      }
      LOG.error(
        "Exception occurred when trying to createSponsoredAccountWithDelegationWithCapacity $additionalData",
        x
      )
      Assertions.fail(x)
    }

    return createdSponsoredAccount?.delegator?.value
      ?: throw AssertionError("Creating user didn't return a MessageSourceId")
  }

  fun getFrequencyUserMsaId(userPublicKeyBytes: PublicKeyBytes): BigInteger? {
    return containers.frequency.aliceProviderClient.getMsaIdByAccountId(userPublicKeyBytes).join()
  }
}