package io.amplica.custodial_wallet.db

import io.amplica.custodial_wallet.client.kms.EncryptedKey
import io.amplica.custodial_wallet.client.kms.KmsDecryptionKey
import io.amplica.custodial_wallet.client.kms.KmsEncryptionAlgorithm
import io.amplica.custodial_wallet.db.repository.*
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions

class TestUserHelper(
  private val reactiveUserAccountRepository: ReactiveUserAccountRepository,
  private val reactiveUserKeyDataRepository: ReactiveUserKeyDataRepository,
  private val reactiveProviderExternalUserRepository: ReactiveProviderExternalUserRepository,
  private val reactiveUserIdentifierRepository: ReactiveUserIdentifierRepository,
  private val reactiveUserAccountUserIdentifierRepository: ReactiveUserAccountUserIdentifierRepository,
  private val reactiveProviderExternalUserDetailRepository: ReactiveProviderExternalUserDetailRepository,
) {

  fun insertAccountKeysAndExternalUser(props: AbstractAccountKeysAndExternalUserProps): SavedAccountKeysAndExternalUser {
    val userAccount = reactiveUserAccountRepository.save(UserAccount.create()).blockOptional().get()

    val accountUserKeyData = reactiveUserKeyDataRepository.save(
      UserKeyData.create(
        userAccount.id!!,
        props.accountKeyPair.publicKeyBytes,
        EncryptedKey(
          props.accountKeyPair.privateKeyBytes,
          KmsDecryptionKey("keyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)
        ),
        KeyPairType.SR25519,
        KeyUsageType.ACCOUNT
      )
    ).blockOptional().get()

    val graphUserKeyData = reactiveUserKeyDataRepository.save(
      UserKeyData.create(
        userAccount.id!!,
        props.graphKeyPair.publicKeyBytes,
        EncryptedKey(
          props.graphKeyPair.privateKeyBytes,
          KmsDecryptionKey("keyId", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)
        ),
        KeyPairType.SR25519,
        KeyUsageType.GRAPH
      )
    ).blockOptional().get()

    val providerExternalUser = reactiveProviderExternalUserRepository.save(
      ProviderExternalUser.create(
        props.providerMsaId,
        props.externalIdentifier,
        accountUserKeyData.id!!
      )
    ).blockOptional().get()

    return SavedAccountKeysAndExternalUser(userAccount, accountUserKeyData, graphUserKeyData, providerExternalUser)
  }

  fun insertTestUser(props: TestUserProps): SavedTestUser {
    val savedAccountKeysAndExternalUser = insertAccountKeysAndExternalUser(props)

    val userAccountId = savedAccountKeysAndExternalUser.userAccount.id!!
    val providerExternalUserId = savedAccountKeysAndExternalUser.providerExternalUser.id!!

    val userIdentifier = reactiveUserIdentifierRepository
      .save(UserIdentifier.create(props.userDetail))
      .blockOptional().get()

    val userAccountUserIdentifier =
      reactiveUserAccountUserIdentifierRepository
        .save(UserAccountUserIdentifier(userAccountId, userIdentifier.id!!))
        .blockOptional().get()

    val providerExternalUserDetail = reactiveProviderExternalUserDetailRepository.save(
      ProviderExternalUserDetail.create(
        providerExternalUserId,
        userAccountId,
        props.userDetail,
        userIdentifier.id!!
      )
    ).blockOptional().get()

    return savedAccountKeysAndExternalUser.toSavedTestUser(
      userIdentifier,
      userAccountUserIdentifier,
      providerExternalUserDetail,
    )
  }

  /**
   * NOTE: Does not include "user_identifier" records, see `assertUserIdentifierDeletedFor`
   */
  fun assertNoRecordsRemainInDatabaseFor(testUser: SavedTestUser) {
    runBlocking {
      val userAccountData = reactiveUserAccountRepository
        .findById(testUser.userAccount.id!!)
        .awaitSingleOrNull()
      Assertions.assertThat(userAccountData).isNull()

      val accountUserKeyData = reactiveUserKeyDataRepository
        .findById(testUser.accountUserKeyData.id!!)
        .awaitSingleOrNull()
      Assertions.assertThat(accountUserKeyData).isNull()

      val graphUserKeyData = reactiveUserKeyDataRepository
        .findById(testUser.graphUserKeyData.id!!)
        .awaitSingleOrNull()
      Assertions.assertThat(graphUserKeyData).isNull()

      val providerExternalUserData = reactiveProviderExternalUserRepository
        .findById(testUser.providerExternalUser.id!!)
        .awaitSingleOrNull()
      Assertions.assertThat(providerExternalUserData).isNull()

      val userAccountUserIdentifierData = reactiveUserAccountUserIdentifierRepository
        .findByUserAccountId(testUser.userAccount.id!!)
        .awaitFirstOrNull()
      Assertions.assertThat(userAccountUserIdentifierData).isNull()

      val providerExternalUserDetailData = reactiveProviderExternalUserDetailRepository
        .findById(testUser.providerExternalUserDetail.id!!)
        .awaitSingleOrNull()
      Assertions.assertThat(providerExternalUserDetailData).isNull()
    }
  }

  fun assertNoUserIdentifiersExistFor(testUser: SavedTestUser) {
    runBlocking {
      val userIdentifier = reactiveUserIdentifierRepository
        .findOneByValueAndType(testUser.userIdentifier.value, testUser.userIdentifier.type)
        .awaitFirstOrNull()
      Assertions.assertThat(userIdentifier).isNull()
    }
  }
}
