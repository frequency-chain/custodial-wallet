package io.amplica.custodial_wallet

import io.amplica.custodial_wallet.client.kms.EncryptedKey
import io.amplica.custodial_wallet.db.data.PasskeyWallet
import io.amplica.custodial_wallet.db.repository.*
import io.amplica.custodial_wallet.db.spring.DelegatingTransactionalOperator
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.domain.PageRequest
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigInteger
import java.util.stream.Collectors

open class ReactiveCustodialWalletDatabaseService(
  private val userAccountRepository: ReactiveUserAccountRepository,
  private val userKeyDataRepository: ReactiveUserKeyDataRepository,
  private val providerExternalUserRepository: ReactiveProviderExternalUserRepository,
  private val providerExternalUserDetailRepository: ReactiveProviderExternalUserDetailRepository,
  private val userIdentifierRepository: ReactiveUserIdentifierRepository,
  private val userAccountUserIdentifierRepository: ReactiveUserAccountUserIdentifierRepository,
  private val auditSessionRecordRepository: ReactiveAuditSessionRecordRepository,
  private val userPasswordRepository: ReactiveUserPasswordRepository,
  private val walletRepository: ReactiveWalletRepository,
  private val credentialRepository: ReactiveCredentialRepository,
  private val credentialTransportRepository: ReactiveCredentialTransportRepository,
  private val walletMetadataRepository: ReactiveWalletMetadataRepository,
  private val optInRepository: ReactiveOptInRepository,
  private val userSeedDataRepository: ReactiveUserSeedDataRepository,
  private val userDerivedKeyDataRepository: ReactiveUserDerivedKeyDataRepository,
  private val delegatingTransactionalOperator: DelegatingTransactionalOperator,
) : CustodialWalletDatabaseService {

  // NOTE: Assumes the user details have been verified (e.g., sms code, email link clicked)
  override suspend fun saveNewUserData(
    providerMsaId: BigInteger,
    providerExternalId: String,
    userDetails: List<UserDetail>,
    encryptedKeysData: List<EncryptedKeyData>
  ): UserKeyData {
    return delegatingTransactionalOperator.executeReadWrite {
      assert(encryptedKeysData.size in 1..2 && encryptedKeysData.find { it.keyUsageType == KeyUsageType.ACCOUNT } != null)
      val accountKey: EncryptedKeyData = encryptedKeysData.find { it.keyUsageType == KeyUsageType.ACCOUNT }!!
      val graphKey = encryptedKeysData.find { it.keyUsageType == KeyUsageType.GRAPH }

      val userAccountMono = saveOrFindUserAccountByUserDetails(userDetails)
      userAccountMono.flatMap { userAccount ->
        var first = createUserKeyData(
          userAccount,
          accountKey.publicKey,
          accountKey.encryptedPrivateKey,
          accountKey.encryptedPrivateKeyType,
          accountKey.keyUsageType
        )
        if (graphKey != null) {
          first = first.zipWith(
            createUserKeyData(
              userAccount,
              graphKey.publicKey,
              graphKey.encryptedPrivateKey,
              graphKey.encryptedPrivateKeyType,
              graphKey.keyUsageType
            )
          ) { a, b ->
            if (a.second.keyUsageType == KeyUsageType.ACCOUNT) a else b
          }
        }
        first
      }.flatMap { keysTuple ->
        // continue process with account key
        val userAccount = keysTuple.first
        val userKeyData = keysTuple.second
        createProviderExternalUser(providerMsaId, providerExternalId, userKeyData, userAccount)
      }.flatMap { userAccountAndUserKeyDataAndProviderExternalUser ->
        val userAccount = userAccountAndUserKeyDataAndProviderExternalUser.first
        val userKeyData = userAccountAndUserKeyDataAndProviderExternalUser.second
        val providerExternalUser = userAccountAndUserKeyDataAndProviderExternalUser.third
        createUserDetails(userDetails, providerExternalUser, userAccount, userKeyData)
      }.awaitSingle()
    }
  }

  override suspend fun saveUserKeyData(userKeyData: UserKeyData): BigInteger {
    return delegatingTransactionalOperator.executeReadWrite {
      userKeyDataRepository.save(userKeyData).awaitSingle().id!!
    }
  }

  override suspend fun saveNewUserIdentifierForUserAccount(
    userDetail: UserDetail,
    userAccountId: BigInteger
  ): UserAccountUserIdentifier {
    return delegatingTransactionalOperator.executeReadWrite {
      userIdentifierRepository.save(UserIdentifier.create(userDetail)).flatMap { userIdentifier ->
        userAccountUserIdentifierRepository.save(UserAccountUserIdentifier(userAccountId, userIdentifier.id!!))
      }.awaitSingle()
    }
  }

  override suspend fun findOneUserKeyData(
    providerMsaId: BigInteger, providerExternalId: String, keyUsageType: KeyUsageType
  ): UserKeyData? {
    return delegatingTransactionalOperator.executeReadOnly {
      userKeyDataRepository.findByProviderMsaIdAndProviderExternalIdAndKeyUsageTypeInUserAccount(
        providerMsaId, providerExternalId, keyUsageType
      ).awaitFirstOrNull()
    }
  }

  override suspend fun findOneUserKeyData(
    providerMsaId: BigInteger, keyPairType: KeyPairType, publicKeyHex: String, keyUsageType: KeyUsageType
  ): UserKeyData? {
    return delegatingTransactionalOperator.executeReadOnly {
      userKeyDataRepository.findOneByProviderMsaIdAndKeyPairTypeAndPublicKeyHexAndKeyUsageType(
        providerMsaId, keyPairType, publicKeyHex, keyUsageType
      ).awaitSingleOrNull()
    }
  }

  override suspend fun findUserKeyDataByProviderMsaIdAndProviderExternalIdAndKeyUsageType(
    providerMsaId: BigInteger, providerExternalId: String, keyUsageType: KeyUsageType
  ): List<UserKeyData> {
    return delegatingTransactionalOperator.executeReadOnly {
      userKeyDataRepository.findByProviderMsaIdAndProviderExternalIdAndKeyUsageType(
        providerMsaId, providerExternalId, keyUsageType
      ).collectList().awaitSingle()
    }
  }

  override suspend fun findUserKeyDataByUserAccountIdAndKeyUsageType(
    userAccountId: BigInteger,
    keyUsageType: KeyUsageType
  ): List<UserKeyData> {
    return delegatingTransactionalOperator.executeReadOnly {
      userKeyDataRepository.findByUserAccountIdAndKeyUsageType(userAccountId, keyUsageType)
        .collectList().awaitSingle()
    }
  }

  override suspend fun findUserKeyDataByUserAccountIdKeyUsageTypeAndKeyPairType(
    userAccountId: BigInteger,
    keyUsageType: KeyUsageType,
    keyPairType: KeyPairType
  ): List<UserKeyData> {
    return delegatingTransactionalOperator.executeReadOnly {
      userKeyDataRepository.findByUserAccountIdAndKeyUsageTypeAndEncryptedPrivateKeyType(
        userAccountId,
        keyUsageType,
        keyPairType,
      ).collectList().awaitSingle()
    }
  }

  override suspend fun findUserKeyDataMissingKeyType(
    existingKeyPairType: KeyPairType,
    keyUsageType: KeyUsageType,
    missingKeyPairType: KeyPairType,
    limit: Int,
    offset: Int,
  ): List<UserKeyData> {
    return delegatingTransactionalOperator.executeReadOnly {
      userKeyDataRepository.findUsersWithoutKeyOfType(existingKeyPairType, keyUsageType, missingKeyPairType, limit, offset).collectList().awaitSingle()
    }
  }

  override suspend fun createAuditSessionRecord(auditSessionRecord: AuditSessionRecord): AuditSessionRecord {
    return delegatingTransactionalOperator.executeReadWrite {
      auditSessionRecordRepository.save(auditSessionRecord).awaitSingle()
    }
  }

  override suspend fun findAuditSessionRecordBySessionId(sessionId: String): AuditSessionRecord {
    return delegatingTransactionalOperator.executeReadOnly {
      auditSessionRecordRepository.findOneBySessionId(sessionId).awaitSingle()
    }
  }

  override suspend fun updateAuditSessionRecord(auditSessionRecord: AuditSessionRecord): AuditSessionRecord {
    return delegatingTransactionalOperator.executeReadWrite {
      (auditSessionRecord.takeIf { it.id != null }?.let {
        auditSessionRecordRepository.save(it)
      } ?: throw IllegalStateException("Audit session record passed with no id given")).awaitSingle()
    }
  }

  override suspend fun findUserKeyDataByKeyPairTypeAndPublicKeys(
    keyPairType: KeyPairType, publicKeysInHex: List<String>
  ): List<UserKeyData> {
    return delegatingTransactionalOperator.executeReadOnly {
      val upperCasedPublicKeysInHex = publicKeysInHex.map { it.uppercase() }
      userKeyDataRepository.findByEncryptedPrivateKeyTypeAndPublicKeyHexIgnoreCaseIn(keyPairType, upperCasedPublicKeysInHex)
        .collectList().awaitSingle()
    }
  }

  override suspend fun findOneProviderExternalUserDetailByUserDetailValueAndUserDetailType(
    userDetailValue: String, userDetailType: UserDetailType
  ): ProviderExternalUserDetail {
    return delegatingTransactionalOperator.executeReadOnly {
      providerExternalUserDetailRepository.findByUserDetailValueAndUserDetailType(userDetailValue, userDetailType)
        .awaitFirst()
    }
  }

  override suspend fun findProviderExternalUserDetailByUserDetailValueAndUserDetailTypeAndProviderExternalIdAndProviderMsaId(
    userDetailValue: String,
    userDetailType: UserDetailType,
    providerExternalId: String,
    providerExternalUserProviderMsaId: BigInteger
  ): ProviderExternalUserDetail? {
    return delegatingTransactionalOperator.executeReadOnly {
      providerExternalUserDetailRepository.findOneProviderExternalUserDetailByUserDetailValueAndUserDetailTypeAndProviderExternalIdAndProviderMsaId(
        userDetailValue, userDetailType, providerExternalId, providerExternalUserProviderMsaId
      ).awaitSingleOrNull()
    }
  }

  private fun deleteProviderExternalUserDetailsByIds(ids: List<BigInteger>): Mono<Void> {
    return providerExternalUserDetailRepository.deleteAllById(ids)
  }

  private fun deleteProviderExternalUsersByIds(ids: List<BigInteger>): Mono<Void> {
    return providerExternalUserRepository.deleteAllById(ids)
  }

  private fun deleteUserKeyDataByUserAccountIds(userAccountIds: List<BigInteger>): Mono<Void> {
    return userKeyDataRepository.deleteByUserAccountIdIn(userAccountIds)
  }

  private fun deleteUserAccountsByUserAccountIds(userAccountIds: List<BigInteger>): Mono<Void> {
    return userPasswordRepository.deleteByUserAccountIdIn(userAccountIds)
      .then(userAccountRepository.deleteAllById(userAccountIds))
  }

  private fun deleteUserPasswordByUserAccountId(userAccountId: BigInteger): Mono<Void> {
    return userPasswordRepository.deleteByUserAccountId(userAccountId)
  }

  private fun deleteUserPasswordsByUserAccountIds(userAccountIds: List<BigInteger>): Mono<Void> {
    return userPasswordRepository.deleteByUserAccountIdIn(userAccountIds)
  }

  private fun deleteUserKeyDataAndExternalUser(userAccountIds: List<BigInteger>): Mono<Void> {
    val userKeyDataIdsMono = Flux.fromIterable(userAccountIds)
      .flatMap { userAccountId ->
        userKeyDataRepository.findByUserAccountId(userAccountId).map { userKeyData -> userKeyData.id!! }
      }
      .collectList()

    return userKeyDataIdsMono.flatMap { userKeyDataIds ->
      val providerExternalUserIdsMono =
        providerExternalUserRepository.findByUserKeyDataIdIn(userKeyDataIds).map { it.id!! }.collectList()

      providerExternalUserIdsMono.flatMap { providerExternalUserIds ->
        val providerExternalUserDetailIdsMono = providerExternalUserDetailRepository
          .findByProviderExternalUserIdIn(providerExternalUserIds)
          .mapNotNull { it.id }
          .collectList()

        providerExternalUserDetailIdsMono.flatMap { providerExternalUserDetailIds ->
          providerExternalUserDetailRepository.deleteAllById(providerExternalUserDetailIds)
            .then(providerExternalUserRepository.deleteAllById(providerExternalUserIds))
            .then(userKeyDataRepository.deleteAllById(userKeyDataIds))
        }
      }
    }
  }

  private fun deleteUserIdentifiersByUserAccountIds(userAccountIds: List<BigInteger>): Mono<Boolean> {
    return Flux.fromIterable(userAccountIds)
      // Find all UserIdentifier associated with the accounts
      .flatMap { userAccountUserIdentifierRepository.findByUserAccountId(it) }
      .map { it.userIdentifierId }
      .collectList()
      .flatMap { userIdentifierIds ->
        userAccountUserIdentifierRepository.deleteByUserAccountIdIn(userAccountIds)
          .then(userIdentifierRepository.deleteAllById(userIdentifierIds))
          .then(Mono.fromCallable { true })
      }
  }

  private fun findAllCredentialsByUserAccountIds(userAccountIds: List<BigInteger>): Flux<Credential> {
    val walletCredentials = Flux.fromIterable(userAccountIds)
      .flatMap { userAccountId -> walletRepository.findAllByUserAccountId(userAccountId) }
      .flatMap { wallet -> credentialRepository.findAllByWalletId(wallet.id!!) }

    val userCredentials = Flux.fromIterable(userAccountIds)
      .flatMap { id -> credentialRepository.findByUserAccountId(id) }

    return walletCredentials.concatWith(userCredentials)
  }

  private fun findAllCredentialTransportIdsByUserAccountIds(userAccountIds: List<BigInteger>): Mono<Set<BigInteger>> {
    return findAllCredentialsByUserAccountIds(userAccountIds)
      .flatMap { credential -> credentialTransportRepository.findAllByCredentialId(credential.id!!) }
      .collectList()
      .map {
        credentialTransports -> credentialTransports.map { it.id!! }.toSet()
      }
  }

  private fun findAllWalletMetadataIdsByWalletId(userAccountIds: List<BigInteger>): Mono<Set<BigInteger>> {
    return Flux.fromIterable(userAccountIds)
      .flatMap { userAccountId -> walletRepository.findAllByUserAccountId(userAccountId) }
      .flatMap { wallet -> walletMetadataRepository.findAllByWalletId(wallet.id!!) }
      .collectList()
      .map {
        walletMetadataList -> walletMetadataList.map {it.id!! }.toSet()
      }
  }

  private fun deleteCredentialTransportsByUserAccountIds(userAccountIds: List<BigInteger>): Mono<Void> {
    return findAllCredentialTransportIdsByUserAccountIds(userAccountIds).flatMap { ids ->
      credentialTransportRepository.deleteAllById(ids)
    }
  }

  private fun deleteWalletMetadataByUserAccountIds(userAccountIds: List<BigInteger>): Mono<Void> {
    return findAllWalletMetadataIdsByWalletId(userAccountIds).flatMap { ids ->
      walletMetadataRepository.deleteAllById(ids)
    }
  }

  private fun deleteCredentialsByUserAccountIds(userAccountIds: List<BigInteger>): Mono<Void> {
    val credentialIdsToDelete = findAllCredentialsByUserAccountIds(userAccountIds)
      .collectList()
      .map { credentials -> credentials.map { it.id } }

    return deleteCredentialTransportsByUserAccountIds(userAccountIds).then(
      credentialIdsToDelete.flatMap { ids -> credentialRepository.deleteAllById(ids) }
    )
  }

  private fun deleteWalletsByUserAccountIds(userAccountIds: List<BigInteger>): Mono<Void> {
    return walletRepository.deleteAllByUserAccountIdIn(userAccountIds)
  }

  private fun deleteUserAccountsCascading(userAccountIds: List<BigInteger>): Mono<Boolean> {
    return deleteUserPasswordsByUserAccountIds(userAccountIds)
      .then(deleteUserKeyDataAndExternalUser(userAccountIds))
      .then(deleteUserIdentifiersByUserAccountIds(userAccountIds))
      .then(deleteWalletMetadataByUserAccountIds(userAccountIds))
      .then(deleteCredentialsByUserAccountIds(userAccountIds))
      .then(deleteWalletsByUserAccountIds(userAccountIds))
      .then(deleteUserAccountsByUserAccountIds(userAccountIds))
      .then(Mono.just(true))
  }

  override suspend fun deleteAllUserAccountsByUserDetailCascading(userDetail: UserDetail): Boolean {
    return delegatingTransactionalOperator.executeReadWrite {
      providerExternalUserDetailRepository.findByUserDetailValueAndUserDetailType(userDetail.value, userDetail.type)
        .collectList()
        .map { providerExternalUserDetails ->
          providerExternalUserDetails.map { it.userAccountId }
        }
        .flatMap { userAccountIds ->
          if (userAccountIds.isEmpty()) {
            Mono.just(false)
          } else {
            deleteUserAccountsCascading(userAccountIds)
          }
        }.awaitSingle()
    }
  }

  override suspend fun deleteAllUserAccountsByProviderMsaIdAndExternalIdCascading(
    providerMsaId: BigInteger, externalId: String
  ): Boolean {
    return delegatingTransactionalOperator.executeReadWrite {
      userAccountRepository.findAllByProviderExternalUserProviderExternalId(providerMsaId, externalId)
        .map { userAccount -> userAccount.id!! }
        .collectList()
        .flatMap { userAccountIds ->
          if (userAccountIds.isEmpty()) {
            Mono.just(false)
          } else {
            deleteUserAccountsCascading(userAccountIds)
          }
        }.awaitSingleOrNull()
    } ?: false
  }

  override suspend fun deleteProviderExternalUserDetailById(id: BigInteger) {
    delegatingTransactionalOperator.executeReadWrite {
      deleteProviderExternalUserDetailsByIds(listOf(id)).awaitSingleOrNull()  //TODO create single id method
    }
  }

  override suspend fun deleteProviderExternalUserById(id: BigInteger) {
    delegatingTransactionalOperator.executeReadWrite {
      deleteProviderExternalUsersByIds(listOf(id)).awaitSingleOrNull()  //TODO create single id method
    }
  }

  override suspend fun deleteUserKeyDataByUserAccountId(userAccountId: BigInteger) {
    delegatingTransactionalOperator.executeReadWrite {
      deleteUserKeyDataByUserAccountIds(listOf(userAccountId)).awaitSingleOrNull()  //TODO create single id method
    }
  }

  override suspend fun deleteUserAccountByUserAccountId(userAccountId: BigInteger) {
    delegatingTransactionalOperator.executeReadWrite {
      deleteUserPasswordByUserAccountId(userAccountId)
        .then(deleteUserAccountsByUserAccountIds(listOf(userAccountId))) //TODO create single id method)
        .awaitSingleOrNull()
    }
  }

  override suspend fun findAccountUserKeyDataByProviderMsaIdAndUserDetail(
    providerMsaId: BigInteger, userDetail: UserDetail
  ): UserKeyData? {
    return delegatingTransactionalOperator.executeReadOnly {
      userKeyDataRepository.findByProviderMsaIdAndUserDetailValueAndUserDetailTypeAndKeyUsageType(
        providerMsaId, userDetail.value, userDetail.type, KeyUsageType.ACCOUNT
      ).singleOrEmpty().awaitSingleOrNull()
    }
  }

  override suspend fun findUserAccountByUserIdentifier(userIdentifier: UserDetail): UserAccount? {
    return delegatingTransactionalOperator.executeReadOnly {
      userAccountRepository.findByUserIdentifier(userIdentifier.value, userIdentifier.type).awaitSingleOrNull()
    }
  }

  override suspend fun findOneUserAccountByUserIdentifiers(userIdentifiers: List<UserDetail>): UserAccount? {
    return delegatingTransactionalOperator.executeReadOnly {
      userAccountRepository.findByUserIdentifiers(userIdentifiers)
        .singleOrEmpty() // Coerce from a Flux to a Mono--throws when there are multiple values
        .awaitSingleOrNull()
    }
  }

  override suspend fun findUserIdentifiersByUserAccount(userAccountId: BigInteger): List<UserIdentifier> {
    return delegatingTransactionalOperator.executeReadOnly {
      userAccountUserIdentifierRepository.findByUserAccountId(userAccountId).collectList()
        .flatMap { userIdentifierList ->
          val idList = userIdentifierList.map { it.userIdentifierId }
          userIdentifierRepository.findAllById(idList).collectList()
        }.awaitSingle()
    }
  }

  override suspend fun findUserIdentifier(userDetail: UserDetail): UserIdentifier? {
    return delegatingTransactionalOperator.executeReadOnly {
      userIdentifierRepository.findOneByValueAndType(userDetail.value, userDetail.type).awaitSingleOrNull()
    }
  }

  override suspend fun updateUserIdentifierVerifiedDate(userDetail: UserDetail) {
    delegatingTransactionalOperator.executeReadWrite {
      userIdentifierRepository.findOneByValueAndType(userDetail.value, userDetail.type)
        .flatMap { existingUserIdentifier ->
          userIdentifierRepository.save(existingUserIdentifier.updateVerifiedDateToNow())
        }.awaitSingleOrNull()
    }
  }

  private fun findOrInsertUserAccountUserIdentifier(userAccountId: BigInteger, userIdentifierId: BigInteger): Mono<UserAccountUserIdentifier> {
    return userAccountUserIdentifierRepository.findByUserAccountIdAndUserIdentifierId(userAccountId, userIdentifierId)
      .switchIfEmpty(
        userAccountUserIdentifierRepository.save(UserAccountUserIdentifier(userAccountId, userIdentifierId))
      )
  }

  private fun reactiveSaveUserIdentifierAndProviderExternalUserDetail(
    providerExternalUserId: BigInteger, userAccountId: BigInteger, userDetail: UserDetail
  ): Mono<ProviderExternalUserDetail> {

    val foundOrInsertedUserIdentifier =
      userIdentifierRepository.findOneByValueAndType(userDetail.value, userDetail.type)
        .switchIfEmpty(
          // Create a new UserIdentifier
          userIdentifierRepository.save(UserIdentifier.create(userDetail))
        ).flatMap { userIdentifier ->
          // Ensure the relationship with `userAccountId` is in the join table
          // NOTE(Julian, 2024-06-27): An SQL exception will be thrown if `userIdentifier` is already mapped to a
          // *different* `UserAccount`.
          findOrInsertUserAccountUserIdentifier(userAccountId, userIdentifier.id!!)
            .map { userIdentifier }
        }

    return foundOrInsertedUserIdentifier.flatMap { userIdentifier ->
      providerExternalUserDetailRepository.save(
        ProviderExternalUserDetail(
          providerExternalUserId,
          userAccountId,
          userIdentifier.value,
          userIdentifier.type,
          userDetail.priority,
          userIdentifier.id!!,
          userIdentifier.createdAt,
          userIdentifier.lastModified
        )
      )
    }
  }

  // NOTE: Assumes the user detail has been verified (e.g., sms code, email link clicked)
  override suspend fun saveUserIdentifierAndProviderExternalUserDetail(
    providerExternalUserId: BigInteger, userAccountId: BigInteger, userDetail: UserDetail
  ): ProviderExternalUserDetail {
    return delegatingTransactionalOperator.executeReadWrite {
      reactiveSaveUserIdentifierAndProviderExternalUserDetail(
        providerExternalUserId,
        userAccountId,
        userDetail
      ).awaitSingle()
    }
  }

  override suspend fun saveProviderExternalUserDetail(providerExternalUserDetail: ProviderExternalUserDetail): ProviderExternalUserDetail {
    return delegatingTransactionalOperator.executeReadWrite {
      providerExternalUserDetailRepository.save(providerExternalUserDetail).awaitSingle()
    }
  }

  override suspend fun saveProviderExternalUser(providerExternalUser: ProviderExternalUser): ProviderExternalUser {
    return delegatingTransactionalOperator.executeReadWrite {
      providerExternalUserRepository.save(providerExternalUser).awaitSingle()
    }
  }

  override suspend fun findUserDataByUserAccountIdsAndUserDetail(
    userDetail: UserDetail, userAccountIdList: List<BigInteger>
  ): List<UserData> {
    return delegatingTransactionalOperator.executeReadOnly {
      userKeyDataRepository.findUserDataByUserAccountIds(userDetail.value, userDetail.type, userAccountIdList).collectList().awaitSingle()
    }
  }

  override suspend fun findUserDataByUserAccountIds(userAccountIdList: List<BigInteger>): List<UserData> {
    return delegatingTransactionalOperator.executeReadOnly {
      userKeyDataRepository.findUserDataByUserAccountIds(userAccountIdList).collectList().awaitSingle()
    }
  }

  override suspend fun findUserDetailsByProviderMsaIdAndExternalUserId(
    providerMsaId: BigInteger, providerExternalUserId: String
  ): List<ProviderExternalUserDetail> {
    return delegatingTransactionalOperator.executeReadOnly {
      providerExternalUserDetailRepository.findByProviderMsaIdAndExternalUserId(
        providerMsaId, providerExternalUserId
      ).collectList().awaitSingle()
    }
  }

  override suspend fun findProviderExternalUserByProviderMsaIdAndKeyPairTypeAndPublicKeyHexAndKeyUsageType(
    providerMsaId: BigInteger, keyPairType: KeyPairType, publicKeyHex: String, keyUsageType: KeyUsageType
  ): ProviderExternalUser? {
    return delegatingTransactionalOperator.executeReadOnly {
      providerExternalUserRepository.findOneByProviderMsaIdAndKeyPairTypeAndPublicKeyHexAndKeyUsageType(
        providerMsaId, keyPairType, publicKeyHex, keyUsageType
      ).awaitSingleOrNull()
    }
  }

  override suspend fun findUserDetailsByProviderMsaIdAndKeyPairTypeAndPublicKeyHexAndKeyUsageType(
    providerMsaId: BigInteger, keyPairType: KeyPairType, publicKeyHex: String, keyUsageType: KeyUsageType
  ): List<ProviderExternalUserDetail> {
    return delegatingTransactionalOperator.executeReadOnly {
      providerExternalUserDetailRepository.findByProviderMsaIdAndKeyPairTypeAndPublicKeyHexAndKeyUsageType(
        providerMsaId, keyPairType, publicKeyHex, keyUsageType
      ).collectList().awaitSingle()
    }
  }

  override suspend fun saveUserPassword(userPassword: UserPassword): UserPassword {
    return delegatingTransactionalOperator.executeReadWrite {
      userPasswordRepository.save(userPassword).awaitSingle()
    }
  }

  override suspend fun deleteOneUserPasswordById(id: BigInteger) {
    delegatingTransactionalOperator.executeReadWrite {
      userPasswordRepository.deleteById(id).awaitSingleOrNull()
    }
  }

  override suspend fun findOneUserPasswordById(id: BigInteger): UserPassword? {
    return delegatingTransactionalOperator.executeReadOnly {
      userPasswordRepository.findById(id).awaitSingleOrNull()
    }
  }

  // Business related finders
  override suspend fun findOneUserPasswordByPublicKeyHex(publicKeyHex: String): UserPassword? {
    return delegatingTransactionalOperator.executeReadOnly {
      userPasswordRepository.findUserPasswordByPublicKeyHex(publicKeyHex).awaitSingleOrNull()
    }
  }

  override suspend fun findOneUserPasswordByProviderMsaIdAndProviderExternalId(
    providerMsaId: BigInteger,
    providerExternalId: String
  ): UserPassword? {
    return delegatingTransactionalOperator.executeReadOnly {
      userPasswordRepository.findUserPasswordByProviderMsaIdAndProviderExternalId(providerMsaId, providerExternalId)
        .awaitSingleOrNull()
    }
  }

  override suspend fun findOneUserPasswordByUserAccountId(userAccountId: BigInteger): UserPassword? {
    return delegatingTransactionalOperator.executeReadOnly {
      userPasswordRepository.findByUserAccountId(userAccountId).awaitSingleOrNull()
    }
  }

  override suspend fun savePasskeyWallet(passkeyWallet: PasskeyWallet) {
    delegatingTransactionalOperator.executeReadWrite {
      walletRepository.save(passkeyWallet.wallet).flatMap { savedWallet ->
        saveCredentialAndCredentialTransport(savedWallet.id!!, passkeyWallet.credential)
          .flatMap { savedCredential ->
            val walletMetadata = passkeyWallet.credential.walletMetadata!!
            walletMetadata.walletId = savedWallet!!.id
            walletMetadata.credentialId = savedCredential!!.id
            walletMetadataRepository.save(walletMetadata)
        }
      }.awaitSingleOrNull()
    }
  }

  private fun saveCredentialAndCredentialTransport(walletId: BigInteger, credential: Credential): Mono<Credential> {
    val credentialMono: Mono<Credential> = credentialRepository.save(credential.copy(walletId = walletId))
    return credentialMono.flatMap { savedCredential ->
      Flux.fromIterable(credential.transports).flatMap { transport ->
        val credentialTransport = CredentialTransport.create(savedCredential.id!!, transport)
        credentialTransportRepository.save(credentialTransport)
      }.collectList().map { savedCredential }
    }
  }

  private fun findTransportsByCredentialId(credentialId: BigInteger): Mono<Set<String>> {
    return credentialTransportRepository.findAllByCredentialId(credentialId)
      .map { credentialTransportRepository -> credentialTransportRepository.transport }
      .collectList()
      .map { it.toSet() }
  }

  override suspend fun findPasskeyWalletByCredentialId(credentialIdBase64Url: String): PasskeyWallet? {
    return delegatingTransactionalOperator.executeReadOnly {
      credentialRepository.findByCredentialIdBase64Url(credentialIdBase64Url).flatMap { credential ->
        // Hydrate transports
        findTransportsByCredentialId(credential.id!!).map { transports ->
          credential.apply { this.transports = transports }
        }
      }.flatMap { credential ->
        credential.walletId?.let { walletId ->
          walletRepository.findById(walletId).flatMap { wallet ->
            walletMetadataRepository.findOneByCredentialId(credential.id!!).map { metadata ->
              credential.walletMetadata = metadata
              PasskeyWallet(credential, wallet)
            }
          }
        } ?: Mono.empty()
      }.awaitSingleOrNull()
    }
  }

  override suspend fun findPasskeyWalletsByUserAccountId(userAccountId: BigInteger): List<PasskeyWallet> {
    return delegatingTransactionalOperator.executeReadOnly {
      walletRepository.findAllByUserAccountId(userAccountId).flatMap { wallet ->
        credentialRepository.findAllByWalletId(wallet.id!!).flatMap { credential ->
          // Hydrate transports
          findTransportsByCredentialId(credential.id!!).map { transports ->
            credential.apply { this.transports = transports }
          }
        }.flatMap { credential ->
          walletMetadataRepository.findOneByCredentialId(credential.id!!).map { metadata ->
            credential.walletMetadata = metadata
            PasskeyWallet(credential, wallet)
          }
        }
      }.collectList().awaitSingle()
    }
  }

  override suspend fun findUserAccountByCredentialId(credentialIdBase64Url: String): UserAccount? {
    return delegatingTransactionalOperator.executeReadOnly {
      credentialRepository.findByCredentialIdBase64Url(credentialIdBase64Url).flatMap { credential ->
        // Credential may be associated with a wallet or not (e.g., directly with a user account for login)
        credential.walletId?.let { walletId ->
          walletRepository.findById(walletId).flatMap { wallet ->
            userAccountRepository.findById(wallet.userAccountId)
          }
        } ?: Mono.empty()
      }.awaitSingleOrNull()
    }
  }

  override suspend fun saveWalletMetadata(walletMetadata: WalletMetadata): WalletMetadata {
    return delegatingTransactionalOperator.executeReadWrite {
      walletMetadataRepository.save(walletMetadata).awaitSingle()
    }
  }

  override suspend fun findWalletMetadataByWalletId(walletId: BigInteger): List<WalletMetadata> {
    return delegatingTransactionalOperator.executeReadOnly {
      walletMetadataRepository.findAllByWalletId(walletId).collectList().awaitSingle()
    }
  }

  override suspend fun findWalletMetadataByCredentialId(credentialId: BigInteger): WalletMetadata? {
    return delegatingTransactionalOperator.executeReadOnly {
      walletMetadataRepository.findOneByCredentialId(credentialId).awaitSingleOrNull()
    }
  }

  override suspend fun findOptInsByUserAccountId(userAccountId: BigInteger): Set<CustodialWalletOptIn> {
    return delegatingTransactionalOperator.executeReadOnly {
      optInRepository.findAllByUserAccountId(userAccountId).collect(Collectors.toSet()).awaitSingle()
    }
  }

  override suspend fun findOptInByUserAccountIdAndOptInType(
    userAccountId: BigInteger,
    optInType: OptInType
  ): CustodialWalletOptIn? {
    return delegatingTransactionalOperator.executeReadOnly {
      optInRepository.findByUserAccountIdAndOptInType(userAccountId, optInType)
        .awaitFirstOrNull()
    }
  }

  override suspend fun saveOptIn(optIn: CustodialWalletOptIn): CustodialWalletOptIn {
    return delegatingTransactionalOperator.executeReadWrite {
      optInRepository.save(optIn).awaitSingle()
    }
  }

  override suspend fun findMostRecentUserKeyDataByUserSeedDataId(userSeedDataId: BigInteger): UserKeyData? {
    return delegatingTransactionalOperator.executeReadOnly {
      userKeyDataRepository.findFirstByUserSeedDataIdOrderByCreatedAtDesc(userSeedDataId).awaitSingleOrNull()
    }
  }

  override suspend fun findUserSeedDataByUserAccountIdAndSeedUsageType(
    userAccountId: BigInteger,
    seedUsageType: SeedUsageType
  ): List<UserSeedData> {
    return delegatingTransactionalOperator.executeReadOnly {
      userSeedDataRepository.findByUserAccountIdAndSeedUsageType(userAccountId, seedUsageType).collectList().awaitSingle()
    }
  }

  override suspend fun findMostRecentUserSeedDataByUserAccountIdAndSeedUsageType(
    userAccountId: BigInteger,
    seedUsageType: SeedUsageType
  ): UserSeedData? {
    return delegatingTransactionalOperator.executeReadOnly {
      userSeedDataRepository.findFirstByUserAccountIdAndSeedUsageTypeOrderByCreatedAtDesc(
        userAccountId,
        seedUsageType,
      ).awaitSingleOrNull()
    }
  }

  override suspend fun saveUserSeedData(userSeedData: UserSeedData): UserSeedData {
    return delegatingTransactionalOperator.executeReadWrite {
      userSeedDataRepository.save(userSeedData).awaitSingle()
    }
  }

  override suspend fun findUserDerivedKeyDataByUserSeedDataId(userSeedDataId: BigInteger): List<UserDerivedKeyData> {
    return delegatingTransactionalOperator.executeReadOnly {
      userDerivedKeyDataRepository.findByUserSeedDataId(userSeedDataId).collectList().awaitSingle()
    }
  }

  override suspend fun findUserDerivedKeyDataByDerivationPath(
    derivationPath: String,
    offset: Int,
    limit: Int
  ): List<UserDerivedKeyData> {
    val pageable = PageRequest.of(offset, limit)
    return delegatingTransactionalOperator.executeReadOnly {
      userDerivedKeyDataRepository
        .findByDerivationPath(derivationPath, pageable)
        .collectList()
        .awaitSingle()
    }
  }

  override suspend fun findMostRecentUserDerivedKeyDataByDerivationPath(derivationPath: String): UserDerivedKeyData? {
    return delegatingTransactionalOperator.executeReadOnly {
      userDerivedKeyDataRepository
        .findFirstByDerivationPathOrderByCreatedAtDesc(derivationPath)
        .awaitSingleOrNull()
    }
  }

  override suspend fun findMostRecentUserDerivedKeyDataByDerivationPathPrefixed(derivationPathPrefix: String): UserDerivedKeyData? {
    return delegatingTransactionalOperator.executeReadOnly {
      userDerivedKeyDataRepository.findFirstByDerivationPathStartsWithOrderByCreatedAtDesc(derivationPathPrefix)
        .awaitSingleOrNull()
    }
  }

  override suspend fun findMostRecentUserDerivedKeyDataByUserSeedDataIdAndUsageType(
    userSeedDataId: BigInteger,
    usageType: DerivedKeyUsageType
  ): UserDerivedKeyData? {
    return delegatingTransactionalOperator.executeReadOnly {
      userDerivedKeyDataRepository.findFirstByUserSeedDataIdAndDerivedKeyUsageTypeOrderByCreatedAtDesc(
        userSeedDataId,
        usageType,
      ).awaitSingleOrNull()
    }
  }

  override suspend fun saveUserDerivedKeyData(userDerivedKeyData: UserDerivedKeyData): UserDerivedKeyData {
    return delegatingTransactionalOperator.executeReadWrite {
      userDerivedKeyDataRepository.save(userDerivedKeyData).awaitSingle()
    }
  }

  private fun createUserKeyData(
    savedUserAccount: UserAccount,
    publicKey: ByteArray,
    encryptedPrivateKey: EncryptedKey,
    encryptedPrivateKeyType: KeyPairType,
    keyUsageType: KeyUsageType
  ): Mono<Pair<UserAccount, UserKeyData>> {
    val userKeyData =
      UserKeyData.create(savedUserAccount.id!!, publicKey, encryptedPrivateKey, encryptedPrivateKeyType, keyUsageType)
    return userKeyDataRepository.save(userKeyData).flatMap { saveUserKeyData ->
      Mono.just(Pair(savedUserAccount, saveUserKeyData))
    }
  }

  private fun createUserDetails(
    userDetails: List<UserDetail>,
    savedProviderExternalUser: ProviderExternalUser,
    savedUserAccount: UserAccount,
    savedUserKeyData: UserKeyData
  ): Mono<UserKeyData> {
    val savedProviderExternalUsers = userDetails.map { userDetail ->
      reactiveSaveUserIdentifierAndProviderExternalUserDetail(
        savedProviderExternalUser.id!!,
        savedUserAccount.id!!,
        userDetail
      )
    }

    // Convert from list of Mono to Mono of list
    val savedProviderExternalUsersMono = Flux.fromIterable(savedProviderExternalUsers).flatMap { it }.collectList()

    return savedProviderExternalUsersMono.map { savedUserKeyData }
  }

  private fun createProviderExternalUser(
    providerMsaId: BigInteger, providerExternalId: String, savedUserKeyData: UserKeyData, savedUserAccount: UserAccount
  ): Mono<Triple<UserAccount, UserKeyData, ProviderExternalUser>> {
    val providerExternalUser = ProviderExternalUser.create(providerMsaId, providerExternalId, savedUserKeyData.id!!)
    return providerExternalUserRepository.save(providerExternalUser).flatMap { savedProviderExternalUser ->
      Mono.just(Triple(savedUserAccount, savedUserKeyData, savedProviderExternalUser))
    }
  }

  private fun saveOrFindUserAccountByUserDetails(userDetails: List<UserDetail>): Mono<UserAccount> {
    val userAccountIdsMono =
      providerExternalUserDetailRepository.findUserAccountIdsByUserDetailsIn(userDetails).collectList()
    return userAccountIdsMono.flatMap { userAccountIds ->
      if (userAccountIds.isNotEmpty()) {
        if (userAccountIds.size == 1) {
          userAccountRepository.findById(userAccountIds[0])
        } else {
          Mono.error(IllegalStateException("Two UserAccounts found for userDetails={$userDetails}"))
        }
      } else {
        val userAccount = UserAccount.create()
        userAccountRepository.save(userAccount)
      }
    }
  }
}

data class EncryptedKeyData(
  val publicKey: ByteArray,
  val encryptedPrivateKey: EncryptedKey,
  val encryptedPrivateKeyType: KeyPairType,
  val keyUsageType: KeyUsageType,
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as EncryptedKeyData

    if (!publicKey.contentEquals(other.publicKey)) return false
    if (encryptedPrivateKey != other.encryptedPrivateKey) return false
    if (encryptedPrivateKeyType != other.encryptedPrivateKeyType) return false
    if (keyUsageType != other.keyUsageType) return false

    return true
  }

  override fun hashCode(): Int {
    var result = publicKey.contentHashCode()
    result = 31 * result + encryptedPrivateKey.hashCode()
    result = 31 * result + encryptedPrivateKeyType.hashCode()
    result = 31 * result + keyUsageType.hashCode()
    return result
  }
}