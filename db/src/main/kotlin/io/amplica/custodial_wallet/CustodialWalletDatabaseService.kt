package io.amplica.custodial_wallet

import io.amplica.custodial_wallet.db.data.PasskeyWallet
import io.amplica.custodial_wallet.db.repository.*
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import java.math.BigInteger

interface CustodialWalletDatabaseService {

  /**
   * Save a new user making a new set of keys
   */
  suspend fun saveNewUserData(
    providerMsaId: BigInteger,
    providerExternalId: String,
    userDetails: List<UserDetail>,
    encryptedKeysData: List<EncryptedKeyData>
  ): UserKeyData

  suspend fun saveUserKeyData(userKeyData: UserKeyData): BigInteger

  suspend fun saveNewUserIdentifierForUserAccount(
    userDetail: UserDetail,
    userAccountId: BigInteger
  ): UserAccountUserIdentifier


  /**
   * Find a user based on the requesters msa id, and the users external id
   * Truly returns a list underneath and explods with more than one entry, this behavior is dumb, I believe this was supposed to be
   * used with graph stuff and take the "top one", not messing it with it for now
   */
  suspend fun findOneUserKeyData(providerMsaId: BigInteger, providerExternalId: String, keyUsageType: KeyUsageType): UserKeyData?

  //TODO should drop the findOne as it actually only finds one, this violates our naming convention
  suspend fun findOneUserKeyData(providerMsaId: BigInteger, keyPairType: KeyPairType, publicKeyHex: String, keyUsageType: KeyUsageType): UserKeyData?
  suspend fun findUserKeyDataByProviderMsaIdAndProviderExternalIdAndKeyUsageType(providerMsaId: BigInteger, providerExternalId: String, keyUsageType: KeyUsageType): List<UserKeyData>
  suspend fun findUserKeyDataByUserAccountIdAndKeyUsageType(userAccountId: BigInteger, keyUsageType: KeyUsageType): List<UserKeyData>
  suspend fun findUserKeyDataByUserAccountIdKeyUsageTypeAndKeyPairType(userAccountId: BigInteger, keyUsageType: KeyUsageType, keyPairType: KeyPairType): List<UserKeyData>
  suspend fun findUserKeyDataMissingKeyType(existingKeyPairType: KeyPairType, keyUsageType: KeyUsageType, missingKeyPairType: KeyPairType, limit: Int, offset: Int): List<UserKeyData>

  suspend fun createAuditSessionRecord(auditSessionRecord: AuditSessionRecord): AuditSessionRecord
  suspend fun findAuditSessionRecordBySessionId(sessionId: String): AuditSessionRecord
  suspend fun updateAuditSessionRecord(auditSessionRecord: AuditSessionRecord): AuditSessionRecord
  suspend fun findUserKeyDataByKeyPairTypeAndPublicKeys(keyPairType: KeyPairType, publicKeysInHex: List<String>): List<UserKeyData>
  suspend fun findOneProviderExternalUserDetailByUserDetailValueAndUserDetailType(
    userDetailValue: String,
    userDetailType: UserDetailType
  ): ProviderExternalUserDetail

  suspend fun findProviderExternalUserDetailByUserDetailValueAndUserDetailTypeAndProviderExternalIdAndProviderMsaId(
    userDetailValue: String,
    userDetailType: UserDetailType,
    providerExternalId: String,
    providerExternalUserProviderMsaId: BigInteger
  ): ProviderExternalUserDetail?

