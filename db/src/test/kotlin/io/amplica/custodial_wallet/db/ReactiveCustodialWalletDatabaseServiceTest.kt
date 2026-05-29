package io.amplica.custodial_wallet.db

import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.EncryptedKeyData
import io.amplica.custodial_wallet.client.kms.EncryptedKey
import io.amplica.custodial_wallet.client.kms.KmsDecryptionKey
import io.amplica.custodial_wallet.client.kms.KmsEncryptionAlgorithm
import io.amplica.custodial_wallet.db.conf.BeanNames
import io.amplica.custodial_wallet.db.conf.DbTestConfig
import io.amplica.custodial_wallet.db.conf.ReactiveCustodialWalletDatabaseServiceConf
import io.amplica.custodial_wallet.db.data.PasskeyWallet
import io.amplica.custodial_wallet.db.repository.*
import io.amplica.custodial_wallet.util.base64UrlEncode
import io.amplica.custodial_wallet.util.fromHex
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import io.amplica.custodial_wallet.util.key_creation.Secp256k1KeyPairCreator
import io.amplica.custodial_wallet.util.key_creation.Sr25519KeyPairBytes
import io.amplica.custodial_wallet.util.key_creation.Sr25519KeyPairCreator
import io.amplica.custodial_wallet.util.key_creation.X25519KeyPairCreator
import io.amplica.custodial_wallet.util.toHex
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.dao.DuplicateKeyException
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigInteger
import java.time.Instant
import java.util.*
import kotlin.random.Random

