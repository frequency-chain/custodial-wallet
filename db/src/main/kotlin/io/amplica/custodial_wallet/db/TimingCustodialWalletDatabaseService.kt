package io.amplica.custodial_wallet.db

import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.EncryptedKeyData
import io.amplica.custodial_wallet.db.data.PasskeyWallet
import io.amplica.custodial_wallet.db.repository.*
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.math.BigInteger

class TimingCustodialWalletDatabaseService(private val delegate: CustodialWalletDatabaseService) :
  CustodialWalletDatabaseService {
  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(TimingCustodialWalletDatabaseService::class.java)
  }

  private suspend fun <T> time(name: String, block: suspend () -> T): T {
    return io.amplica.custodial_wallet.util.time(LOG, Level.INFO, name, block)
  }

  override suspend fun saveNewUserData(
    providerMsaId: BigInteger,
    providerExternalId: String,
    userDetails: List<UserDetail>,
    encryptedKeysData: List<EncryptedKeyData>
  ): UserKeyData {
    return time("saveNewUserData") {
      delegate.saveNewUserData(providerMsaId, providerExternalId, userDetails, encryptedKeysData)
    }
  }

  override suspend fun saveUserKeyData(userKeyData: UserKeyData): BigInteger {
    return time("saveUserKeyData") {
      delegate.saveUserKeyData(userKeyData)
    }
  }

  override suspend fun saveNewUserIdentifierForUserAccount(
    userDetail: UserDetail,
    userAccountId: BigInteger
  ): UserAccountUserIdentifier {
    return time("saveNewUserIdentifierForUserAccount") {
      delegate.saveNewUserIdentifierForUserAccount(userDetail, userAccountId)
    }
  }

  override suspend fun findOneUserKeyData(
    providerMsaId: BigInteger,
    providerExternalId: String,
    keyUsageType: KeyUsageType
  ): UserKeyData? {
    return time("findOneUserKeyData") {
      delegate.findOneUserKeyData(providerMsaId, providerExternalId, keyUsageType)
    }
  }

  override suspend fun findOneUserKeyData(
    providerMsaId: BigInteger,
    keyPairType: KeyPairType,
    publicKeyHex: String,
    keyUsageType: KeyUsageType
  ): UserKeyData? {
    return time("findOneUserKeyData") {
      delegate.findOneUserKeyData(providerMsaId, keyPairType, publicKeyHex, keyUsageType)
    }
  }

  override suspend fun findUserKeyDataByProviderMsaIdAndProviderExternalIdAndKeyUsageType(
    providerMsaId: BigInteger,
    providerExternalId: String,
    keyUsageType: KeyUsageType
  ): List<UserKeyData> {
    return time("findUserKeyDataByProviderMsaIdAndProviderExternalIdAndKeyUsageType") {
      delegate.findUserKeyDataByProviderMsaIdAndProviderExternalIdAndKeyUsageType(
        providerMsaId,
        providerExternalId,
        keyUsageType
      )
    }
  }

  override suspend fun findUserKeyDataByUserAccountIdAndKeyUsageType(
    userAccountId: BigInteger,
    keyUsageType: KeyUsageType
  ): List<UserKeyData> {
    return time("findUserKeyDataByUserAccountIdAndKeyUsageType") {
      delegate.findUserKeyDataByUserAccountIdAndKeyUsageType(userAccountId, keyUsageType)
    }
  }

  override suspend fun findUserKeyDataByUserAccountIdKeyUsageTypeAndKeyPairType(
    userAccountId: BigInteger,
    keyUsageType: KeyUsageType,
    keyPairType: KeyPairType
  ): List<UserKeyData> {
    return time("findUserKeyDataByUserAccountIdKeyUsageTypeAndKeyPairType") {
      delegate.findUserKeyDataByUserAccountIdKeyUsageTypeAndKeyPairType(userAccountId, keyUsageType, keyPairType)
    }
  }

  override suspend fun findUserKeyDataMissingKeyType(
    existingKeyPairType: KeyPairType,
    keyUsageType: KeyUsageType,
    missingKeyPairType: KeyPairType,
    limit: Int,
    offset: Int,
  ): List<UserKeyData> {
    return time("findUserKeyDataMissingKeyType") {
      delegate.findUserKeyDataMissingKeyType(existingKeyPairType, keyUsageType, missingKeyPairType, limit, offset)
    }
  }

  override suspend fun createAuditSessionRecord(auditSessionRecord: AuditSessionRecord): AuditSessionRecord {
    return time("createAuditSessionRecord") {
      delegate.createAuditSessionRecord(auditSessionRecord)
    }
  }

  override suspend fun findAuditSessionRecordBySessionId(sessionId: String): AuditSessionRecord {
    return time("findAuditSessionRecordBySessionId") {
      delegate.findAuditSessionRecordBySessionId(sessionId)
    }
  }

  override suspend fun updateAuditSessionRecord(auditSessionRecord: AuditSessionRecord): AuditSessionRecord {
    return time("updateAuditSessionRecord") {
      delegate.updateAuditSessionRecord(auditSessionRecord)
    }
  }

  override suspend fun findUserKeyDataByKeyPairTypeAndPublicKeys(
    keyPairType: KeyPairType,
    publicKeysInHex: List<String>
  ): List<UserKeyData> {
    return time("findUserKeyDataByKeyPairTypeAndPublicKeys") {
      delegate.findUserKeyDataByKeyPairTypeAndPublicKeys(keyPairType, publicKeysInHex)
    }
  }

  override suspend fun findOneProviderExternalUserDetailByUserDetailValueAndUserDetailType(
    userDetailValue: String,
    userDetailType: UserDetailType
  ): ProviderExternalUserDetail {
    return time("findOneProviderExternalUserDetailByUserDetailValueAndUserDetailType") {
      delegate.findOneProviderExternalUserDetailByUserDetailValueAndUserDetailType(userDetailValue, userDetailType)
    }
  }

  override suspend fun findProviderExternalUserDetailByUserDetailValueAndUserDetailTypeAndProviderExternalIdAndProviderMsaId(
    userDetailValue: String,
    userDetailType: UserDetailType,
    providerExternalId: String,
    providerExternalUserProviderMsaId: BigInteger
  ): ProviderExternalUserDetail? {
    return time("findProviderExternalUserDetailByUserDetailValueAndUserDetailTypeAndProviderExternalIdAndProviderMsaId") {
      delegate.findProviderExternalUserDetailByUserDetailValueAndUserDetailTypeAndProviderExternalIdAndProviderMsaId(
        userDetailValue,
        userDetailType,
        providerExternalId,
        providerExternalUserProviderMsaId
      )
    }
  }

  override suspend fun deleteAllUserAccountsByUserDetailCascading(userDetail: UserDetail): Boolean {
    return time("deleteAllUserAccountsByUserDetailCascading") {
      delegate.deleteAllUserAccountsByUserDetailCascading(userDetail)
    }
  }

  override suspend fun deleteAllUserAccountsByProviderMsaIdAndExternalIdCascading(
    providerMsaId: BigInteger,
    externalId: String
  ): Boolean {
    return time("deleteAllUserAccountsByProviderMsaIdAndExternalIdCascading") {
      delegate.deleteAllUserAccountsByProviderMsaIdAndExternalIdCascading(providerMsaId, externalId)
    }
  }

  override suspend fun deleteProviderExternalUserDetailById(id: BigInteger) {
    return time("deleteProviderExternalUserDetailById") {
      delegate.deleteProviderExternalUserDetailById(id)
    }
  }

  override suspend fun deleteProviderExternalUserById(id: BigInteger) {
    return time("deleteProviderExternalUserById") {
      delegate.deleteProviderExternalUserById(id)
    }
  }

  override suspend fun deleteUserKeyDataByUserAccountId(userAccountId: BigInteger) {
    return time("deleteUserKeyDataByUserAccountId") {
      delegate.deleteUserKeyDataByUserAccountId(userAccountId)
    }
  }

  override suspend fun deleteUserAccountByUserAccountId(userAccountId: BigInteger) {
    return time("deleteUserAccountByUserAccountId") {
      delegate.deleteUserAccountByUserAccountId(userAccountId)
    }
  }

  override suspend fun findAccountUserKeyDataByProviderMsaIdAndUserDetail(
    providerMsaId: BigInteger,
    userDetail: UserDetail
  ): UserKeyData? {
    return time("findAccountUserKeyDataByProviderMsaIdAndUserDetail") {
      delegate.findAccountUserKeyDataByProviderMsaIdAndUserDetail(providerMsaId, userDetail)
    }
  }

  override suspend fun findUserAccountByUserIdentifier(userIdentifier: UserDetail): UserAccount? {
    return time("findUserAccountByUserIdentifier") {
      delegate.findUserAccountByUserIdentifier(userIdentifier)
    }
  }

  override suspend fun findOneUserAccountByUserIdentifiers(userIdentifiers: List<UserDetail>): UserAccount? {
    return time("findOneUserAccountByUserIdentifiers") {
      delegate.findOneUserAccountByUserIdentifiers(userIdentifiers)
    }
  }

  override suspend fun findUserIdentifiersByUserAccount(userAccountId: BigInteger): List<UserIdentifier> {
    return time("findUserIdentifiersByUserAccount") {
      delegate.findUserIdentifiersByUserAccount(userAccountId)
    }
  }

  override suspend fun findUserIdentifier(userDetail: UserDetail): UserIdentifier? {
    return time("findUserIdentifier") {
      delegate.findUserIdentifier(userDetail)
    }
  }

  override suspend fun updateUserIdentifierVerifiedDate(userDetail: UserDetail) {
    return time("updateUserIdentifierVerifiedDate") {
      delegate.updateUserIdentifierVerifiedDate(userDetail)
    }
  }

  override suspend fun saveUserIdentifierAndProviderExternalUserDetail(
    providerExternalUserId: BigInteger,
    userAccountId: BigInteger,
    userDetail: UserDetail
  ): ProviderExternalUserDetail {
    return time("saveUserIdentifierAndProviderExternalUserDetail") {
      delegate.saveUserIdentifierAndProviderExternalUserDetail(providerExternalUserId, userAccountId, userDetail)
    }
  }

  override suspend fun saveProviderExternalUserDetail(providerExternalUserDetail: ProviderExternalUserDetail): ProviderExternalUserDetail {
    return time("saveProviderExternalUserDetail") {
      delegate.saveProviderExternalUserDetail(providerExternalUserDetail)
    }
  }

  override suspend fun saveProviderExternalUser(providerExternalUser: ProviderExternalUser): ProviderExternalUser {
    return time("saveProviderExternalUser") {
      delegate.saveProviderExternalUser(providerExternalUser)
    }
  }

  override suspend fun findUserDataByUserAccountIdsAndUserDetail(
    userDetail: UserDetail,
    userAccountIdList: List<BigInteger>
  ): List<UserData> {
    return time("findUserDataByUserAccountIdsAndUserDetail") {
      delegate.findUserDataByUserAccountIdsAndUserDetail(userDetail, userAccountIdList)
    }
  }

  override suspend fun findUserDataByUserAccountIds(userAccountIdList: List<BigInteger>): List<UserData> {
    return time("findUserDataByUserAccountIds") {
      delegate.findUserDataByUserAccountIds(userAccountIdList)
    }
  }

  override suspend fun findUserDetailsByProviderMsaIdAndExternalUserId(
    providerMsaId: BigInteger,
    providerExternalUserId: String
  ): List<ProviderExternalUserDetail> {
    return time("findUserDetailsByProviderMsaIdAndExternalUserId") {
      delegate.findUserDetailsByProviderMsaIdAndExternalUserId(providerMsaId, providerExternalUserId)
    }
  }

  override suspend fun findProviderExternalUserByProviderMsaIdAndKeyPairTypeAndPublicKeyHexAndKeyUsageType(
    providerMsaId: BigInteger,
    keyPairType: KeyPairType,
    publicKeyHex: String,
    keyUsageType: KeyUsageType
  ): ProviderExternalUser? {
    return time("findProviderExternalUserByProviderMsaIdAndKeyPairTypeAndPublicKeyHexAndKeyUsageType") {
      delegate.findProviderExternalUserByProviderMsaIdAndKeyPairTypeAndPublicKeyHexAndKeyUsageType(
        providerMsaId,
        keyPairType,
        publicKeyHex,
        keyUsageType
      )
    }
  }

  override suspend fun findUserDetailsByProviderMsaIdAndKeyPairTypeAndPublicKeyHexAndKeyUsageType(
    providerMsaId: BigInteger,
    keyPairType: KeyPairType,
    publicKeyHex: String,
    keyUsageType: KeyUsageType
  ): List<ProviderExternalUserDetail> {
    return time("findUserDetailsByProviderMsaIdAndKeyPairTypeAndPublicKeyHexAndKeyUsageType") {
      delegate.findUserDetailsByProviderMsaIdAndKeyPairTypeAndPublicKeyHexAndKeyUsageType(
        providerMsaId,
        keyPairType,
        publicKeyHex,
        keyUsageType
      )
    }
  }

  override suspend fun saveUserPassword(userPassword: UserPassword): UserPassword {
    return time("saveUserPassword") {
      delegate.saveUserPassword(userPassword)
    }
  }

  override suspend fun deleteOneUserPasswordById(id: BigInteger) {
    return time("deleteOneUserPasswordById") {
      delegate.deleteOneUserPasswordById(id)
    }
  }

  override suspend fun findOneUserPasswordById(id: BigInteger): UserPassword? {
    return time("findOneUserPasswordById") {
      delegate.findOneUserPasswordById(id)
    }
  }

  override suspend fun findOneUserPasswordByPublicKeyHex(publicKeyHex: String): UserPassword? {
    return time("findOneUserPasswordByPublicKeyHex") {
      delegate.findOneUserPasswordByPublicKeyHex(publicKeyHex)
    }
  }

  override suspend fun findOneUserPasswordByProviderMsaIdAndProviderExternalId(
    providerMsaId: BigInteger,
    providerExternalId: String
  ): UserPassword? {
    return time("findOneUserPasswordByProviderMsaIdAndProviderExternalId") {
      delegate.findOneUserPasswordByProviderMsaIdAndProviderExternalId(providerMsaId, providerExternalId)
    }
  }

  override suspend fun findOneUserPasswordByUserAccountId(userAccountId: BigInteger): UserPassword? {
    return time("findOneUserPasswordByUserAccountId") {
      delegate.findOneUserPasswordByUserAccountId(userAccountId)
    }
  }

  override suspend fun savePasskeyWallet(passkeyWallet: PasskeyWallet) {
    return time("savePasskeyWallet") {
      delegate.savePasskeyWallet(passkeyWallet)
    }
  }

  override suspend fun findPasskeyWalletByCredentialId(credentialIdBase64Url: String): PasskeyWallet? {
    return time("findPasskeyWalletByCredentialId") {
      delegate.findPasskeyWalletByCredentialId(credentialIdBase64Url)
    }
  }

  override suspend fun findPasskeyWalletsByUserAccountId(userAccountId: BigInteger): List<PasskeyWallet> {
    return time("findPasskeyWalletsByUserAccountId") {
      delegate.findPasskeyWalletsByUserAccountId(userAccountId)
    }
  }

  override suspend fun findUserAccountByCredentialId(credentialIdBase64Url: String): UserAccount? {
    return time("findUserAccountByCredentialId") {
      delegate.findUserAccountByCredentialId(credentialIdBase64Url)
    }
  }

  override suspend fun saveWalletMetadata(walletMetadata: WalletMetadata): WalletMetadata {
    return time("saveWalletMetadata") {
      delegate.saveWalletMetadata(walletMetadata)
    }
  }

  override suspend fun findWalletMetadataByWalletId(walletId: BigInteger): List<WalletMetadata> {
    return time("findWalletMetadataByWalletId") {
      delegate.findWalletMetadataByWalletId(walletId)
    }
  }

  override suspend fun findWalletMetadataByCredentialId(credentialId: BigInteger): WalletMetadata? {
    return time("findWalletMetadataByCredentialId") {
      delegate.findWalletMetadataByCredentialId(credentialId)
    }
  }

  override suspend fun findOptInsByUserAccountId(userAccountId: BigInteger): Set<CustodialWalletOptIn> {
    return time("findOptInsByUserAccountId") {
      delegate.findOptInsByUserAccountId(userAccountId)
    }
  }

  override suspend fun findOptInByUserAccountIdAndOptInType(
    userAccountId: BigInteger,
    optInType: OptInType
  ): CustodialWalletOptIn? {
    return time("findOptInByUserAccountIdAndOptInType") {
      delegate.findOptInByUserAccountIdAndOptInType(userAccountId, optInType)
    }
  }

  override suspend fun saveOptIn(optIn: CustodialWalletOptIn): CustodialWalletOptIn {
    return time("saveOptIn") {
      delegate.saveOptIn(optIn)
    }
  }

  override suspend fun findMostRecentUserKeyDataByUserSeedDataId(userSeedDataId: BigInteger): UserKeyData? {
    return time("findMostRecentUserKeyDataByUserSeedDataId") {
      delegate.findMostRecentUserKeyDataByUserSeedDataId(userSeedDataId)
    }
  }

  override suspend fun findUserSeedDataByUserAccountIdAndSeedUsageType(
    userAccountId: BigInteger,
    seedUsageType: SeedUsageType
  ): List<UserSeedData> {
    return time("findUserSeedDataByUserAccountIdAndSeedUsageType") {
      delegate.findUserSeedDataByUserAccountIdAndSeedUsageType(userAccountId, seedUsageType)
    }
  }

  override suspend fun findMostRecentUserSeedDataByUserAccountIdAndSeedUsageType(
    userAccountId: BigInteger,
    seedUsageType: SeedUsageType
  ): UserSeedData? {
    return time("findMostRecentUserSeedDataByUserAccountIdAndSeedUsageType") {
      delegate.findMostRecentUserSeedDataByUserAccountIdAndSeedUsageType(userAccountId, seedUsageType)
    }
  }

  override suspend fun saveUserSeedData(userSeedData: UserSeedData): UserSeedData {
    return time("saveUserSeedData") {
      delegate.saveUserSeedData(userSeedData)
    }
  }

  override suspend fun findUserDerivedKeyDataByUserSeedDataId(userSeedDataId: BigInteger): List<UserDerivedKeyData> {
    return time("findUserDerivedKeyDataByUserSeedDataId") {
      delegate.findUserDerivedKeyDataByUserSeedDataId(userSeedDataId)
    }
  }

  override suspend fun findUserDerivedKeyDataByDerivationPath(
    derivationPath: String,
    offset: Int,
    limit: Int,
  ): List<UserDerivedKeyData> {
    return time("findUserDerivedKeyDataByDerivationPath") {
      delegate.findUserDerivedKeyDataByDerivationPath(derivationPath, offset, limit)
    }
  }

  override suspend fun findMostRecentUserDerivedKeyDataByDerivationPath(derivationPath: String): UserDerivedKeyData? {
    return time("findMostRecentUserDerivedKeyDataByDerivationPath") {
      delegate.findMostRecentUserDerivedKeyDataByDerivationPath(derivationPath)
    }
  }

  override suspend fun findMostRecentUserDerivedKeyDataByDerivationPathPrefixed(derivationPathPrefix: String): UserDerivedKeyData? {
    return time("findMostRecentUserDerivedKeyDataByDerivationPathPrefixed") {
      delegate.findMostRecentUserDerivedKeyDataByDerivationPathPrefixed(derivationPathPrefix)
    }
  }

  override suspend fun findMostRecentUserDerivedKeyDataByUserSeedDataIdAndUsageType(
    userAccountId: BigInteger,
    usageType: DerivedKeyUsageType
  ): UserDerivedKeyData? {
    return time("findMostRecentUserDerivedKeyDataByUserAccountIdAndUsageType") {
      delegate.findMostRecentUserDerivedKeyDataByUserSeedDataIdAndUsageType(userAccountId, usageType)
    }
  }

  override suspend fun saveUserDerivedKeyData(userDerivedKeyData: UserDerivedKeyData): UserDerivedKeyData {
    return time("saveUserDerivedKeyData") {
      delegate.saveUserDerivedKeyData(userDerivedKeyData)
    }
  }
}