  suspend fun deleteAllUserAccountsByUserDetailCascading(userDetail: UserDetail): Boolean
  suspend fun deleteAllUserAccountsByProviderMsaIdAndExternalIdCascading(providerMsaId: BigInteger, externalId: String): Boolean
  suspend fun deleteProviderExternalUserDetailById(id: BigInteger)
  suspend fun deleteProviderExternalUserById(id: BigInteger)
  suspend fun deleteUserKeyDataByUserAccountId(userAccountId: BigInteger)
  suspend fun deleteUserAccountByUserAccountId(userAccountId: BigInteger)
  suspend fun findAccountUserKeyDataByProviderMsaIdAndUserDetail(providerMsaId: BigInteger, userDetail: UserDetail): UserKeyData?
  suspend fun findUserAccountByUserIdentifier(userIdentifier: UserDetail): UserAccount?
  suspend fun findOneUserAccountByUserIdentifiers(userIdentifiers: List<UserDetail>): UserAccount?
  suspend fun findUserIdentifiersByUserAccount(userAccountId: BigInteger): List<UserIdentifier>
  suspend fun findUserIdentifier(userDetail: UserDetail): UserIdentifier?
  suspend fun updateUserIdentifierVerifiedDate(userDetail: UserDetail)
  suspend fun saveUserIdentifierAndProviderExternalUserDetail(
    providerExternalUserId: BigInteger,
    userAccountId: BigInteger,
    userDetail: UserDetail
  ): ProviderExternalUserDetail
  suspend fun saveProviderExternalUserDetail(providerExternalUserDetail: ProviderExternalUserDetail): ProviderExternalUserDetail
  suspend fun saveProviderExternalUser(providerExternalUser: ProviderExternalUser): ProviderExternalUser
  //NOTE: findUserDataByUserAccountIdsAndUserDetail may be obsolete at this point since the user detail is not needed to find the list of user data
  suspend fun findUserDataByUserAccountIdsAndUserDetail(userDetail: UserDetail, userAccountIdList: List<BigInteger>): List<UserData>
  suspend fun findUserDataByUserAccountIds(userAccountIdList: List<BigInteger>): List<UserData>
  suspend fun findUserDetailsByProviderMsaIdAndExternalUserId(providerMsaId: BigInteger, providerExternalUserId: String): List<ProviderExternalUserDetail>
  suspend fun findProviderExternalUserByProviderMsaIdAndKeyPairTypeAndPublicKeyHexAndKeyUsageType(providerMsaId: BigInteger,
                                                                                                  keyPairType: KeyPairType,
                                                                                                  publicKeyHex: String,
                                                                                                  keyUsageType: KeyUsageType): ProviderExternalUser?
  suspend fun findUserDetailsByProviderMsaIdAndKeyPairTypeAndPublicKeyHexAndKeyUsageType(providerMsaId: BigInteger, keyPairType: KeyPairType, publicKeyHex: String, keyUsageType: KeyUsageType): List<ProviderExternalUserDetail>
  suspend fun saveUserPassword(userPassword: UserPassword): UserPassword
  suspend fun deleteOneUserPasswordById(id: BigInteger)
  suspend fun findOneUserPasswordById(id: BigInteger): UserPassword?
  suspend fun findOneUserPasswordByPublicKeyHex(publicKeyHex: String): UserPassword?
  suspend fun findOneUserPasswordByProviderMsaIdAndProviderExternalId(
    providerMsaId: BigInteger,
    providerExternalId: String
  ): UserPassword?
  suspend fun findOneUserPasswordByUserAccountId(userAccountId: BigInteger): UserPassword?
  suspend fun savePasskeyWallet(passkeyWallet: PasskeyWallet)
  suspend fun findPasskeyWalletByCredentialId(credentialIdBase64Url: String): PasskeyWallet?
  suspend fun findPasskeyWalletsByUserAccountId(userAccountId: BigInteger): List<PasskeyWallet>
  suspend fun findUserAccountByCredentialId(credentialIdBase64Url: String): UserAccount?
  suspend fun saveWalletMetadata(walletMetadata: WalletMetadata): WalletMetadata
  suspend fun findWalletMetadataByWalletId(walletId: BigInteger): List<WalletMetadata>
  suspend fun findWalletMetadataByCredentialId(credentialId: BigInteger): WalletMetadata?
  suspend fun findOptInsByUserAccountId(userAccountId: BigInteger): Set<CustodialWalletOptIn>
  suspend fun findOptInByUserAccountIdAndOptInType(userAccountId: BigInteger, optInType: OptInType): CustodialWalletOptIn?
  suspend fun saveOptIn(optIn: CustodialWalletOptIn): CustodialWalletOptIn
  suspend fun findMostRecentUserKeyDataByUserSeedDataId(userSeedDataId: BigInteger): UserKeyData?
  suspend fun findUserSeedDataByUserAccountIdAndSeedUsageType(userAccountId: BigInteger, seedUsageType: SeedUsageType): List<UserSeedData>
  suspend fun findMostRecentUserSeedDataByUserAccountIdAndSeedUsageType(userAccountId: BigInteger, seedUsageType: SeedUsageType): UserSeedData?
  suspend fun saveUserSeedData(userSeedData: UserSeedData): UserSeedData
  suspend fun findUserDerivedKeyDataByUserSeedDataId(userSeedDataId: BigInteger): List<UserDerivedKeyData>
  suspend fun findUserDerivedKeyDataByDerivationPath(derivationPath: String, offset: Int, limit: Int): List<UserDerivedKeyData>
  suspend fun findMostRecentUserDerivedKeyDataByDerivationPath(derivationPath: String): UserDerivedKeyData?
  //NOTE this method is NOT production grade and will be removed in the future https://github.com/ProjectLibertyLabs/custodial-wallet/issues/1883
  suspend fun findMostRecentUserDerivedKeyDataByDerivationPathPrefixed(derivationPathPrefix: String): UserDerivedKeyData?
  suspend fun findMostRecentUserDerivedKeyDataByUserSeedDataIdAndUsageType(userSeedDataId: BigInteger, usageType: DerivedKeyUsageType): UserDerivedKeyData?
  suspend fun saveUserDerivedKeyData(userDerivedKeyData: UserDerivedKeyData): UserDerivedKeyData
}