@Testcontainers
@SpringJUnitConfig(classes = [DbTestConfig::class, ReactiveCustodialWalletDatabaseServiceTest::class, ReactiveCustodialWalletDatabaseServiceConf::class])
@EnableAutoConfiguration
class ReactiveCustodialWalletDatabaseServiceTest(
  @Qualifier(BeanNames.TEST_USER_HELPER) private val testUserHelper: TestUserHelper
) {
  @Autowired
  lateinit var custodialWalletDatabaseService: CustodialWalletDatabaseService

  @Autowired
  lateinit var reactiveUserAccountRepository: ReactiveUserAccountRepository
  @Autowired
  lateinit var reactiveUserKeyDataRepository: ReactiveUserKeyDataRepository
  @Autowired
  lateinit var reactiveProviderExternalUserRepository: ReactiveProviderExternalUserRepository
  @Autowired
  lateinit var reactiveProviderExternalUserDetailRepository: ReactiveProviderExternalUserDetailRepository
  @Autowired
  lateinit var reactiveUserIdentifierRepository: ReactiveUserIdentifierRepository
  @Autowired
  lateinit var reactiveUserAccountUserIdentifierRepository: ReactiveUserAccountUserIdentifierRepository
  @Autowired
  lateinit var reactiveAuditSessionRecordRepository: ReactiveAuditSessionRecordRepository
  @Autowired
  lateinit var reactiveUserPasswordRepository: ReactiveUserPasswordRepository
  @Autowired
  lateinit var reactiveWalletRepository: ReactiveWalletRepository
  @Autowired
  lateinit var reactiveCredentialRepository: ReactiveCredentialRepository
  @Autowired
  lateinit var reactiveCredentialTransportRepository: ReactiveCredentialTransportRepository
  @Autowired
  lateinit var reactiveWalletMetadataRepository: ReactiveWalletMetadataRepository
  @Autowired
  lateinit var reactiveOptInRepository: ReactiveOptInRepository
  @Autowired
  lateinit var reactiveUserSeedDataRepository: ReactiveUserSeedDataRepository
  @Autowired
  lateinit var reactiveUserDerivedKeyDataRepository: ReactiveUserDerivedKeyDataRepository


  companion object {
    private const val USERNAME = "unfinished"
    private const val PASSWORD = "somePassword"
    private const val DATABASE_NAME = "custodial_wallet_dev"
    private const val SCHEMA = "custodial_wallet"

    //This is not marked @Container by design and handled by hand because the .apply trick in order to use the
    //Testcontainers builder doesn't resolve correctly when setting module things like .withXXX
    private lateinit var postgres: PostgreSQLContainer<Nothing>

    @DynamicPropertySource
    @JvmStatic
    fun registerDynamicProperties(registry: DynamicPropertyRegistry) {
      registry.add("unfinished.custodial-wallet.db.service.createSchema") { true }
      registry.add("unfinished.custodial-wallet.db.service.schema") { SCHEMA }
      registry.add("spring.r2dbc.url") { "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/${DATABASE_NAME}?schema=${SCHEMA}" }
      registry.add("unfinished.custodial-wallet.ro.r2dbc.url") { "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/${DATABASE_NAME}?schema=${SCHEMA}" }
      registry.add("spring.r2dbc.username") { USERNAME }
      registry.add("spring.r2dbc.password") { PASSWORD }

      registry.add("spring.r2dbc.pool.enabled") { true }
      registry.add("spring.r2dbc.pool.max-life-time") { 300000 }
      registry.add("spring.r2dbc.pool.max-create-connection-time") { 1000 }
      registry.add("spring.r2dbc.pool.max-size") { 10 }
      registry.add("unfinished.custodial-wallet.r2dbc.statement.timeout.millis") { 5000 }
    }

    @BeforeAll
    @JvmStatic
    fun setUpClass() {
      postgres = PostgreSQLContainer<Nothing>("postgres:12.7")
      postgres.withUsername(USERNAME)
      postgres.withPassword(PASSWORD)
      postgres.withDatabaseName(DATABASE_NAME)
      postgres.addEnv("POSTGRES_INITDB_ARGS", "--encoding=UTF8 --lc-collate=en_US.UTF-8 --lc-ctype=en_US.UTF-8")
      postgres.start()
    }

    @AfterAll
    @JvmStatic
    fun afterClass() {
      postgres.stop()
    }
  }

  @AfterEach
  fun tearDown() {
    runBlocking {
      reactiveUserPasswordRepository.deleteAll().awaitFirstOrNull()
      reactiveProviderExternalUserDetailRepository.deleteAll().awaitFirstOrNull()
      reactiveProviderExternalUserRepository.deleteAll().awaitFirstOrNull()
      reactiveUserKeyDataRepository.deleteAll().awaitFirstOrNull()
      reactiveUserAccountUserIdentifierRepository.deleteAll().awaitFirstOrNull()
      reactiveUserIdentifierRepository.deleteAll().awaitFirstOrNull()
      reactiveWalletMetadataRepository.deleteAll().awaitSingleOrNull()
      reactiveCredentialTransportRepository.deleteAll().awaitFirstOrNull()
      reactiveCredentialRepository.deleteAll().awaitFirstOrNull()
      reactiveWalletRepository.deleteAll().awaitFirstOrNull()
      reactiveOptInRepository.deleteAll().awaitFirstOrNull()
      reactiveUserDerivedKeyDataRepository.deleteAll().awaitFirstOrNull()
      reactiveUserSeedDataRepository.deleteAll().awaitFirstOrNull()
      reactiveUserAccountRepository.deleteAll().awaitFirstOrNull()
      reactiveAuditSessionRecordRepository.deleteAll().awaitFirstOrNull()
    }
  }

  @Nested
  @DisplayName("UserKeyData Tests")
  inner class UserKeyDataTests {

    private val providerMsaId1 = 1.toBigInteger()
    private val providerMsaId2 = 2.toBigInteger()
    private val providerMsaId3 = 3.toBigInteger()
    private val providerExternalId1 = "someUserId1"
    private val providerExternalId2 = "someUserId2"
    private val providerExternalId3 = "someUserId3"
    private val emailDetail1 = UserDetail("saas-dev@unfinished.com", UserDetailType.EMAIL, 0)
    private val emailDetail2 = UserDetail("joe.floopy@unfinished.com", UserDetailType.EMAIL, 0)
    private val emailDetail3 = UserDetail("foo.bar@unfinished.com", UserDetailType.EMAIL, 1)
    private val phoneDetail1 = UserDetail("1112223333", UserDetailType.PHONE_NUMBER, 1)
    private val phoneDetail3 = UserDetail("7778889999", UserDetailType.PHONE_NUMBER, 0)
    private val sessionId = "sessionId"

    private val keypair1 = Sr25519KeyPairCreator.createKeyPair()
    private val keypair2 = Sr25519KeyPairCreator.createKeyPair()
    private val keypair3 = Sr25519KeyPairCreator.createKeyPair()
    private val encryptedPrivateKeyType = KeyPairType.SR25519
    private val accountKeyUsageType = KeyUsageType.ACCOUNT

    private val graphKeypair1 = X25519KeyPairCreator.createKeyPair()
    private val graphKeypair2 = X25519KeyPairCreator.createKeyPair()
    private val graphKeypair3 = X25519KeyPairCreator.createKeyPair()
    private val graphKeypair4 = X25519KeyPairCreator.createKeyPair()
    private val encryptedGraphKeyType = KeyPairType.X25519
    private val graphKeyUsageType = KeyUsageType.GRAPH

    private lateinit var userAccount1: UserAccount
    private lateinit var userAccount2: UserAccount

    private lateinit var userKeyDataAccount1: UserKeyData
    private lateinit var userKeyDataGraph1: UserKeyData

    private lateinit var userKeyDataAccount2: UserKeyData
    private lateinit var userKeyDataGraph2: UserKeyData

    private lateinit var providerExternalUser1: ProviderExternalUser
    private lateinit var providerExternalUser2: ProviderExternalUser

    private lateinit var providerExternalUserDetail1: ProviderExternalUserDetail
    private lateinit var providerExternalUserDetail2: ProviderExternalUserDetail

    private lateinit var userIdentifier1: UserIdentifier
    private lateinit var userIdentifier2: UserIdentifier

    private lateinit var auditSessionRecord: AuditSessionRecord


    @BeforeEach
    fun populateTables() {
      runBlocking {
        userAccount1 = reactiveUserAccountRepository.save(UserAccount.create()).awaitSingle()
        userAccount2 = reactiveUserAccountRepository.save(UserAccount.create()).awaitSingle()

        userKeyDataAccount1 = reactiveUserKeyDataRepository.save(
          UserKeyData.create(
            userAccount1.id!!,
            keypair1.publicKeyBytes,
            EncryptedKey(keypair1.privateKeyBytes, KmsDecryptionKey("keyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)),
            encryptedPrivateKeyType,
            accountKeyUsageType
          )
        ).awaitSingle()

        userKeyDataGraph1 = reactiveUserKeyDataRepository.save(
          UserKeyData.create(
            userAccount1.id!!,
            graphKeypair1.publicKeyBytes,
            EncryptedKey(graphKeypair1.privateKeyBytes, KmsDecryptionKey("keyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)),
            encryptedGraphKeyType,
            graphKeyUsageType
          )
        ).awaitSingle()

        userKeyDataAccount2 = reactiveUserKeyDataRepository.save(
          UserKeyData.create(
            userAccount2.id!!,
            keypair2.publicKeyBytes,
            EncryptedKey(keypair2.privateKeyBytes, KmsDecryptionKey("keyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)),
            encryptedPrivateKeyType,
            accountKeyUsageType
          )
        ).awaitSingle()

        userKeyDataGraph2 = reactiveUserKeyDataRepository.save(
          UserKeyData.create(
            userAccount2.id!!,
            graphKeypair2.publicKeyBytes,
            EncryptedKey(graphKeypair2.privateKeyBytes, KmsDecryptionKey("keyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)),
            encryptedGraphKeyType,
            graphKeyUsageType
          )
        ).awaitSingle()

        providerExternalUser1 = reactiveProviderExternalUserRepository.save(ProviderExternalUser.create(providerMsaId1, providerExternalId1, userKeyDataAccount1.id!!)).awaitSingle()
        providerExternalUser2 = reactiveProviderExternalUserRepository.save(ProviderExternalUser.create(providerMsaId2, providerExternalId2, userKeyDataAccount2.id!!)).awaitSingle()

        userIdentifier1 = reactiveUserIdentifierRepository.save(UserIdentifier.create(UserDetail(emailDetail1.value, emailDetail1.type, emailDetail1.priority))).awaitSingle()
        userIdentifier2 = reactiveUserIdentifierRepository.save(UserIdentifier.create(UserDetail(emailDetail2.value, emailDetail2.type, emailDetail2.priority))).awaitSingle()


        reactiveUserAccountUserIdentifierRepository.save(UserAccountUserIdentifier(userAccount1.id!!, userIdentifier1.id!!)).awaitSingle()
        reactiveUserAccountUserIdentifierRepository.save(UserAccountUserIdentifier(userAccount2.id!!, userIdentifier2.id!!)).awaitSingle()

        providerExternalUserDetail1 = reactiveProviderExternalUserDetailRepository.save(
          ProviderExternalUserDetail.create(
            providerExternalUser1.id!!,
            userAccount1.id!!,
            emailDetail1,
            userIdentifier1.id!!
          )
        ).awaitSingle()
        providerExternalUserDetail2 = reactiveProviderExternalUserDetailRepository.save(
          ProviderExternalUserDetail.create(
            providerExternalUser2.id!!,
            userAccount2.id!!,
            emailDetail2,
            userIdentifier1.id!!
          )
        ).awaitSingle()

        auditSessionRecord = reactiveAuditSessionRecordRepository.save(AuditSessionRecord.create(sessionId, Flow.ONBOARD, State.REQUEST_RECEIVED, FinalizedState.INCOMPLETE, null)).awaitSingle()
      }
    }

    @Test
    fun testFindUserDetailsByProviderMsaIdAndExternalUserId() {
      runBlocking {
        val retVal = custodialWalletDatabaseService.findUserDetailsByProviderMsaIdAndExternalUserId(providerMsaId1, providerExternalId1)
        Assertions.assertThat(retVal.size).isGreaterThan(0)
      }
    }

    @Test
    fun findProviderExternalUserByProviderMsaIdAndPublicKeyHex(): Unit = runBlocking {
      val retVal = custodialWalletDatabaseService.findProviderExternalUserByProviderMsaIdAndKeyPairTypeAndPublicKeyHexAndKeyUsageType(providerMsaId1, encryptedPrivateKeyType, toHex(keypair1.publicKeyBytes), KeyUsageType.ACCOUNT)

      Assertions.assertThat(retVal).isNotNull
      Assertions.assertThat(retVal!!.providerMsaId).isEqualTo(providerMsaId1)
      Assertions.assertThat(retVal.providerExternalId).isEqualTo(providerExternalUser1.providerExternalId)
    }

    @Test
    fun findByProviderMsaIdAndKeyPairTypeAndPublicKeyHexAndKeyUsageType(): Unit = runBlocking {
      val retVal = custodialWalletDatabaseService.findOneUserKeyData(providerMsaId1, encryptedPrivateKeyType, toHex(keypair1.publicKeyBytes), KeyUsageType.ACCOUNT)

      Assertions.assertThat(retVal).isNotNull
      Assertions.assertThat(retVal!!.keyUsageType).isSameAs(KeyUsageType.ACCOUNT)
      Assertions.assertThat(retVal.publicKeyHex).isEqualTo(toHex(keypair1.publicKeyBytes))
      Assertions.assertThat(retVal.encryptedPrivateKeyType).isSameAs(encryptedPrivateKeyType)
    }

    @Test
    fun testSQL() {
      runBlocking {
        val data = reactiveProviderExternalUserDetailRepository.findUserAccountIdsByUserDetailsIn(
          listOf(emailDetail1, emailDetail2)
        ).collectList().awaitSingle()
        Assertions.assertThat(data).isNotNull
        Assertions.assertThat(data.size).isEqualTo(2)
      }

    }


    @Test
    fun saveNewUserData() {
      // GIVEN (Above data)

      // WHEN
      val storedUserKeyData = runBlocking {
        custodialWalletDatabaseService.saveNewUserData(
          providerMsaId3,
          providerExternalId3,
          listOf(UserDetail(emailDetail3.value, emailDetail3.type, emailDetail3.priority), phoneDetail3),
          listOf(
            EncryptedKeyData(
              keypair3.publicKeyBytes,
              EncryptedKey(keypair3.privateKeyBytes, KmsDecryptionKey("keyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)),
              encryptedPrivateKeyType,
              KeyUsageType.ACCOUNT
            ),
            EncryptedKeyData(
              graphKeypair3.publicKeyBytes,
              EncryptedKey(graphKeypair3.privateKeyBytes, KmsDecryptionKey("keyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)),
              encryptedGraphKeyType,
              KeyUsageType.GRAPH
            ),
          )
        )
      }

      // THEN
      runBlocking {
        Assertions.assertThat(storedUserKeyData.id).isNotNull
        val foundUserKeyData = reactiveUserKeyDataRepository.findById(storedUserKeyData.id!!).awaitSingle()
        Assertions.assertThat(foundUserKeyData).isNotNull
        Assertions.assertThat(storedUserKeyData).usingRecursiveComparison().isEqualTo(foundUserKeyData)

        val graphKeyData = reactiveUserKeyDataRepository.findByProviderMsaIdAndProviderExternalIdAndKeyUsageTypeInUserAccount(
          providerMsaId3,
          providerExternalId3,
          KeyUsageType.GRAPH)
          .collectList()
          .awaitSingle()
        Assertions.assertThat(graphKeyData).isNotNull
        Assertions.assertThat(graphKeyData.size).isEqualTo(1)
        Assertions.assertThat(graphKeyData[0].keyUsageType).isEqualTo(KeyUsageType.GRAPH)
        Assertions.assertThat(graphKeyData[0].userAccountId).isEqualTo(storedUserKeyData.userAccountId)

        val accountKeyData = reactiveUserKeyDataRepository.findByProviderMsaIdAndProviderExternalIdAndKeyUsageTypeInUserAccount(
          providerMsaId3,
          providerExternalId3,
          KeyUsageType.ACCOUNT)
          .collectList()
         .awaitSingle()
        Assertions.assertThat(accountKeyData).isNotNull
        Assertions.assertThat(accountKeyData.size).isEqualTo(1)
        Assertions.assertThat(accountKeyData[0].keyUsageType).isEqualTo(KeyUsageType.ACCOUNT)
        Assertions.assertThat(accountKeyData[0]).usingRecursiveComparison().isEqualTo(storedUserKeyData)

        val userKeyDataCount = reactiveUserKeyDataRepository.findAll().count().awaitSingle()
        Assertions.assertThat(userKeyDataCount).isEqualTo(6) //four existing plus two new keys

        val userAccountsCount = reactiveUserAccountRepository.findAll().count().awaitSingle()
        Assertions.assertThat(userAccountsCount).isEqualTo(3) //Two existing plus our new one

        val providerExternalUserCount = reactiveProviderExternalUserRepository.findAll().count().awaitSingle()
        Assertions.assertThat(providerExternalUserCount).isEqualTo(3) //Two existing plus our new one

        val providerExternalUserDetailCount = reactiveProviderExternalUserDetailRepository.findAll().count().awaitSingle()
        Assertions.assertThat(providerExternalUserDetailCount).isEqualTo(4) //Two existing plus our two new ones
      }
    }

    @Test
    fun saveDuplicateUserData() {
      // GIVEN (Above data)

      // WHEN
      Assertions.assertThatThrownBy {
        runBlocking {
          custodialWalletDatabaseService.saveNewUserData(
            providerMsaId3,
            providerExternalId3,
            listOf(emailDetail1, emailDetail2),
            listOf(
              EncryptedKeyData(
                keypair3.publicKeyBytes,
                EncryptedKey(keypair3.privateKeyBytes, KmsDecryptionKey("keyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)),
                encryptedPrivateKeyType,
                KeyUsageType.ACCOUNT
              ),
              EncryptedKeyData(
                graphKeypair3.publicKeyBytes,
                EncryptedKey(graphKeypair3.privateKeyBytes, KmsDecryptionKey("keyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)),
                encryptedGraphKeyType,
                KeyUsageType.GRAPH
              ),
            )
          )
        }
      }.isInstanceOf(IllegalStateException::class.java)

      runBlocking {
        val userKeyDataCount = reactiveUserKeyDataRepository.findAll().count().awaitSingle()
        Assertions.assertThat(userKeyDataCount).isEqualTo(4) //Four existing

        val userAccountsCount = reactiveUserAccountRepository.findAll().count().awaitSingle()
        Assertions.assertThat(userAccountsCount).isEqualTo(2) //Two existing

        val providerExternalUserCount = reactiveProviderExternalUserRepository.findAll().count().awaitSingle()
        Assertions.assertThat(providerExternalUserCount).isEqualTo(2) //Two existing

        val providerExternalUserDetailCount = reactiveProviderExternalUserDetailRepository.findAll().count().awaitSingle()
        Assertions.assertThat(providerExternalUserDetailCount).isEqualTo(2) //Two existing
      }

    }

    @Test
    fun saveNewUserForNewProviderSameUserDetails() {
      // GIVEN (Above data)

      // WHEN
      val storedUserKeyData = runBlocking {
        custodialWalletDatabaseService.saveNewUserData(
          providerMsaId3,
          providerExternalId3,
          listOf(emailDetail1),
          listOf(
            EncryptedKeyData(
              keypair3.publicKeyBytes,
              EncryptedKey(keypair3.privateKeyBytes, KmsDecryptionKey("keyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)),
              encryptedPrivateKeyType,
              KeyUsageType.ACCOUNT
            ),
            EncryptedKeyData(
              graphKeypair3.publicKeyBytes,
              EncryptedKey(graphKeypair3.privateKeyBytes, KmsDecryptionKey("keyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)),
              encryptedGraphKeyType,
              KeyUsageType.GRAPH
            ),
          )
        )
      }
      // THEN
      runBlocking {
        Assertions.assertThat(storedUserKeyData.id).isNotNull
        val foundUserKeyData = reactiveUserKeyDataRepository.findById(storedUserKeyData.id!!).awaitSingle()
        Assertions.assertThat(foundUserKeyData).isNotNull
        Assertions.assertThat(storedUserKeyData).usingRecursiveComparison().isEqualTo(foundUserKeyData)

        val graphKeyData = reactiveUserKeyDataRepository.findByProviderMsaIdAndProviderExternalIdAndKeyUsageTypeInUserAccount(
          providerMsaId3,
          providerExternalId3,
          KeyUsageType.GRAPH)
          .collectList()
          .awaitSingle()
        Assertions.assertThat(graphKeyData).isNotNull
        Assertions.assertThat(graphKeyData.size).isEqualTo(2) // one for previous msa and another for the new msa
        Assertions.assertThat(graphKeyData[0].keyUsageType).isEqualTo(KeyUsageType.GRAPH)
        Assertions.assertThat(graphKeyData[1].keyUsageType).isEqualTo(KeyUsageType.GRAPH)
        Assertions.assertThat(graphKeyData[0].userAccountId).isEqualTo(storedUserKeyData.userAccountId)
        Assertions.assertThat(graphKeyData[1].userAccountId).isEqualTo(storedUserKeyData.userAccountId)

        val accountKeyData = reactiveUserKeyDataRepository.findByProviderMsaIdAndProviderExternalIdAndKeyUsageTypeInUserAccount(
          providerMsaId3,
          providerExternalId3,
          KeyUsageType.ACCOUNT)
          .collectList()
          .awaitSingle()
        Assertions.assertThat(accountKeyData).isNotNull
        Assertions.assertThat(accountKeyData.size).isEqualTo(2) // one for previous msa and another for the new msa
        Assertions.assertThat(accountKeyData[0].keyUsageType).isEqualTo(KeyUsageType.ACCOUNT)
        Assertions.assertThat(accountKeyData[1].keyUsageType).isEqualTo(KeyUsageType.ACCOUNT)
        Assertions.assertThat(accountKeyData[1]).usingRecursiveComparison().isEqualTo(storedUserKeyData)

        val userKeyDataCount = reactiveUserKeyDataRepository.findAll().count().awaitSingle()
        Assertions.assertThat(userKeyDataCount).isEqualTo(6) //four existing plus two new keys

        val userAccountsCount = reactiveUserAccountRepository.findAll().count().awaitSingle()
        Assertions.assertThat(userAccountsCount).isEqualTo(2) //Two existing

        val providerExternalUserCount = reactiveProviderExternalUserRepository.findAll().count().awaitSingle()
        Assertions.assertThat(providerExternalUserCount).isEqualTo(3) //Two existing plus one new one

        val providerExternalUserDetailCount = reactiveProviderExternalUserDetailRepository.findAll().count().awaitSingle()
        Assertions.assertThat(providerExternalUserDetailCount).isEqualTo(3) //Two existing plus our new one for new provider
      }
    }

    @Test
    fun saveNewUserForNewProviderMatchTwoDifferentAccounts() {
      // GIVEN (Above data)

      // WHEN / THEN
      Assertions.assertThatThrownBy {
        runBlocking {
          custodialWalletDatabaseService.saveNewUserData(
            providerMsaId3,
            providerExternalId3,
            listOf(emailDetail1, emailDetail2),
            listOf(
              EncryptedKeyData(
                keypair3.publicKeyBytes,
                EncryptedKey(keypair3.privateKeyBytes, KmsDecryptionKey("keyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)),
                encryptedPrivateKeyType,
                KeyUsageType.ACCOUNT
              ),
              EncryptedKeyData(
                graphKeypair3.publicKeyBytes,
                EncryptedKey(graphKeypair3.privateKeyBytes, KmsDecryptionKey("keyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)),
                encryptedGraphKeyType,
                KeyUsageType.GRAPH
              ),
            )
          )
        }
      }.isInstanceOf(IllegalStateException::class.java)

      runBlocking {
        val userKeyDataCount = reactiveUserKeyDataRepository.findAll().count().awaitSingle()
        Assertions.assertThat(userKeyDataCount).isEqualTo(4) //Four existing

        val userAccountsCount = reactiveUserAccountRepository.findAll().count().awaitSingle()
        Assertions.assertThat(userAccountsCount).isEqualTo(2) //Two existing

        val providerExternalUserCount = reactiveProviderExternalUserRepository.findAll().count().awaitSingle()
        Assertions.assertThat(providerExternalUserCount).isEqualTo(2) //Two existing

        val providerExternalUserDetailCount = reactiveProviderExternalUserDetailRepository.findAll().count().awaitSingle()
        Assertions.assertThat(providerExternalUserDetailCount).isEqualTo(2) //Two existing
      }

    }

    @Test
    fun saveUserKeyData(): Unit = runBlocking {
      // GIVEN
      val freshUserAccount = reactiveUserAccountRepository.save(UserAccount.create()).awaitSingle()
      val userKeyDataEntity = UserKeyData.create(
        freshUserAccount.id!!,
        graphKeypair4.publicKeyBytes,
        EncryptedKey(graphKeypair4.privateKeyBytes, KmsDecryptionKey("keyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)),
        encryptedGraphKeyType,
        graphKeyUsageType
      )

      // WHEN
      val userKeyDataId = custodialWalletDatabaseService.saveUserKeyData(userKeyDataEntity)

      // THEN
      val savedUserKeyData = reactiveUserKeyDataRepository.findById(userKeyDataId).awaitSingle()
      Assertions.assertThat(savedUserKeyData)

        .usingRecursiveComparison()
        .ignoringFields("id") // `id` is assigned by Postgres when entity is saved
        .isEqualTo(userKeyDataEntity)
    }

    @Test
    fun saveNewUserIdentifierForUserAccount() {

      // GIVEN (Above data)

      // WHEN
      runBlocking {
        custodialWalletDatabaseService.saveNewUserIdentifierForUserAccount(UserDetail(emailDetail3.value, emailDetail3.type, emailDetail3.priority), userAccount1.id!!)
      }

      // THEN
      runBlocking {
        val userAccountsCount = reactiveUserAccountRepository.findAll().count().awaitSingle()
        Assertions.assertThat(userAccountsCount).isEqualTo(2) //Two existing

        val userAccountUserIdentifierCount = reactiveUserAccountUserIdentifierRepository.findAll().count().awaitSingle()
        Assertions.assertThat(userAccountUserIdentifierCount).isEqualTo(3) // Associating 1 more userIdentifier with an account

        val userIdentifierCount = reactiveUserIdentifierRepository.findAll().count().awaitSingle()
        Assertions.assertThat(userIdentifierCount).isEqualTo(3) // 1 new identifier added
      }

    }

    @Test
    fun saveNewIdentifierToAccountDetailAlreadyExists() {
      // GIVEN (Above data)

      // WHEN/THEN
      Assertions.assertThatThrownBy {
        runBlocking {
          custodialWalletDatabaseService.saveNewUserIdentifierForUserAccount(emailDetail2, userAccount1.id!!)
        }
      }.isInstanceOf(DuplicateKeyException::class.java)
    }

    @Test
    fun findByUserIdHash() {
      // GIVEN (above)

      // WHEN
      val foundUserKeyData = runBlocking {
        custodialWalletDatabaseService.findOneUserKeyData(providerMsaId1, providerExternalId1, accountKeyUsageType)
      }

      // THEN
      Assertions.assertThat(foundUserKeyData).isNotNull
      Assertions.assertThat(foundUserKeyData).usingRecursiveComparison().isEqualTo(userKeyDataAccount1)
    }

    @Test
    fun findByUserIdHashNotFound() {
      // GIVEN
      val differentProviderExternalId = "SomeDifferentUserId"

      // WHEN
      val foundUserKeyData = runBlocking {
        custodialWalletDatabaseService.findOneUserKeyData(providerMsaId1, differentProviderExternalId, accountKeyUsageType)
      }

      // THEN
      Assertions.assertThat(foundUserKeyData).isNull()

    }

    @Test
    fun findUserKeyDataByProviderMdaIdAndProviderExternalIdAndKeyUsageTypeNotFound() {
      // GIVEN
      val differentProviderExternalId = "SomeDifferentUserId"

      // WHEN
      val foundUserKeyData = runBlocking {
        custodialWalletDatabaseService.findUserKeyDataByProviderMsaIdAndProviderExternalIdAndKeyUsageType(providerMsaId1, differentProviderExternalId, accountKeyUsageType)
      }

      // THEN
      Assertions.assertThat(foundUserKeyData).isEmpty()
    }

    @Test
    fun findUserKeyDataByProviderMdaIdAndProviderExternalIdAndKeyUsageType() {
      //GIVEN WHEN
      val foundUserKeyData = runBlocking {
        custodialWalletDatabaseService.findUserKeyDataByProviderMsaIdAndProviderExternalIdAndKeyUsageType(providerMsaId1, providerExternalId1, accountKeyUsageType)
      }

      // THEN
      Assertions.assertThat(foundUserKeyData).isNotEmpty
      Assertions.assertThat(foundUserKeyData.single()).usingRecursiveComparison().isEqualTo(userKeyDataAccount1)
    }

    @Test
    fun findUserKeyDataByUserAccountIdAndKeyUsageType(): Unit = runBlocking {
      // GIVEN `populateTables()`

      // WHEN
      val userKeyData = custodialWalletDatabaseService.findUserKeyDataByUserAccountIdAndKeyUsageType(userAccount1.id!!, KeyUsageType.ACCOUNT)

      // THEN
      Assertions.assertThat(userKeyData).hasSize(1)
      Assertions.assertThat(userKeyData.first()).isEqualTo(userKeyDataAccount1)
    }

    @Test
    fun findUserKeyDataByUserAccountIdAndKeyUsageTypeNoMatches(): Unit = runBlocking {
      // GIVEN `populateTables()`
      val unknownUserAccountId = userAccount2.id!! + BigInteger.ONE
      Assertions.assertThat(unknownUserAccountId).isNotIn(userAccount1.id!!, userAccount2.id!!)

      // WHEN
      val userKeyData = custodialWalletDatabaseService.findUserKeyDataByUserAccountIdAndKeyUsageType(unknownUserAccountId, KeyUsageType.ACCOUNT)

      // THEN
      Assertions.assertThat(userKeyData).hasSize(0)
    }

    @Test
    fun findUserKeyDataMissingKeyType() {
      // GIVEN (Above)
      val existingKeyPairType = KeyPairType.SR25519
      val keyUsageType = KeyUsageType.ACCOUNT
      val keyPairTypeToBackfill = KeyPairType.SECP256K1

      //WHEN
      val foundUserKeyData = runBlocking {
        custodialWalletDatabaseService.findUserKeyDataMissingKeyType(existingKeyPairType, keyUsageType,keyPairTypeToBackfill, 2, 0)
      }

      //THEN
      Assertions.assertThat(foundUserKeyData).isNotEmpty
      Assertions.assertThat(foundUserKeyData.size).isEqualTo(2)
      foundUserKeyData.forEach { userKeyData ->
        Assertions.assertThat(userKeyData.encryptedPrivateKeyType).isNotEqualTo(keyPairTypeToBackfill)
      }

    }

    @Test
    fun findUserKeyDataMissingKeyTypeLimitOne() {
      // GIVEN (Above)
      val existingKeyPairType = KeyPairType.SR25519
      val keyUsageType = KeyUsageType.ACCOUNT
      val keyPairTypeToBackfill = KeyPairType.SECP256K1

      //WHEN
      val foundUserKeyData = runBlocking {
        custodialWalletDatabaseService.findUserKeyDataMissingKeyType(existingKeyPairType, keyUsageType,keyPairTypeToBackfill, 1, 0)
      }

      //THEN
      Assertions.assertThat(foundUserKeyData.size).isEqualTo(1)
    }

    @Test
    fun findUserKeyDataMissingKeyTypeOffsetOne() {
      // GIVEN (Above)
      val existingKeyPairType = KeyPairType.SR25519
      val keyUsageType = KeyUsageType.ACCOUNT
      val keyPairTypeToBackfill = KeyPairType.SECP256K1

      //WHEN
      val foundUserKeyData = runBlocking {
        custodialWalletDatabaseService.findUserKeyDataMissingKeyType(existingKeyPairType, keyUsageType,keyPairTypeToBackfill, 1, 1)
      }

      //THEN
      // Due to the ordering of the id output, we should be able to predict the return order
      Assertions.assertThat(foundUserKeyData.size).isEqualTo(1)
      Assertions.assertThat(foundUserKeyData[0].id).isEqualTo(userKeyDataAccount2.id)
    }

    @Test
    fun findUserKeyDataMissingKeyTypeWithSECPKeysExisting() {
      // GIVEN (Above)
      val existingKeyPairType = KeyPairType.SR25519
      val keyUsageType = KeyUsageType.ACCOUNT
      val keyPairTypeToBackfill = KeyPairType.SECP256K1

      // We add an SECP256K1 key for account_id = 1
      // This will cause no user key data from account_id 1 to return.
      runBlocking {
        reactiveUserKeyDataRepository.save(
          UserKeyData.create(
            userAccount1.id!!,
            keypair1.publicKeyBytes,
            EncryptedKey(keypair1.privateKeyBytes, KmsDecryptionKey("keyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)),
            KeyPairType.SECP256K1,
            accountKeyUsageType
          )
        ).awaitSingle()
      }

      //WHEN
      val foundUserKeyData = runBlocking {
        custodialWalletDatabaseService.findUserKeyDataMissingKeyType(existingKeyPairType, keyUsageType,keyPairTypeToBackfill, 2, 0)
      }

      //THEN
      Assertions.assertThat(foundUserKeyData).isNotEmpty
      Assertions.assertThat(foundUserKeyData.size).isEqualTo(1)
      foundUserKeyData.forEach { userKeyData ->
        Assertions.assertThat(userKeyData.encryptedPrivateKeyType).isNotEqualTo(keyPairTypeToBackfill)
      }

    }

    @Test
    @Disabled("Disabled until this function is properly supported")
    fun addNewUserDetail() {
      // GIVEN (Above data)

      // WHEN
      val userKeyDataWithId = runBlocking {
        custodialWalletDatabaseService.saveNewUserData(
          providerMsaId2,
          providerExternalId2,
          listOf(phoneDetail1),
          listOf(
            EncryptedKeyData(
              keypair2.publicKeyBytes,
              EncryptedKey(keypair2.privateKeyBytes, KmsDecryptionKey("keyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)),
              encryptedPrivateKeyType,
              KeyUsageType.ACCOUNT
            ),
            EncryptedKeyData(
              graphKeypair2.publicKeyBytes,
              EncryptedKey(graphKeypair2.privateKeyBytes, KmsDecryptionKey("keyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)),
              encryptedGraphKeyType,
              KeyUsageType.GRAPH
            ),
          )
        )
      }
      // THEN
      runBlocking {
        Assertions.assertThat(userKeyDataWithId.id).isNotNull
        Assertions.assertThat(userKeyDataWithId).usingRecursiveComparison().isEqualTo(userKeyDataAccount2)

        val userKeyDataCount = reactiveUserKeyDataRepository.findAll().count().awaitSingle()
        Assertions.assertThat(userKeyDataCount).isEqualTo(2) //Two existing

        val userAccountsCount = reactiveUserAccountRepository.findAll().count().awaitSingle()
        Assertions.assertThat(userAccountsCount).isEqualTo(2) //Two existing

        val providerExternalUserCount = reactiveProviderExternalUserRepository.findAll().count().awaitSingle()
        Assertions.assertThat(providerExternalUserCount).isEqualTo(2) //Two existing

        val providerExternalUserDetailCount = reactiveProviderExternalUserDetailRepository.findAll().count().awaitSingle()
        Assertions.assertThat(providerExternalUserDetailCount).isEqualTo(3) //Two existing plus our new one
      }

    }

    @ParameterizedTest
    @CsvSource(value = [
      "SR25519, 2",
      "SECP256K1, 1",
      "X25519, 0",
      "PASSKEY_COMPRESSED, 0",
    ])
    fun findUserKeyDataByUserAccountIdKeyUsageTypeAndKeyPairType(keyPairType: KeyPairType, expectedKeyCount: Int): Unit = runBlocking {
      // GIVEN
      val freshUserAccount = reactiveUserAccountRepository.save(UserAccount.create()).awaitSingle()
      val userAccountId = freshUserAccount.id!!

      // 2 SR25519 account key pairs
      listOf(keypair1, keypair2).forEach { keypair ->
        custodialWalletDatabaseService.saveUserKeyData(
          UserKeyData.create(
            userAccountId,
            keypair.publicKeyBytes,
            EncryptedKey(
              keypair.privateKeyBytes,
              KmsDecryptionKey("keyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)
            ),
            KeyPairType.SR25519,
            KeyUsageType.ACCOUNT
          )
        )
      }

      // 1 SECP256k1 account key pair
      val secpKeyPair = Secp256k1KeyPairCreator.createKeyPair()
      custodialWalletDatabaseService.saveUserKeyData(
        UserKeyData.create(
          userAccountId,
          secpKeyPair.publicKeyBytes,
          EncryptedKey(
            secpKeyPair.privateKeyBytes,
            KmsDecryptionKey("keyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)
          ),
          KeyPairType.SECP256K1,
          KeyUsageType.ACCOUNT
        )
      )

      // 1 X25519 graph key pair
      custodialWalletDatabaseService.saveUserKeyData(
        UserKeyData.create(
          userAccountId,
          graphKeypair1.publicKeyBytes,
          EncryptedKey(
            graphKeypair1.privateKeyBytes,
            KmsDecryptionKey("keyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)
          ),
          KeyPairType.X25519,
          KeyUsageType.GRAPH
        )
      )

      // WHEN
      val foundUserKeyData = custodialWalletDatabaseService.findUserKeyDataByUserAccountIdKeyUsageTypeAndKeyPairType(
        userAccountId,
        KeyUsageType.ACCOUNT,
        keyPairType,
      )

      // THEN
      Assertions.assertThat(foundUserKeyData).hasSize(expectedKeyCount)
      foundUserKeyData.forEach { foundData ->
        Assertions.assertThat(foundData.userAccountId).isEqualTo(userAccountId)
        Assertions.assertThat(foundData.encryptedPrivateKeyType).isEqualTo(keyPairType)
        Assertions.assertThat(foundData.keyUsageType).isEqualTo(KeyUsageType.ACCOUNT)
      }
    }

    @Test
    fun createAuditSessionRecord() {
      // Create an audit session record
      val auditSessionRecord = AuditSessionRecord.create("sessionId2", Flow.SIGN_UP_SMS, State.SMS_CODE_REQUESTED, FinalizedState.INCOMPLETE, null)
      val createdRecord = runBlocking {
        custodialWalletDatabaseService.createAuditSessionRecord(auditSessionRecord)
      }
      Assertions.assertThat(createdRecord).isNotNull
      Assertions.assertThat(createdRecord.sessionId).isEqualTo("sessionId2")
      Assertions.assertThat(createdRecord.createdAt).isEqualTo(createdRecord.lastModified)
      Assertions.assertThat(createdRecord.version).isEqualTo(BigInteger.ZERO)
    }

    @Test
    fun findExistingAuditSessionRecordBySessionId() {
      // Find audit session record
      val foundRecord = runBlocking {
        val auditSessionRecord = custodialWalletDatabaseService.findAuditSessionRecordBySessionId(auditSessionRecord.sessionId)
        auditSessionRecord
      }
      Assertions.assertThat(foundRecord).isNotNull
      Assertions.assertThat(foundRecord.sessionId).isEqualTo(sessionId)
      Assertions.assertThat(foundRecord.flow).isEqualTo(Flow.ONBOARD)
      Assertions.assertThat(foundRecord.state).isEqualTo(State.REQUEST_RECEIVED)
      Assertions.assertThat(foundRecord.finalizedState).isEqualTo(FinalizedState.INCOMPLETE)
    }

    @Test
    fun updateExistingAuditSessionRecord() {
      // Update audit session record
      val findByIdRecord = runBlocking { reactiveAuditSessionRecordRepository.findById(auditSessionRecord.id!!).awaitSingle() }
      val updateFindByIdRecord = AuditSessionRecord.update(findByIdRecord, Flow.ONBOARD, State.EMAIL_SENT, FinalizedState.INCOMPLETE, null)
      val updatedRecord = runBlocking {
        custodialWalletDatabaseService.updateAuditSessionRecord(updateFindByIdRecord)
      }
      Assertions.assertThat(updatedRecord.sessionId).isEqualTo(sessionId)
      Assertions.assertThat(updatedRecord.state).isEqualTo(State.EMAIL_SENT)
      Assertions.assertThat(updatedRecord.lastModified).isNotEqualTo(auditSessionRecord.lastModified)
      Assertions.assertThat(updatedRecord.version).isEqualTo(BigInteger.ONE)
    }

    @Test
    fun updatingAuditSessionRecordWithNoIdGiven() {
      //Update record but record given has null id
      val updateRecord = AuditSessionRecord(null, "sessionId2", Flow.LOGIN_SMS, State.REQUEST_RECEIVED, FinalizedState.INCOMPLETE, null, null, null)
      Assertions.assertThatThrownBy {
        runBlocking {
          custodialWalletDatabaseService.updateAuditSessionRecord(updateRecord)
        }
      }.isInstanceOf(IllegalStateException::class.java).hasMessage("Audit session record passed with no id given")
    }

    @Test
    fun findUserKeyDataByKeyPairTypeAndPublicKeys() {
      //WHEN
      val userKeyDataAccounts = runBlocking {
        val publicKeys = listOf(
          toHex(keypair1.publicKeyBytes), toHex(keypair2.publicKeyBytes), toHex(keypair3.publicKeyBytes)
        )
        custodialWalletDatabaseService.findUserKeyDataByKeyPairTypeAndPublicKeys(
          encryptedPrivateKeyType, publicKeys
        )
      }

      //THEN
      Assertions.assertThat(userKeyDataAccounts).hasSize(2)
    }

    @Test
    fun findUserKeyDataByProviderMsaIdAndUserDetail() {
      val userKeyData = runBlocking {
        custodialWalletDatabaseService.findAccountUserKeyDataByProviderMsaIdAndUserDetail(providerMsaId1, emailDetail1)
      }
      Assertions.assertThat(userKeyData).isNotNull
      Assertions.assertThat(userKeyData!!).isEqualTo(userKeyDataAccount1)
    }

    @Test
    fun findUserKeyDataByProviderMsaAndUserDetailReturnsNullWhenUserDetailDoesNotMatch() {
      val userKeyData = runBlocking {
        custodialWalletDatabaseService.findAccountUserKeyDataByProviderMsaIdAndUserDetail(providerMsaId1, emailDetail2)
      }
      Assertions.assertThat(userKeyData).isNull()
    }

    @Test
    fun findUserKeyDataByProviderMsaAndUserDetailAndReturnsNullWhenProviderMsaIdDoesNotMatch() {
      val userKeyData = runBlocking {
        custodialWalletDatabaseService.findAccountUserKeyDataByProviderMsaIdAndUserDetail(providerMsaId2, emailDetail1)
      }
      Assertions.assertThat(userKeyData).isNull()
    }

    @Test
    fun findUserAccountByUserIdentifier() {
      val userAccount = runBlocking {
        custodialWalletDatabaseService.findUserAccountByUserIdentifier(emailDetail1)
      }

      Assertions.assertThat(userAccount).isNotNull
      Assertions.assertThat(userAccount?.id).isEqualTo(providerExternalUserDetail1.userAccountId)
    }

    @Test
    fun findUserAccountByUserIdentifierUnknown() {
      val userAccount = runBlocking {
        custodialWalletDatabaseService.findUserAccountByUserIdentifier(emailDetail3)
      }

      Assertions.assertThat(userAccount).isNull()
    }

    @Test
    fun findOneUserAccountByUserIdentifiers() {
      val userAccount = runBlocking {
        custodialWalletDatabaseService.findOneUserAccountByUserIdentifiers(listOf(emailDetail1, emailDetail3))
      }

      Assertions.assertThat(userAccount).isNotNull
      Assertions.assertThat(userAccount?.id).isEqualTo(providerExternalUserDetail1.userAccountId)
    }

    @Test
    fun findOneUserAccountByUserIdentifiersReverse() {
      val userAccount = runBlocking {
        custodialWalletDatabaseService.findOneUserAccountByUserIdentifiers(listOf(emailDetail3, emailDetail1))
      }

      Assertions.assertThat(userAccount).isNotNull
      Assertions.assertThat(userAccount?.id).isEqualTo(providerExternalUserDetail1.userAccountId)
    }

    @Test
    fun findOneUserAccountByUserIdentifiersUnknown() {
      val userAccount = runBlocking {
        custodialWalletDatabaseService.findOneUserAccountByUserIdentifiers(listOf(emailDetail3))
      }

      Assertions.assertThat(userAccount).isNull()
    }

    @Test
    fun findOneUserAccountByUserIdentifiersMultiple() {
      val userAccount = runBlocking {
        // Supply two user identifiers corresponding to *the same* user account
        custodialWalletDatabaseService.findOneUserAccountByUserIdentifiers(listOf(emailDetail1, emailDetail1))
      }

      Assertions.assertThat(userAccount).isNotNull
      Assertions.assertThat(userAccount?.id).isEqualTo(providerExternalUserDetail1.userAccountId)
    }

    @Test
    fun findOneUserAccountByUserIdentifiersConflicting() {
      Assertions.assertThatThrownBy {
        runBlocking {
          // Supply two user identifiers corresponding to two *different* user accounts
          custodialWalletDatabaseService.findOneUserAccountByUserIdentifiers(listOf(emailDetail1, emailDetail2))
        }
      }.isInstanceOf(IndexOutOfBoundsException::class.java)
    }

    @Test
    fun findUserIdentifiersByUserAccountSingle() {
      val userIdentifiers = runBlocking {
        custodialWalletDatabaseService.findUserIdentifiersByUserAccount(userAccount1.id!!)
      }
      Assertions.assertThat(userIdentifiers.size).isEqualTo(1)
      Assertions.assertThat(userIdentifiers[0].value).isEqualToIgnoringCase(emailDetail1.value)
    }

    @Test
    fun findUserIdentifiersByUserAccountMultiple() {
      runBlocking {
        val userIdentifier3 = reactiveUserIdentifierRepository.save(UserIdentifier.create(emailDetail3)).awaitSingle()
        reactiveUserAccountUserIdentifierRepository.save(UserAccountUserIdentifier(userAccount1.id!!, userIdentifier3.id!!)).awaitSingle()
      }
      val userIdentifiers = runBlocking {
        custodialWalletDatabaseService.findUserIdentifiersByUserAccount(userAccount1.id!!)
      }
      Assertions.assertThat(userIdentifiers.size).isEqualTo(2)
      Assertions.assertThat(userIdentifiers[0].value).isEqualToIgnoringCase(emailDetail1.value)
      Assertions.assertThat(userIdentifiers[1].value).isEqualToIgnoringCase(emailDetail3.value)
    }

    @Test
    fun updateUserIdentifierVerifiedDate() {
      // GIVEN
      val savedUserIdentifier = runBlocking {
        reactiveUserIdentifierRepository.save(UserIdentifier.create(emailDetail3)).awaitSingle()
      }

      // WHEN
      runBlocking {
        custodialWalletDatabaseService.updateUserIdentifierVerifiedDate(
          UserDetail(savedUserIdentifier.value, savedUserIdentifier.type)
        )
      }

      // THEN
      val updatedUserIdentifier = runBlocking {
        reactiveUserIdentifierRepository.findById(savedUserIdentifier.id!!).awaitSingleOrNull()
      }
      Assertions.assertThat(updatedUserIdentifier!!.verifiedDate).isGreaterThan(savedUserIdentifier.verifiedDate)
    }

    @Test
    fun saveProviderExternalUserDetailForExistingUserWhenGivenNewEmail() {
      val newEmailUserDetail = UserDetail("thisisatest@test.com", UserDetailType.EMAIL, 0)
      val savedUserDetail = runBlocking {
        custodialWalletDatabaseService.saveUserIdentifierAndProviderExternalUserDetail(providerExternalUser1.id!!, userAccount1.id!!, newEmailUserDetail)
      }

      Assertions.assertThat(savedUserDetail.userDetailValue).isEqualToIgnoringCase(newEmailUserDetail.value)
      Assertions.assertThat(savedUserDetail.userDetailType).isEqualTo(newEmailUserDetail.type)
      Assertions.assertThat(savedUserDetail.userDetailPriority).isEqualTo(newEmailUserDetail.priority)

      //Verifying that newly added user detail updates the existing user, and doesn't overwrite an existing userDetail for that user
      val existingDetail = runBlocking {
        reactiveProviderExternalUserDetailRepository.findById(providerExternalUserDetail1.id!!).awaitSingle()
      }
      val newlyAddedDetail = runBlocking {
        reactiveProviderExternalUserDetailRepository.findById(savedUserDetail.id!!).awaitSingle()
      }

      Assertions.assertThat(existingDetail.userDetailValue).isEqualToIgnoringCase(emailDetail1.value)
      Assertions.assertThat(existingDetail.userDetailType).isEqualTo(UserDetailType.EMAIL)
      Assertions.assertThat(newlyAddedDetail.userDetailValue).isEqualToIgnoringCase(newEmailUserDetail.value)
      Assertions.assertThat(newlyAddedDetail.userDetailType).isEqualTo(newEmailUserDetail.type)
    }

    @Test
    fun saveProviderExternalUserDetailForExistingUserWhenGivenNewPhoneNumber() {
      val newPhoneUserDetail = UserDetail("6789012345", UserDetailType.PHONE_NUMBER, 0)
      val savedUserDetail = runBlocking {
        custodialWalletDatabaseService.saveUserIdentifierAndProviderExternalUserDetail(providerExternalUser1.id!!, userAccount1.id!!, newPhoneUserDetail)
      }

      Assertions.assertThat(savedUserDetail.userDetailValue).isEqualTo(newPhoneUserDetail.value)
      Assertions.assertThat(savedUserDetail.userDetailType).isEqualTo(newPhoneUserDetail.type)
      Assertions.assertThat(savedUserDetail.userDetailPriority).isEqualTo(newPhoneUserDetail.priority)

      //Verifying that newly added user detail updates the existing user, and doesn't overwrite an existing userDetail for that user
      val existingDetail = runBlocking {
        reactiveProviderExternalUserDetailRepository.findById(providerExternalUserDetail1.id!!).awaitSingle()
      }
      val newlyAddedDetail = runBlocking {
        reactiveProviderExternalUserDetailRepository.findById(savedUserDetail.id!!).awaitSingle()
      }

      Assertions.assertThat(existingDetail.userDetailValue).isEqualToIgnoringCase(emailDetail1.value)
      Assertions.assertThat(existingDetail.userDetailType).isEqualTo(UserDetailType.EMAIL)
      Assertions.assertThat(newlyAddedDetail.userDetailValue).isEqualTo(newPhoneUserDetail.value)
      Assertions.assertThat(newlyAddedDetail.userDetailType).isEqualTo(newPhoneUserDetail.type)
    }

    @Test
    fun saveUserIdentifierAndProviderExternalUserDetailForExistingProviderExternalUser() {
      Assertions.assertThatThrownBy {
        runBlocking {
          runBlocking {
            custodialWalletDatabaseService.saveUserIdentifierAndProviderExternalUserDetail(providerExternalUser1.id!!, userAccount1.id!!, emailDetail1)
          }
        }
      }.isInstanceOf(DuplicateKeyException::class.java)
        .hasMessageContaining("duplicate key value violates unique constraint \"provider_external_user_detail_provider_external_user_id_use_key\"")
    }

    @Test
    fun saveUserIdentifierAndProviderExternalUserDetailForDifferentProviderExternalUserExistingUserDetail() {
      // Requires a distinct ProviderExternalUser, otherwise SQL constraints get broken
      val newProviderExternalUser = runBlocking {
        reactiveProviderExternalUserRepository.save(
          ProviderExternalUser.create(
            providerExternalUser1.providerMsaId, "different-provider-external-id", userKeyDataAccount1.id!!
          )
        ).awaitSingle()
      }

      val savedUserDetail = runBlocking {
        custodialWalletDatabaseService.saveUserIdentifierAndProviderExternalUserDetail(
          newProviderExternalUser.id!!,
          userAccount1.id!!,
          emailDetail1
        )
      }

      Assertions.assertThat(savedUserDetail.userDetailValue).isEqualToIgnoringCase(emailDetail1.value)
      Assertions.assertThat(savedUserDetail.userDetailType).isEqualTo(emailDetail1.type)
      Assertions.assertThat(savedUserDetail.userDetailPriority).isEqualTo(emailDetail1.priority)

      // Assert that a new duplicate user detail is created
      Assertions.assertThat(savedUserDetail.id!!).isNotEqualTo(providerExternalUserDetail1.id!!)

      val existingDetail = runBlocking {
        reactiveProviderExternalUserDetailRepository.findById(providerExternalUserDetail1.id!!).awaitSingle()
      }
      Assertions.assertThat(existingDetail.userDetailValue).isEqualToIgnoringCase(emailDetail1.value)
      Assertions.assertThat(existingDetail.userDetailType).isEqualTo(emailDetail1.type)

      val newlyAddedDetail = runBlocking {
        reactiveProviderExternalUserDetailRepository.findById(savedUserDetail.id!!).awaitSingle()
      }
      Assertions.assertThat(newlyAddedDetail.userDetailValue).isEqualToIgnoringCase(emailDetail1.value)
      Assertions.assertThat(newlyAddedDetail.userDetailType).isEqualTo(emailDetail1.type)

    }

    @Test
    fun saveProviderExternalUser(): Unit = runBlocking {
      //GIVEN
      val desiredExternalUserId = "desiredExternalUserId"
      val lastModified = Instant.now().toEpochMilli().toBigInteger()
      val expectedProviderExternalUser = ProviderExternalUser(providerExternalUser1.id,
        providerExternalUser1.providerMsaId,
        desiredExternalUserId,
        providerExternalUser1.userKeyDataId,
        providerExternalUser1.createdAt,
        lastModified,
        providerExternalUser1.version
        )

      //WHEN
      val persisted = custodialWalletDatabaseService.saveProviderExternalUser(expectedProviderExternalUser)

      //THEN
      Assertions.assertThat(persisted.id).isEqualTo(persisted.id)
      Assertions.assertThat(persisted.providerMsaId).isEqualTo(persisted.providerMsaId)
      Assertions.assertThat(persisted.providerExternalId).isEqualTo(desiredExternalUserId)
      Assertions.assertThat(persisted.userKeyDataId).isEqualTo(expectedProviderExternalUser.userKeyDataId)
      Assertions.assertThat(persisted.createdAt).isEqualTo(expectedProviderExternalUser.createdAt)
      Assertions.assertThat(persisted.lastModified).isEqualTo(expectedProviderExternalUser.lastModified)
      Assertions.assertThat(persisted.version).isGreaterThan(providerExternalUser1.version)
    }

    @Test
    fun findUserDataByUserAccountIdsAndUserDetail() {
      val userData = runBlocking {
        custodialWalletDatabaseService.findUserDataByUserAccountIdsAndUserDetail(emailDetail1, listOf(userAccount1.id!!))
      }
      Assertions.assertThat(userData).isNotEmpty
      Assertions.assertThat(userData[0].providerMsaId).isEqualTo(providerMsaId1)
      Assertions.assertThat(userData[0].userAccountId).isEqualTo(userAccount1.id)
      Assertions.assertThat(userData[0].publicKeyHex).isEqualTo(userKeyDataAccount1.publicKeyHex)
      Assertions.assertThat(userData[0].userDetailValue).isEqualToIgnoringCase(emailDetail1.value)
    }

    @Test
    fun findUserDataByUserAccountIds() {
      val userData = runBlocking {
        custodialWalletDatabaseService.findUserDataByUserAccountIds(listOf(userAccount1.id!!))
      }
      Assertions.assertThat(userData).isNotEmpty
      Assertions.assertThat(userData[0].providerMsaId).isEqualTo(providerMsaId1)
      Assertions.assertThat(userData[0].userAccountId).isEqualTo(userAccount1.id)
      Assertions.assertThat(userData[0].publicKeyHex).isEqualTo(userKeyDataAccount1.publicKeyHex)
      Assertions.assertThat(userData[0].userDetailValue).isEqualToIgnoringCase(emailDetail1.value)
    }

    @Test
    fun findUserDataByUserAccountIdsMultipleUserDetails(): Unit = runBlocking {
      custodialWalletDatabaseService.saveUserIdentifierAndProviderExternalUserDetail(providerExternalUser1.id!!, userAccount1.id!!, UserDetail("new@email.com", UserDetailType.EMAIL, 1))
      custodialWalletDatabaseService.saveUserIdentifierAndProviderExternalUserDetail(providerExternalUser1.id!!, userAccount1.id!!, UserDetail("+10987654321", UserDetailType.PHONE_NUMBER, 2))
      val userData = custodialWalletDatabaseService.findUserDataByUserAccountIds(listOf(userAccount1.id!!))
      Assertions.assertThat(userData).isNotEmpty
      Assertions.assertThat(userData[0].providerMsaId).isEqualTo(providerMsaId1)
      Assertions.assertThat(userData[0].userAccountId).isEqualTo(userAccount1.id)
      Assertions.assertThat(userData[0].publicKeyHex).isEqualTo(userKeyDataAccount1.publicKeyHex)
      Assertions.assertThat(userData[0].userDetailValue).isEqualToIgnoringCase(emailDetail1.value)
      Assertions.assertThat(userData[1].providerMsaId).isEqualTo(providerMsaId1)
      Assertions.assertThat(userData[1].userAccountId).isEqualTo(userAccount1.id)
      Assertions.assertThat(userData[1].publicKeyHex).isEqualTo(userKeyDataAccount1.publicKeyHex)
      Assertions.assertThat(userData[1].userDetailValue).isEqualTo("new@email.com")
      Assertions.assertThat(userData[1].userDetailType).isEqualTo(UserDetailType.EMAIL)
      Assertions.assertThat(userData[2].userDetailValue).isEqualTo("+10987654321")
      Assertions.assertThat(userData[2].userDetailType).isEqualTo(UserDetailType.PHONE_NUMBER)
    }

    @Test
    fun findUserDetailsByProviderMsaIdAndProviderExternalUserId(): Unit = runBlocking {
      custodialWalletDatabaseService.saveUserIdentifierAndProviderExternalUserDetail(providerExternalUser1.id!!, userAccount1.id!!, UserDetail("new@email.com", UserDetailType.EMAIL, 1))
      custodialWalletDatabaseService.saveUserIdentifierAndProviderExternalUserDetail(providerExternalUser1.id!!, userAccount1.id!!, UserDetail("+10987654321", UserDetailType.PHONE_NUMBER, 2))
      val userDetailList = custodialWalletDatabaseService.findUserDetailsByProviderMsaIdAndKeyPairTypeAndPublicKeyHexAndKeyUsageType(
        BigInteger.ONE, userKeyDataAccount1.encryptedPrivateKeyType, userKeyDataAccount1.publicKeyHex, userKeyDataAccount1.keyUsageType)

      Assertions.assertThat(userDetailList).isNotEmpty
      Assertions.assertThat(userDetailList[0].providerExternalUserId).isEqualTo(userDetailList[1].providerExternalUserId)
      Assertions.assertThat(userDetailList[0].providerExternalUserId).isEqualTo(userDetailList[2].providerExternalUserId)
      Assertions.assertThat(userDetailList[0].userDetailValue).isEqualToIgnoringCase("saas-dev@unfinished.com")
      Assertions.assertThat(userDetailList[1].userDetailValue).isEqualTo("new@email.com")
      Assertions.assertThat(userDetailList[2].userDetailValue).isEqualTo("+10987654321")
    }

    @Nested
    @DisplayName("User Password Tests")
    inner class UserPasswordTests {
      private lateinit var storedUserKeyData: UserKeyData
      private lateinit var reactiveProviderExternalUserDetailData: ProviderExternalUserDetail
      private lateinit var keypair: Sr25519KeyPairBytes
      private lateinit var keyUsageType: KeyUsageType

      private val userPasswordHash1 = "\$2b\$10\$//DXiVVE59p7G5k/4Klx/ezF7BI42QZKmoOD0NDvUuqxRE5bFFBLy"
      private val userPasswordHash2 = "\$2a\$12\$//DXiVVE59p7G5k/4Klx/ezF7BI42QZKmoOD437gh80wgh984w3hf"

      @BeforeEach
      fun storeInitialData() = runBlocking{
        val denormalizedEmailDetail3 = UserDetail(emailDetail3.value, emailDetail3.type, emailDetail3.priority)
        storedUserKeyData = custodialWalletDatabaseService.saveNewUserData(
          providerMsaId3,
          providerExternalId3,
          listOf(denormalizedEmailDetail3),
          listOf(
            EncryptedKeyData(
              keypair3.publicKeyBytes,
              EncryptedKey(keypair3.privateKeyBytes, KmsDecryptionKey("keyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)),
              encryptedPrivateKeyType,
              KeyUsageType.ACCOUNT
            ),
            EncryptedKeyData(
              graphKeypair3.publicKeyBytes,
              EncryptedKey(graphKeypair3.privateKeyBytes, KmsDecryptionKey("keyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)),
              encryptedGraphKeyType,
              KeyUsageType.GRAPH
            ),
          )
        )
        Assertions.assertThat(storedUserKeyData.id).isNotNull

        reactiveProviderExternalUserDetailData = reactiveProviderExternalUserDetailRepository.findByUserDetailValueAndUserDetailType(emailDetail3.value, emailDetail3.type).singleOrEmpty().awaitSingle()

        keypair = Sr25519KeyPairCreator.createKeyPair()
        keyUsageType = KeyUsageType.ACCOUNT

      }

      private fun saveNewUserPassword(): UserPassword = runBlocking {
        val userPassword = UserPassword.create(storedUserKeyData.userAccountId, KeyDerivationAlgorithmType.BCRYPT, userPasswordHash1)
        custodialWalletDatabaseService.saveUserPassword(userPassword)
        val existingRowCount = reactiveUserPasswordRepository.findAll().count().awaitSingle()
        Assertions.assertThat(existingRowCount).isEqualTo(1)

        val storedUserPassword = custodialWalletDatabaseService.findOneUserPasswordById(userPassword.id!!)!!
        Assertions.assertThat(storedUserPassword.hash).isEqualTo(userPasswordHash1)
        Assertions.assertThat(storedUserPassword.userAccountId).isEqualTo(storedUserKeyData.userAccountId)

        return@runBlocking storedUserPassword
      }

      @Test
      fun updateExistingUserPasswordSuccessfully(): Unit = runBlocking {
        val storedUserPassword = saveNewUserPassword()
        val foundStoredUserPassword = custodialWalletDatabaseService.findOneUserPasswordById(storedUserPassword.id!!)!!
        val userPasswordToUpdate = UserPassword.update(foundStoredUserPassword, KeyDerivationAlgorithmType.BCRYPT, userPasswordHash2)
        custodialWalletDatabaseService.saveUserPassword(userPasswordToUpdate)


        val updatedUserPassword = custodialWalletDatabaseService.findOneUserPasswordById(storedUserPassword.id!!)!!
        Assertions.assertThat(storedUserPassword.userAccountId).isEqualTo(updatedUserPassword.userAccountId)
        Assertions.assertThat(updatedUserPassword.hash).isEqualTo(userPasswordHash2)
        Assertions.assertThat(updatedUserPassword.createdAt).isEqualTo(storedUserPassword.createdAt)
        Assertions.assertThat(updatedUserPassword.lastModified).isNotEqualTo(storedUserPassword.lastModified)
      }

      @Test
      fun deleteExistingUserPasswordSuccessfully(): Unit = runBlocking {
        val storedUserPassword = saveNewUserPassword()
        custodialWalletDatabaseService.deleteOneUserPasswordById(storedUserPassword.id!!)
        Assertions.assertThat(reactiveUserPasswordRepository.findAll().count().awaitSingle()).isEqualTo(0)
        val foundNoPassword = custodialWalletDatabaseService.findOneUserPasswordById(storedUserPassword.id!!)
        Assertions.assertThat(foundNoPassword).isNull()

        //If a password doesn't exist for the user, delete does nothing
        custodialWalletDatabaseService.deleteOneUserPasswordById(storedUserPassword.id!!)
        Assertions.assertThat(reactiveUserPasswordRepository.findAll().count().awaitSingle()).isEqualTo(0)
      }


      @Test
      fun findExistingUserPasswordByUserAccountIdSuccessfully(): Unit = runBlocking {
        val storedUserPassword = saveNewUserPassword()
        val foundUserPassword = custodialWalletDatabaseService.findOneUserPasswordByUserAccountId(storedUserPassword.userAccountId)!!
        Assertions.assertThat(foundUserPassword.keyDerivationAlgorithmType).isEqualTo(storedUserPassword.keyDerivationAlgorithmType)
        Assertions.assertThat(foundUserPassword.hash).isEqualTo(storedUserPassword.hash)
      }

      @Test
      fun findExistingUserPasswordByUserAccountIdAfterUpdatePassword(): Unit = runBlocking {
        val storedUserPassword = saveNewUserPassword()
        val foundStoredUserPassword = custodialWalletDatabaseService.findOneUserPasswordByUserAccountId(storedUserPassword.userAccountId)!!
        val userPasswordToUpdate = UserPassword.update(foundStoredUserPassword, KeyDerivationAlgorithmType.BCRYPT, userPasswordHash2)
        custodialWalletDatabaseService.saveUserPassword(userPasswordToUpdate)
        val updatedUserPassword = custodialWalletDatabaseService.findOneUserPasswordByUserAccountId(storedUserPassword.userAccountId)!!

        Assertions.assertThat(updatedUserPassword.keyDerivationAlgorithmType).isEqualTo(storedUserPassword.keyDerivationAlgorithmType)
        Assertions.assertThat(updatedUserPassword.hash).isNotEqualTo(storedUserPassword.hash)
        Assertions.assertThat(updatedUserPassword.hash).isEqualTo(userPasswordToUpdate.hash)
      }

      @Test
      fun findExistingUserPasswordByUserAccountIdNoUserPasswordFound(): Unit = runBlocking {
        val noUserPasswordFound = custodialWalletDatabaseService.findOneUserPasswordByUserAccountId(BigInteger.TEN)
        Assertions.assertThat(noUserPasswordFound).isNull()
        val existingRowCount = reactiveUserPasswordRepository.findAll().count().awaitSingle()
        Assertions.assertThat(existingRowCount).isEqualTo(0)

        //Now add a password so there are rows that exist, but this password still doesn't exist
        val storedUserPassword = saveNewUserPassword()
        val foundStoredUserPassword = custodialWalletDatabaseService.findOneUserPasswordByUserAccountId(storedUserPassword.userAccountId)!!
        Assertions.assertThat(foundStoredUserPassword).isNotNull
        Assertions.assertThat(foundStoredUserPassword.userAccountId).isNotEqualTo(BigInteger.TEN)
        val existingRowCountAfterAddingPassword = reactiveUserPasswordRepository.findAll().count().awaitSingle()
        Assertions.assertThat(existingRowCountAfterAddingPassword).isEqualTo(1)
        val stillNoUserPasswordFound = custodialWalletDatabaseService.findOneUserPasswordByUserAccountId(BigInteger.TEN)
        Assertions.assertThat(stillNoUserPasswordFound).isNull()
      }

      @Test
      fun findUserPasswordByPublicKeyHexSuccessfully(): Unit = runBlocking {
        val storedUserPassword = saveNewUserPassword()
        val foundUserPassword = custodialWalletDatabaseService.findOneUserPasswordByPublicKeyHex(storedUserKeyData.publicKeyHex)!!
        Assertions.assertThat(foundUserPassword.keyDerivationAlgorithmType).isEqualTo(storedUserPassword.keyDerivationAlgorithmType)
        Assertions.assertThat(foundUserPassword.hash).isEqualTo(storedUserPassword.hash)
      }

      @Test
      fun findExistingUserPasswordByPublicKeyHexAfterUpdatePassword(): Unit = runBlocking {
        val storedUserPassword = saveNewUserPassword()
        val foundStoredUserPassword = custodialWalletDatabaseService.findOneUserPasswordByPublicKeyHex(storedUserKeyData.publicKeyHex)!!
        val userPasswordToUpdate = UserPassword.update(foundStoredUserPassword, KeyDerivationAlgorithmType.BCRYPT, userPasswordHash2)
        custodialWalletDatabaseService.saveUserPassword(userPasswordToUpdate)
        val updatedUserPassword = custodialWalletDatabaseService.findOneUserPasswordByPublicKeyHex(storedUserKeyData.publicKeyHex)!!

        Assertions.assertThat(updatedUserPassword.keyDerivationAlgorithmType).isEqualTo(storedUserPassword.keyDerivationAlgorithmType)
        Assertions.assertThat(updatedUserPassword.hash).isNotEqualTo(storedUserPassword.hash)
        Assertions.assertThat(updatedUserPassword.hash).isEqualTo(userPasswordToUpdate.hash)
      }

      @Test
      fun findUserPasswordByPublicKeyHexNoUserPasswordFound(): Unit = runBlocking {
        val fakePublicKeyHex = "7g0qh34jf-9q8h4=jmqgqh4g79q03ph28r0[jwafn"
        val noUserPasswordFound = custodialWalletDatabaseService.findOneUserPasswordByPublicKeyHex(fakePublicKeyHex)
        Assertions.assertThat(noUserPasswordFound).isNull()
        val existingRowCount = reactiveUserPasswordRepository.findAll().count().awaitSingle()
        Assertions.assertThat(existingRowCount).isEqualTo(0)

        //Now add a password so there are rows that exist, but this password still doesn't exist
        saveNewUserPassword()
        val foundStoredUserPassword = custodialWalletDatabaseService.findOneUserPasswordByPublicKeyHex(storedUserKeyData.publicKeyHex)!!
        Assertions.assertThat(foundStoredUserPassword).isNotNull
        val existingRowCountAfterAddingPassword = reactiveUserPasswordRepository.findAll().count().awaitSingle()
        Assertions.assertThat(existingRowCountAfterAddingPassword).isEqualTo(1)
        val stillNoUserPasswordFound = custodialWalletDatabaseService.findOneUserPasswordByPublicKeyHex(fakePublicKeyHex)
        Assertions.assertThat(stillNoUserPasswordFound).isNull()
      }

      @Test
      fun findUserPasswordByProviderMsaIdAndProviderExternalIdSuccessfully(): Unit = runBlocking {
        val storedUserPassword = saveNewUserPassword()
        val foundUserPassword = custodialWalletDatabaseService.findOneUserPasswordByProviderMsaIdAndProviderExternalId(providerMsaId3, providerExternalId3)!!
        Assertions.assertThat(foundUserPassword.keyDerivationAlgorithmType).isEqualTo(storedUserPassword.keyDerivationAlgorithmType)
        Assertions.assertThat(foundUserPassword.hash).isEqualTo(storedUserPassword.hash)
      }

      @Test
      fun findUserPasswordByProviderMsaIdAndProviderExternalIdAfterUpdatePassword(): Unit = runBlocking {
        val storedUserPassword = saveNewUserPassword()
        val foundStoredUserPassword = custodialWalletDatabaseService.findOneUserPasswordByProviderMsaIdAndProviderExternalId(providerMsaId3, providerExternalId3)!!
        val userPasswordToUpdate = UserPassword.update(foundStoredUserPassword, KeyDerivationAlgorithmType.BCRYPT, userPasswordHash2)
        custodialWalletDatabaseService.saveUserPassword(userPasswordToUpdate)
        val updatedUserPassword = custodialWalletDatabaseService.findOneUserPasswordByProviderMsaIdAndProviderExternalId(providerMsaId3, providerExternalId3)!!

        Assertions.assertThat(updatedUserPassword.keyDerivationAlgorithmType).isEqualTo(storedUserPassword.keyDerivationAlgorithmType)
        Assertions.assertThat(updatedUserPassword.hash).isNotEqualTo(storedUserPassword.hash)
        Assertions.assertThat(updatedUserPassword.hash).isEqualTo(userPasswordToUpdate.hash)
      }

      @Test
      fun findUserPasswordByProviderMsaIdAndProviderExternalIdNoPasswordFound(): Unit = runBlocking {
        val noUserPasswordFound = custodialWalletDatabaseService.findOneUserPasswordByProviderMsaIdAndProviderExternalId(BigInteger.TEN, "someDifferentExternalId")
        Assertions.assertThat(noUserPasswordFound).isNull()
        val existingRowCount = reactiveUserPasswordRepository.findAll().count().awaitSingle()
        Assertions.assertThat(existingRowCount).isEqualTo(0)

        //Now add a password so there are rows that exist, but this password still doesn't exist
        saveNewUserPassword()
        val foundStoredUserPassword = custodialWalletDatabaseService.findOneUserPasswordByProviderMsaIdAndProviderExternalId(providerMsaId3, providerExternalId3)
        Assertions.assertThat(foundStoredUserPassword).isNotNull
        val existingRowCountAfterAddingPassword = reactiveUserPasswordRepository.findAll().count().awaitSingle()
        Assertions.assertThat(existingRowCountAfterAddingPassword).isEqualTo(1)
        val stillNoUserPasswordFound = custodialWalletDatabaseService.findOneUserPasswordByProviderMsaIdAndProviderExternalId(BigInteger.TEN, "someDifferentExternalId")
        Assertions.assertThat(stillNoUserPasswordFound).isNull()
      }
    }
  }

  @Nested
  @DisplayName("Partial Account")
  inner class PartialAccountTests {
    private val keyPair = Sr25519KeyPairCreator.createKeyPair()
    private val keyPairType = KeyPairType.SR25519
    private val userDetail = UserDetail("simple.deletion@unfinished.com", UserDetailType.EMAIL, 1)
    private val providerMsaId = 4.toBigInteger()
    private val providerExternalId = userDetail.value

    private lateinit var userAccount: UserAccount

    @BeforeEach
    fun saveUserAccount() {
      runBlocking {
        userAccount = reactiveUserAccountRepository.save(UserAccount.create()).awaitSingle()
      }
    }

    @Test
    fun deleteUserAccount() {
      runBlocking {
        // WHEN
        custodialWalletDatabaseService.deleteUserAccountByUserAccountId(userAccount.id!!)

        // THEN
        val reactiveUserAccountDataAfterDelete = reactiveUserAccountRepository.findById(userAccount.id!!).awaitSingleOrNull()
        Assertions.assertThat(reactiveUserAccountDataAfterDelete).isNull()
      }
    }

    @Nested
    @DisplayName("With UserKeyData")
    inner class WithUserKeyData {
      private lateinit var userKeyData: UserKeyData

      @BeforeEach
      fun saveUserKeyData() {
        runBlocking {
          userKeyData = reactiveUserKeyDataRepository.save(
            UserKeyData.create(
              userAccount.id!!,
              keyPair.publicKeyBytes,
              EncryptedKey(keyPair.privateKeyBytes, KmsDecryptionKey("keyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)),
              keyPairType,
              KeyUsageType.ACCOUNT
            )
          ).awaitSingle()
        }
      }

      @Test
      fun deleteUserKeyData() = runBlocking {
        // WHEN
        custodialWalletDatabaseService.deleteUserKeyDataByUserAccountId(userAccount.id!!)

        // THEN
        val reactiveUserKeyDataAfterDelete =
          reactiveUserKeyDataRepository.findByUserAccountId(userAccount.id!!).collectList().awaitSingleOrNull()
        Assertions.assertThat(reactiveUserKeyDataAfterDelete).isEmpty()
      }

      @Nested
      @DisplayName("With ProviderExternalUser")
      inner class WithProviderExternalUser {
        private lateinit var providerExternalUser: ProviderExternalUser

        @BeforeEach
        fun saveProviderExternalUser() {
          runBlocking {
            providerExternalUser = reactiveProviderExternalUserRepository.save(
              ProviderExternalUser.create(
                providerMsaId, providerExternalId, userKeyData.id!!
              )
            ).awaitSingle()
          }
        }

        @Test
        fun saveUserIdentifierAndProviderExternalUserDetail(): Unit = runBlocking {
          // WHEN
          val returnedProviderExternalUserDetail = custodialWalletDatabaseService.saveUserIdentifierAndProviderExternalUserDetail(
            providerExternalUser.id!!,
            userAccount.id!!,
            userDetail
          )

          // THEN
          // Fetch expected rows from the DB
          val dbUserAccountUserIdentifier = reactiveUserAccountUserIdentifierRepository
            .findByUserAccountId(userAccount.id!!)
            .awaitFirst()
          val dbUserIdentifier = reactiveUserIdentifierRepository
            .findById(dbUserAccountUserIdentifier.userIdentifierId)
            .awaitSingle()
          val dbProviderExternalUserDetail = reactiveProviderExternalUserDetailRepository
            .findByUserAccountIdIn(listOf(userAccount.id!!))
            .awaitFirstOrNull()!!

          // Assert rows have expected values
          Assertions.assertThat(dbUserAccountUserIdentifier.userAccountId).isEqualTo(userAccount.id!!)
          Assertions.assertThat(dbUserAccountUserIdentifier.userIdentifierId).isEqualTo(dbUserIdentifier.id!!)

          Assertions.assertThat(dbUserIdentifier.value).isEqualTo(userDetail.value)
          Assertions.assertThat(dbUserIdentifier.type).isEqualTo(userDetail.type)

          Assertions.assertThat(dbProviderExternalUserDetail.providerExternalUserId).isEqualTo(providerExternalUser.id)
          Assertions.assertThat(dbProviderExternalUserDetail.userAccountId).isEqualTo(userAccount.id!!)
          Assertions.assertThat(dbProviderExternalUserDetail.userDetailValue).isEqualTo(userDetail.value)
          Assertions.assertThat(dbProviderExternalUserDetail.userDetailType).isEqualTo(userDetail.type)
          Assertions.assertThat(dbProviderExternalUserDetail.userDetailPriority).isEqualTo(userDetail.priority)
          Assertions.assertThat(dbProviderExternalUserDetail.userIdentifierId).isEqualTo(dbUserIdentifier.id!!)

          // Assert returned object matched saved row
          Assertions.assertThat(returnedProviderExternalUserDetail).usingRecursiveComparison().isEqualTo(dbProviderExternalUserDetail)
        }

        @Test
        fun deleteProviderExternalUser() = runBlocking {
          // WHEN
          custodialWalletDatabaseService.deleteProviderExternalUserById(providerExternalUser.id!!)

          // THEN
          val reactiveProviderExternalUserDataAfterDelete =
            reactiveProviderExternalUserRepository.findById(providerExternalUser.id!!).awaitSingleOrNull()
          Assertions.assertThat(reactiveProviderExternalUserDataAfterDelete).isNull()
        }


        @Test
        fun deleteProviderExternalUserDetail() = runBlocking {
          // GIVEN
          val userIdentifier = reactiveUserIdentifierRepository.save(UserIdentifier.create(userDetail)).awaitSingle()
          reactiveUserAccountUserIdentifierRepository.save(
            UserAccountUserIdentifier(
              userAccount.id!!, userIdentifier.id!!
            )
          )
          val providerExternalUserDetail = reactiveProviderExternalUserDetailRepository.save(
            ProviderExternalUserDetail.create(
              providerExternalUser.id!!, userAccount.id!!, userDetail, userIdentifier.id!!
            )
          ).awaitSingle()

          // WHEN
          custodialWalletDatabaseService.deleteProviderExternalUserDetailById(providerExternalUserDetail?.id!!)

          // THEN
          val reactiveProviderExternalUserDetailDataAfterDelete =
            reactiveProviderExternalUserDetailRepository.findByUserDetailValueAndUserDetailType(
                userDetail.value,
                userDetail.type
              ).singleOrEmpty().awaitSingleOrNull()
          Assertions.assertThat(reactiveProviderExternalUserDetailDataAfterDelete).isNull()
        }
      }
    }
  }

  @Nested
  @DisplayName("Existing Account")
  inner class ExistingAccountTests {
    private val providerMsaId = 1.toBigInteger()

    // Test data for the 'primary' user account
    private val primaryUserProviderExternalId = "primary@unfinished.com"
    private val primaryUserDetail = UserDetail(primaryUserProviderExternalId, UserDetailType.EMAIL)
    private val primaryUserProps = TestUserProps(
      providerMsaId,
      primaryUserProviderExternalId,
      primaryUserDetail,
      Sr25519KeyPairCreator.createKeyPair(),
      Sr25519KeyPairCreator.createKeyPair()
    )

    // Test data for an unrecognized user that is not in the database
    private val unknownUserDetail = UserDetail("unknown@unfinished.com", UserDetailType.EMAIL, 0)

    // Fixtures
    private lateinit var primaryUser: SavedTestUser

    @BeforeEach
    fun storeInitialData() = runBlocking {
      primaryUser = testUserHelper.insertTestUser(primaryUserProps)
    }

    private fun base64Encode(bytes: ByteArray): String {
      return Base64.getUrlEncoder().encodeToString(bytes)
    }

    private fun createRandomCredential() = Credential.create(
      authenticatorUuid = UUID.randomUUID(),
      credentialIdBase64Url = base64Encode(Random.nextBytes(1024)),
      publicKeyBase64Url = base64Encode(Random.nextBytes(1024)),
      compressedPublicKeyBase64Url = base64Encode(Random.nextBytes(1024)),
      signCount = 0,
      backupEligible = true,
      backedUp = true,
      transports = setOf("example-transport-1")
    )

    private fun assertPasskeyWalletsEqual(actual: PasskeyWallet?, expected: PasskeyWallet) {
      Assertions.assertThat(actual?.wallet)
        .usingRecursiveComparison().ignoringFields("id", "version")
        .isEqualTo(expected.wallet)
      Assertions.assertThat(actual?.credential)
        .usingRecursiveComparison().ignoringFields("id", "walletId", "version", "walletMetadata.id")
        .isEqualTo(expected.credential)
    }

    @Test
    fun deleteUserAccountCascading() = runBlocking {
      // WHEN
      val retVal = custodialWalletDatabaseService.deleteAllUserAccountsByUserDetailCascading(primaryUserDetail)

      // THEN
      Assertions.assertThat(retVal).isTrue()
      testUserHelper.assertNoRecordsRemainInDatabaseFor(primaryUser)
      testUserHelper.assertNoUserIdentifiersExistFor(primaryUser)
    }

    @Test
    fun deleteUserAccountByProviderMsaIdAndExternalIdCascading() = runBlocking {
      // WHEN
      val retVal = custodialWalletDatabaseService.deleteAllUserAccountsByProviderMsaIdAndExternalIdCascading(providerMsaId, primaryUserProviderExternalId)

      // THEN
      Assertions.assertThat(retVal).isTrue()
      testUserHelper.assertNoRecordsRemainInDatabaseFor(primaryUser)
      testUserHelper.assertNoUserIdentifiersExistFor(primaryUser)
    }

    @Test
    fun deleteNonExistentUser() {
      runBlocking {
        val retVal = custodialWalletDatabaseService.deleteAllUserAccountsByUserDetailCascading(unknownUserDetail)
        Assertions.assertThat(retVal).isFalse()
      }
    }

    @Test
    fun saveUserIdentifierAndProviderExternalUserDetailThrows(): Unit = runBlocking {
      // GIVEN
      // A partially inserted second user (still missing user identifier(s) and details)
      val secondaryTestUser = testUserHelper.insertAccountKeysAndExternalUser(
        AccountKeysAndExternalUserProps(
          providerMsaId,
          "different@unfinished.com",
        )
      )

      // WHEN
      // Trying to add a user detail for a secondary (unrelated) account that has already been assigned to the primary
      // account results in an error.
      Assertions.assertThatThrownBy {
        runBlocking {
          custodialWalletDatabaseService.saveUserIdentifierAndProviderExternalUserDetail(
            secondaryTestUser.providerExternalUser.id!!,
            secondaryTestUser.userAccount.id!!,
            primaryUserDetail
          )
        }
      }.isInstanceOf(DuplicateKeyException::class.java).hasMessageContaining("duplicate key value violates unique constraint \"user_account_user_identifier_user_identifier_id_key\"")
    }

    @Test
    fun saveUserSeedData(): Unit = runBlocking {
      // GIVEN
      val userSeedData = UserSeedData.create(
        primaryUser.userAccount.id!!,
        SeedUsageType.CONTEXT_ITEM_MASTER,
        null,
        "0x000000",
        "different-test-kms-key",
        KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
      )

      // WHEN
      runBlocking {
        custodialWalletDatabaseService.saveUserSeedData(userSeedData)
      }

      // THEN
      val results = reactiveUserSeedDataRepository.findAll().collectList().awaitSingle()
      Assertions.assertThat(results).hasSize(1)
      Assertions.assertThat(results.first())
        .usingRecursiveComparison()
        .ignoringFields("id", "version")
        .isEqualTo(userSeedData)
    }

    @Test
    fun findMostRecentUserKeyDataByUserSeedDataId(): Unit = runBlocking {
      // GIVEN
      val userSeedData = UserSeedData.create(
        primaryUser.userAccount.id!!,
        SeedUsageType.HCP_MASTER,
        null,
        "0x1234",
        "test-kms-key",
        KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
      )
      val savedUserSeedData = reactiveUserSeedDataRepository.save(userSeedData).awaitSingle()

      val fiveSecondsAgo = Instant.now().minusMillis(5000).toEpochMilli().toBigInteger()
      val oldUserKeyData = UserKeyData(
        primaryUser.userAccount.id!!,
        "0x000000",
        "0x000000",
        KeyPairType.ED25519,
        "decryption-key",
        KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
        KeyUsageType.ICS,
        savedUserSeedData.id!!,
        fiveSecondsAgo,
        fiveSecondsAgo,
      )

      val newUserKeyData = UserKeyData.create(
        primaryUser.userAccount.id!!,
        fromHex("0x000000"),
        EncryptedKey(
          fromHex("0x000000"),
          KmsDecryptionKey(
            "decryption-key",
            KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT),
        ),
        KeyPairType.ED25519,
        KeyUsageType.ICS,
        savedUserSeedData.id!!,
      )

      reactiveUserKeyDataRepository.save(oldUserKeyData).awaitSingle()
      reactiveUserKeyDataRepository.save(newUserKeyData).awaitSingle()

      // WHEN
      val result = custodialWalletDatabaseService.findMostRecentUserKeyDataByUserSeedDataId(savedUserSeedData.id!!)

      // THEN
      Assertions.assertThat(result).usingRecursiveComparison().isEqualTo(newUserKeyData)
    }

    @Test
    fun findMostRecentUserKeyDataByUserSeedDataIdNoRows(): Unit = runBlocking {
      // GIVEN

      // WHEN
      val result = custodialWalletDatabaseService.findMostRecentUserKeyDataByUserSeedDataId(1.toBigInteger())

      // THEN
      Assertions.assertThat(result).isNull()
    }

    @Test
    fun findMostRecentUserSeedDataByUserAccountIdAndSeedUsageType(): Unit = runBlocking {
      // GIVEN
      val fiveSecondsAgo = Instant.now().minusMillis(5000).toEpochMilli().toBigInteger()
      val oldUserSeedData = UserSeedData(
        null,
        primaryUser.userAccount.id!!,
        SeedUsageType.HCP_MASTER,
        "0x1234",
        null,
        "test-kms-key",
        KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
        fiveSecondsAgo,
        fiveSecondsAgo,
        null
      )

      val newUserSeedData = UserSeedData.create(
        primaryUser.userAccount.id!!,
        SeedUsageType.HCP_MASTER,
        null,
        "0x1234",
        "test-kms-key",
        KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
      )

      reactiveUserSeedDataRepository.save(oldUserSeedData).awaitSingle()
      val savedNewUserSeedData = reactiveUserSeedDataRepository.save(newUserSeedData).awaitSingle()

      // WHEN
      val result = custodialWalletDatabaseService.findMostRecentUserSeedDataByUserAccountIdAndSeedUsageType(
        primaryUser.userAccount.id!!,
        SeedUsageType.HCP_MASTER,
      )

      // THEN
      Assertions.assertThat(result).usingRecursiveComparison().isEqualTo(savedNewUserSeedData)
    }

    @Test
    fun findMostRecentUserSeedDataByUserAccountIdAndSeedUsageTypeNoRows(): Unit = runBlocking {
      // GIVEN

      // WHEN
      val result = custodialWalletDatabaseService.findMostRecentUserSeedDataByUserAccountIdAndSeedUsageType(
        1.toBigInteger(),
        SeedUsageType.HCP_MASTER
      )

      // THEN
      Assertions.assertThat(result).isNull()
    }

    @Test
    fun findMostRecentUserDerivedKeyDataByUserSeedDataIdAndUsageType(): Unit = runBlocking {
      // GIVEN
      val userSeedData = UserSeedData.create(
        primaryUser.userAccount.id!!,
        SeedUsageType.HCP_MASTER,
        null,
        "0x1234",
        "test-kms-key",
        KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
      )
      val savedUserSeedData = reactiveUserSeedDataRepository.save(userSeedData).awaitSingle()

      val fiveSecondsAgo = Instant.now().minusMillis(5000).toEpochMilli().toBigInteger()
      val oldUserDerivedKeyData = UserDerivedKeyData(
        null,
        savedUserSeedData.id!!,
        "derivation/path",
        DerivedKeyUsageType.ON_CHAIN,
        "0x000000",
        "decryption-key",
        KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
        fiveSecondsAgo,
        fiveSecondsAgo,
        null
      )

      val newUserDerivedKeyData = UserDerivedKeyData.create(
        savedUserSeedData.id!!,
        "derivation/path",
        DerivedKeyUsageType.ON_CHAIN,
        "0x000000",
        "decryption-key",
        KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
      )

      reactiveUserDerivedKeyDataRepository.save(oldUserDerivedKeyData).awaitSingle()
      reactiveUserDerivedKeyDataRepository.save(newUserDerivedKeyData).awaitSingle()

      // WHEN
      val result = custodialWalletDatabaseService.findMostRecentUserDerivedKeyDataByUserSeedDataIdAndUsageType(
        savedUserSeedData.id!!,
        DerivedKeyUsageType.ON_CHAIN
      )

      // THEN
      Assertions.assertThat(result).usingRecursiveComparison().isEqualTo(newUserDerivedKeyData)
    }

    @Test
    fun findMostRecentUserDerivedKeyDataByUserSeedDataIdAndUsageTypeNoRows(): Unit = runBlocking {
      // GIVEN
      // WHEN
      val result = custodialWalletDatabaseService.findMostRecentUserDerivedKeyDataByUserSeedDataIdAndUsageType(
        1.toBigInteger(),
        DerivedKeyUsageType.ON_CHAIN
      )

      // THEN
      Assertions.assertThat(result).isNull()
    }

    @Test
    fun findMostRecentUserDerivedKeyDataByDerivationPathPrefixed(): Unit = runBlocking {
      //GIVEN
      val userSeedData = UserSeedData.create(
        primaryUser.userAccount.id!!,
        SeedUsageType.HCP_MASTER,
        null,
        "0x1234",
        "test-kms-key",
        KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
      )
      val savedUserSeedData = reactiveUserSeedDataRepository.save(userSeedData).awaitSingle()

      val userDerivedKeyData = UserDerivedKeyData.create(
        savedUserSeedData.id!!,
        "derivation/path",
        DerivedKeyUsageType.ON_CHAIN,
        "0x000000",
        "decryption-key",
        KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
      )

      val savedUserDerivedKeyData = reactiveUserDerivedKeyDataRepository.save(userDerivedKeyData).awaitSingle()

      // WHEN
      val result = custodialWalletDatabaseService.findMostRecentUserDerivedKeyDataByDerivationPathPrefixed("derivation")

      //THEN
      Assertions.assertThat(result).isNotNull
      Assertions.assertThat(result).isEqualTo(savedUserDerivedKeyData)
    }

    @Nested
    @DisplayName("OptIn")
    inner class OptIn {
      @BeforeEach
      fun saveOptIn(): Unit = runBlocking {
        custodialWalletDatabaseService.saveOptIn(
          CustodialWalletOptIn(
            primaryUser.userAccount.id!!,
            OptInType.COMMUNITY_REWARDS,
            true,
            Instant.now().toEpochMilli().toBigInteger(),
            Instant.now().toEpochMilli().toBigInteger()
          )
        )
      }

      @Test
      fun findOptInsByUserAccountId(): Unit = runBlocking {
        // WHEN
        val foundOptIn = runBlocking {
          custodialWalletDatabaseService.findOptInsByUserAccountId(primaryUser.userAccount.id!!)
        }

        // THEN
        Assertions.assertThat(foundOptIn.size).isEqualTo(1)
        Assertions.assertThat(foundOptIn.first().optInType).isEqualTo(OptInType.COMMUNITY_REWARDS)
        Assertions.assertThat(foundOptIn.first().isOptedIn).isEqualTo(true)
      }

      @Test
      fun findOptInByUserAccountIdAndOptInType(): Unit = runBlocking {
        // WHEN
        val foundOptIn = runBlocking {
          custodialWalletDatabaseService.findOptInByUserAccountIdAndOptInType(primaryUser.userAccount.id!!, OptInType.COMMUNITY_REWARDS)
        }

        // THEN
        Assertions.assertThat(foundOptIn).isNotNull
        Assertions.assertThat(foundOptIn!!.optInType).isEqualTo(OptInType.COMMUNITY_REWARDS)
        Assertions.assertThat(foundOptIn.isOptedIn).isEqualTo(true)
      }

      @Test
      fun saveDuplicateOptInThrows(): Unit = runBlocking {
        Assertions.assertThatThrownBy {
          runBlocking {
            custodialWalletDatabaseService.saveOptIn(
              CustodialWalletOptIn(
                primaryUser.userAccount.id!!,
                OptInType.COMMUNITY_REWARDS,
                true,
                Instant.now().toEpochMilli().toBigInteger(),
                Instant.now().toEpochMilli().toBigInteger()
              )
            )
          }
        }.isInstanceOf(DuplicateKeyException::class.java).hasMessageContaining("duplicate key value violates unique constraint \"opt_in_user_account_id_opt_in_type_key\"")
      }
    }

    @Nested
    inner class WithSavedUserSeedData {

      lateinit var contextItemMasterSeedData: UserSeedData
      lateinit var hcpMasterSeedData: UserSeedData

      @BeforeEach
      fun setUp(): Unit = runBlocking {
        contextItemMasterSeedData = UserSeedData.create(
          primaryUser.userAccount.id!!,
          SeedUsageType.CONTEXT_ITEM_MASTER,
          null,
          "0xa1b2c3",
          "ci-test-kms-key",
          KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
        )
        reactiveUserSeedDataRepository.save(contextItemMasterSeedData).awaitSingle()

        hcpMasterSeedData = UserSeedData.create(
          primaryUser.userAccount.id!!,
          SeedUsageType.HCP_MASTER,
          "0x123ABC",
          "0xz9y8x7",
          "hcp-test-kms-key",
          KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
        )
        reactiveUserSeedDataRepository.save(hcpMasterSeedData).awaitSingle()
      }

      @Test
      fun findUserSeedDataByUserAccountIdAndSeedUsageType(): Unit = runBlocking {
        // WHEN
        val foundSeedData = custodialWalletDatabaseService.findUserSeedDataByUserAccountIdAndSeedUsageType(
          primaryUser.userAccount.id!!,
          SeedUsageType.CONTEXT_ITEM_MASTER,
        )

        // THEN
        Assertions.assertThat(foundSeedData.first())
          .usingRecursiveComparison()
          .ignoringFields("id", "version")
          .isEqualTo(contextItemMasterSeedData)
      }

      @Test
      fun saveUserDerivedKeyData(): Unit = runBlocking {
        // GIVEN
        val userDerivedKeyData = UserDerivedKeyData.create(
          contextItemMasterSeedData.id!!,
          "derivation-path",
          DerivedKeyUsageType.CONTEXT_ITEM,
          "0x445566",
          "test-kms-key-id",
          KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
        )

        // WHEN
        custodialWalletDatabaseService.saveUserDerivedKeyData(userDerivedKeyData)

        // THEN
        val rows = reactiveUserDerivedKeyDataRepository.findAll().collectList().awaitSingle()
        Assertions.assertThat(rows).hasSize(1)
        Assertions.assertThat(rows.first())
          .usingRecursiveComparison()
          .ignoringFields("id", "version")
          .isEqualTo(userDerivedKeyData)
      }

      @Nested
      inner class WithSavedUserDerivedKeyData {

        private val derivationPath = "derivation-path"

        lateinit var staleDerivedKeyData: UserDerivedKeyData
        lateinit var latestDerivedKeyData: UserDerivedKeyData

        @BeforeEach
        fun setUp(): Unit = runBlocking {
          staleDerivedKeyData = UserDerivedKeyData(
            null,
            contextItemMasterSeedData.id!!,
            derivationPath,
            DerivedKeyUsageType.CONTEXT_ITEM,
            "0x445566",
            "test-kms-key-id",
            KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
            0.toBigInteger(),
            0.toBigInteger(),
            null
          )
          reactiveUserDerivedKeyDataRepository.save(staleDerivedKeyData).awaitSingle()

          latestDerivedKeyData = UserDerivedKeyData.create(
            contextItemMasterSeedData.id!!,
            derivationPath,
            DerivedKeyUsageType.CONTEXT_ITEM,
            "0x112233",
            "test-kms-key-id",
            KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
          )
          reactiveUserDerivedKeyDataRepository.save(latestDerivedKeyData).awaitSingle()
        }

        @Test
        fun findUserDerivedKeyDataByUserSeedDataId(): Unit = runBlocking {
          // GIVEN
          val seedId = contextItemMasterSeedData.id!!

          // WHEN
          val results = custodialWalletDatabaseService.findUserDerivedKeyDataByUserSeedDataId(seedId)

          // THEN
          Assertions.assertThat(results).hasSize(2)

          val sortedByCreatedAt = results.sortedBy { it.createdAt }
          val oldest = sortedByCreatedAt.first()
          val newest = sortedByCreatedAt.last()

          Assertions.assertThat(newest)
            .usingRecursiveComparison()
            .ignoringFields("id", "version")
            .isEqualTo(latestDerivedKeyData)

          Assertions.assertThat(oldest)
            .usingRecursiveComparison()
            .ignoringFields("id", "version")
            .isEqualTo(staleDerivedKeyData)
        }

        @Test
        fun findUserDerivedKeyDataByDerivationPath(): Unit = runBlocking {
          // WHEN
          val firstPage = custodialWalletDatabaseService.findUserDerivedKeyDataByDerivationPath(derivationPath, 0, 1)
          val secondPage = custodialWalletDatabaseService.findUserDerivedKeyDataByDerivationPath(derivationPath, 1, 1)

          // THEN
          Assertions.assertThat(firstPage).hasSize(1)
          Assertions.assertThat(firstPage.first())
            .usingRecursiveComparison()
            .ignoringFields("id", "version")
            .isEqualTo(staleDerivedKeyData)

          Assertions.assertThat(secondPage).hasSize(1)
          Assertions.assertThat(secondPage.first())
            .usingRecursiveComparison()
            .ignoringFields("id", "version")
            .isEqualTo(latestDerivedKeyData)
        }

        @Test
        fun findMostRecentUserDerivedKeyDataByDerivationPath(): Unit = runBlocking {
          // WHEN
          val result = custodialWalletDatabaseService.findMostRecentUserDerivedKeyDataByDerivationPath(derivationPath)

          // THEN
          Assertions.assertThat(result)
            .usingRecursiveComparison()
            .ignoringFields("id", "version")
            .isEqualTo(latestDerivedKeyData)
        }
      }
    }

    @Nested
    @DisplayName("With a Passkey Wallet")
    inner class WithPasskeyWallet {

      private lateinit var passkeyWallet: PasskeyWallet

      @BeforeEach
      fun savePasskeyWallet() {
        val credential = createRandomCredential()
        val wallet = Wallet.create(
          primaryUser.userAccount.id!!,
          base64Encode(Sr25519KeyPairCreator.createKeyPair().publicKeyBytes),
        )

        credential.walletMetadata = WalletMetadata.create(
          base64UrlEncode(Random.nextBytes(1024)),
          base64UrlEncode(Random.nextBytes(1024))
        )

        passkeyWallet = PasskeyWallet(credential, wallet)

        runBlocking {
          custodialWalletDatabaseService.savePasskeyWallet(passkeyWallet)
        }
      }

      @Test
      fun findPasskeyWalletByCredentialId() {
        // WHEN
        val foundPasskeyWallet = runBlocking {
          custodialWalletDatabaseService.findPasskeyWalletByCredentialId(passkeyWallet.credential.credentialIdBase64Url)
        }

        // THEN
        assertPasskeyWalletsEqual(foundPasskeyWallet, passkeyWallet)
      }

      @Test
      fun findPasskeyWalletsByUserAccountId() {
        // WHEN
        val foundPasskeyWallets = runBlocking {
          custodialWalletDatabaseService.findPasskeyWalletsByUserAccountId(primaryUser.userAccount.id!!)
        }

        // THEN
        Assertions.assertThat(foundPasskeyWallets).hasSize(1)
        assertPasskeyWalletsEqual(foundPasskeyWallets.first(), passkeyWallet)
      }

      @Test
      fun findUserAccountByCredentialId() {
        // WHEN
        val foundUserAccount = runBlocking {
          custodialWalletDatabaseService.findUserAccountByCredentialId(passkeyWallet.credential.credentialIdBase64Url)
        }

        // THEN
        Assertions.assertThat(foundUserAccount).isNotNull
        Assertions.assertThat(foundUserAccount?.id).isEqualTo(primaryUser.userAccount.id!!)
      }

      @Test
      fun deleteUserAccountCascading() = runBlocking {
        val persistedWallet = reactiveWalletRepository.findAllByUserAccountId(primaryUser.userAccount.id!!).awaitFirstOrNull()
        val persistedCredential = reactiveCredentialRepository.findAllByWalletId(persistedWallet?.id!!).awaitFirstOrNull()
        val persistedCredentialTransports = reactiveCredentialTransportRepository.findAllByCredentialId(persistedCredential?.id!!).collectList().awaitSingle()
        val persistedWalletMetadataList = reactiveWalletMetadataRepository.findAllByWalletId(persistedWallet.id!!).collectList().awaitSingle()

        // WHEN
        val retVal = custodialWalletDatabaseService.deleteAllUserAccountsByUserDetailCascading(primaryUserDetail)

        // THEN
        Assertions.assertThat(retVal).isTrue()
        testUserHelper.assertNoRecordsRemainInDatabaseFor(primaryUser)
        testUserHelper.assertNoUserIdentifiersExistFor(primaryUser)

        val wallet = reactiveWalletRepository.findById(persistedWallet.id!!).awaitFirstOrNull()
        Assertions.assertThat(wallet).isNull()

        val credential = reactiveCredentialRepository.findById(persistedCredential.id!!).awaitFirstOrNull()
        Assertions.assertThat(credential).isNull()

        persistedCredentialTransports.forEach { t ->
          val credentialTransport = reactiveCredentialTransportRepository.findById(t.id!!).awaitFirstOrNull()
          Assertions.assertThat(credentialTransport).isNull()
        }

        persistedWalletMetadataList.forEach { m ->
          val walletMetadata = reactiveWalletMetadataRepository.findById(m.id!!).awaitFirstOrNull()
          Assertions.assertThat(walletMetadata).isNull()
        }
      }

      @Test
      fun findWalletMetadataByWalletId(): Unit = runBlocking {
        //WHEN
        val persistedWallet = reactiveWalletRepository.findAllByUserAccountId(primaryUser.userAccount.id!!).awaitFirstOrNull()
        val foundPasskeyWalletWalletMetadata = custodialWalletDatabaseService.findWalletMetadataByWalletId(persistedWallet?.id!!)

        //THEN
        Assertions.assertThat(foundPasskeyWalletWalletMetadata).isNotNull
        Assertions.assertThat(foundPasskeyWalletWalletMetadata).isNotEmpty
        Assertions.assertThat(foundPasskeyWalletWalletMetadata[0].walletId).isEqualTo(persistedWallet.id)
      }

      @Test
      fun findWalletMetadataByCredentialId(): Unit = runBlocking {
        //WHEN
        val persistedWallet = reactiveWalletRepository.findAllByUserAccountId(primaryUser.userAccount.id!!).awaitFirstOrNull()

        val foundPasskeyWalletWalletMetadata = custodialWalletDatabaseService.findWalletMetadataByWalletId(persistedWallet?.id!!)

        //THEN
        Assertions.assertThat(foundPasskeyWalletWalletMetadata).isNotNull
        Assertions.assertThat(foundPasskeyWalletWalletMetadata).isNotEmpty
        Assertions.assertThat(foundPasskeyWalletWalletMetadata[0].walletId).isEqualTo(persistedWallet.id)
      }
    }

    @Nested
    @DisplayName("With Multiple Passkey Wallets")
    inner class WithMultiplePasskeyWallets {

      private lateinit var firstWallet: PasskeyWallet
      private lateinit var secondWallet: PasskeyWallet

      @BeforeEach
      fun savePasskeyWallet() {
        val userAccountId = primaryUser.userAccount.id!!

        val credential1 = createRandomCredential()
        val credential2 = createRandomCredential()
        val walletMetadata1 = WalletMetadata.create(
          base64UrlEncode(Random.nextBytes(1024)),
          base64UrlEncode(Random.nextBytes(1024))
        )
        val walletMetadata2 = WalletMetadata.create(
          base64UrlEncode(Random.nextBytes(1024)),
          base64UrlEncode(Random.nextBytes(1024))
        )

        credential1.walletMetadata = walletMetadata1
        credential2.walletMetadata = walletMetadata2

        firstWallet = PasskeyWallet(
          credential1,
          Wallet.create(
            userAccountId,
            base64Encode(Sr25519KeyPairCreator.createKeyPair().publicKeyBytes),
          ),
        )
        runBlocking {
          custodialWalletDatabaseService.savePasskeyWallet(firstWallet)
        }

        secondWallet = PasskeyWallet(
          credential2,
          Wallet.create(
            userAccountId,
            base64Encode(Sr25519KeyPairCreator.createKeyPair().publicKeyBytes),
          ),
        )
        runBlocking {
          custodialWalletDatabaseService.savePasskeyWallet(secondWallet)
        }
      }

      @Test
      fun findPasskeyWalletsByUserAccountId() {
        // WHEN
        val foundPasskeyWallets = runBlocking {
          custodialWalletDatabaseService.findPasskeyWalletsByUserAccountId(primaryUser.userAccount.id!!)
        }

        // THEN
        Assertions.assertThat(foundPasskeyWallets).hasSize(2)

        val foundPasskeyWalletsMatchingFirst =
          foundPasskeyWallets.filter { it.credential.authenticatorUuid == firstWallet.credential.authenticatorUuid }
        Assertions.assertThat(foundPasskeyWalletsMatchingFirst).hasSize(1)
        assertPasskeyWalletsEqual(foundPasskeyWalletsMatchingFirst.first(), firstWallet)

        val foundPasskeyWalletsMatchingSecond =
          foundPasskeyWallets.filter { it.credential.authenticatorUuid == secondWallet.credential.authenticatorUuid }
        Assertions.assertThat(foundPasskeyWalletsMatchingSecond).hasSize(1)
        assertPasskeyWalletsEqual(foundPasskeyWalletsMatchingSecond.first(), secondWallet)
      }

      @Test
      fun findUserAccountByCredentialId() {
        // WHEN
        val foundUserAccountForFirstWallet = runBlocking {
          custodialWalletDatabaseService.findUserAccountByCredentialId(firstWallet.credential.credentialIdBase64Url)
        }
        val foundUserAccountForSecondWallet = runBlocking {
          custodialWalletDatabaseService.findUserAccountByCredentialId(firstWallet.credential.credentialIdBase64Url)
        }

        // THEN
        Assertions.assertThat(foundUserAccountForFirstWallet?.id).isEqualTo(primaryUser.userAccount.id)
        Assertions.assertThat(foundUserAccountForSecondWallet?.id).isEqualTo(primaryUser.userAccount.id)
      }
    }

    @Nested
    @DisplayName("Without a Passkey Wallet")
    inner class WithoutPasskeyWallet {
      @Test
      fun savePasskeyWalletSucceeds() {
        // GIVEN
        val userAccountId = primaryUser.userAccount.id!!

        val publicKey = base64Encode(Sr25519KeyPairCreator.createKeyPair().publicKeyBytes)
        val wallet = Wallet.create(userAccountId, publicKey)
        val credential = createRandomCredential()
        val walletMetadata = WalletMetadata.create(
          base64UrlEncode(Random.nextBytes(1024)),
          base64UrlEncode(Random.nextBytes(1024))
        )

        credential.walletMetadata = walletMetadata

        val passkeyWallet = PasskeyWallet(credential, wallet)

        // WHEN
        runBlocking {
          custodialWalletDatabaseService.savePasskeyWallet(passkeyWallet)
        }

        // THEN
        val credentialCount = runBlocking { reactiveCredentialTransportRepository.findAll().count().awaitSingle() }
        Assertions.assertThat(credentialCount).isEqualTo(1)

        val persistedCredential = runBlocking { reactiveCredentialRepository.findAll().awaitFirstOrNull() }
        Assertions.assertThat(persistedCredential).isNotNull
        Assertions.assertThat(persistedCredential).usingRecursiveComparison()
          .ignoringFields("id", "walletId", "version", "transports", "walletMetadata")
          .isEqualTo(credential)

        val credentialTransportCount = runBlocking { reactiveCredentialTransportRepository.findAll().count().awaitSingle() }
        Assertions.assertThat(credentialTransportCount).isEqualTo(1)

        val persistedCredentialTransport = runBlocking { reactiveCredentialTransportRepository.findAll().awaitFirstOrNull() }
        Assertions.assertThat(persistedCredentialTransport?.transport).isEqualTo(credential.transports.first())
        Assertions.assertThat(persistedCredentialTransport?.credentialId).isEqualTo(persistedCredential?.id)

        val credentialWallet = runBlocking { reactiveWalletRepository.findAll().count().awaitSingle() }
        Assertions.assertThat(credentialWallet).isEqualTo(1)

        val persistedWallet = runBlocking { reactiveWalletRepository.findAll().awaitFirstOrNull() }
        Assertions.assertThat(persistedWallet).isNotNull
        Assertions.assertThat(persistedWallet).usingRecursiveComparison()
          .ignoringFields("id", "version")
          .isEqualTo(wallet)
        Assertions.assertThat(persistedCredential?.walletId).isEqualTo(persistedWallet?.id)

        val persistedWalletMetadata = runBlocking { reactiveWalletMetadataRepository.findAll().awaitFirstOrNull() }
        Assertions.assertThat(persistedWalletMetadata).isNotNull
        Assertions.assertThat(persistedWalletMetadata?.credentialId).isEqualTo(persistedCredential?.id)
        Assertions.assertThat(persistedWalletMetadata?.walletId).isEqualTo(persistedWallet?.id)
      }

      @Test
      fun findPasskeyWalletForNonExistentCredentialIdReturnsNull() {
        val foundPasskeyWallet = runBlocking {
          custodialWalletDatabaseService.findPasskeyWalletByCredentialId(createRandomCredential().credentialIdBase64Url)
        }

        Assertions.assertThat(foundPasskeyWallet).isNull()
      }

      @Test
      fun findPasskeyWalletsByUserAccountIdReturnsEmptyList() {
        val foundPasskeyWallets = runBlocking {
          custodialWalletDatabaseService.findPasskeyWalletsByUserAccountId(primaryUser.userAccount.id!!)
        }

        Assertions.assertThat(foundPasskeyWallets).isEmpty()
      }

      @Test
      fun findUserAccountForNonExistentCredentialIdReturnsNull() {
        val foundUserAccount = runBlocking {
          custodialWalletDatabaseService.findUserAccountByCredentialId(createRandomCredential().credentialIdBase64Url)
        }

        Assertions.assertThat(foundUserAccount).isNull()
      }
    }
  }
}
