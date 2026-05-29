package io.amplica.custodial_wallet.orchestration

import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifier
import io.amplica.custodial_wallet.controller.util.PublicKeyAndChainStateRequest
import io.amplica.custodial_wallet.controller.util.PublicKeyAndChainStateResponse
import io.amplica.custodial_wallet.db.repository.*
import io.amplica.custodial_wallet.dto.FinalizedHeadNumberResponse
import io.amplica.custodial_wallet.dto.GetHandleResponse
import io.amplica.custodial_wallet.dto.LatestBlockNumberResponse
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.service.provider_metadata.ProviderMetadata
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto
import io.amplica.custodial_wallet.util.key_creation.PublicKeyFormat
import io.amplica.frequency.client.CommonPrimitivesProviderRegistryEntry
import io.amplica.frequency.crypto.AccountKeyPair
import java.math.BigInteger
import java.net.URI

interface LookupOrchestrationService {
  suspend fun findPublicKeysIn(publicKeysRequest: PublicKeysRequest): PublicKeysResponse
  suspend fun findOneUserKeyDataWithPublicKeyOrThrow(providerMsaId: BigInteger, keyPairType: KeyPairType, publicKeyHex: String, keyUsageType: KeyUsageType, sessionId: String? = null): UserKeyData
  suspend fun findUserKeyData(userAccountId: BigInteger, keyUsageType: KeyUsageType, keyPairType: KeyPairType): UserKeyData?
  /**
   * Finds the most recently-created [UserKeyData] of [KeyUsageType] `usage` associated with the given `userAccountId`.
   * Makes the best effort to find a keypair matching the specified `preferredKeyPairType`, but may return a keypair
   * of any type if the preferred is not found.
   *
   * If there is no [UserKeyData] matching the given `usage` and associated with the given `userAccountId`,
   * an [ApiException] is thrown.
   */
  suspend fun findUserKeyDataOrThrow(userAccountId: BigInteger, usage: KeyUsageType, preferredKeyPairType: KeyPairType): UserKeyData
  suspend fun findAllUserKeyDataByUserAccountIdAndKeyUsageType(userAccountId: BigInteger, keyUsageType: KeyUsageType): List<UserKeyData>
  suspend fun validateIdentifierNotFoundOrThrow(userIdentifier: UserIdentifier)
  suspend fun findProviderExternalUserByProviderMsaIdAndKeyPairTypeAndPublicKeyHexAndKeyUsageType(providerMsaId: BigInteger, keyPairType: KeyPairType, publicKeyHex: String, keyUsageType: KeyUsageType) : ProviderExternalUser
  suspend fun findAccountUserKeyDataByProviderMsaIdAndUserDetail(providerMsaId: BigInteger, userDetail: UserDetail): UserKeyData
  suspend fun getExistingAccountIdFromContactMethod(contactMethod: String, contactMethodType: UserIdentifierType): BigInteger?
  suspend fun findUserDetailsFromUserAccountId(userAccountId: BigInteger): List<UserDetail>
  suspend fun getUserDataForUserAccountId(userAccountId: BigInteger): List<UserData>
  suspend fun getUserDataFromWebsiteSession(websiteSession: WebsiteSession): List<UserData>
  suspend fun getFinalizedHeadNumber(): FinalizedHeadNumberResponse
  suspend fun getLatestBlockNumber(): LatestBlockNumberResponse
  suspend fun retrieveCurrentBlockNumber(): Long
  suspend fun getPublicKeyAndChainState(publicKeyAndChainStateRequest: PublicKeyAndChainStateRequest): PublicKeyAndChainStateResponse
  suspend fun getHandle(userMsaId: BigInteger): GetHandleResponse
  suspend fun validateHandle(handle: String)
  suspend fun getGrantedSchemasByMsaId(delegatorMsaId: BigInteger, providerMsaId: BigInteger): List<Int>
  suspend fun getMsaIdByPublicKeyHex(publicKeyHex: String) : BigInteger
  suspend fun getMsaIdByPublicKey(publicKey: ByteArray) : BigInteger?
  suspend fun getMsaIdByUserIdentifier(userIdentifier: UserIdentifier): BigInteger
  suspend fun retrieveMsaId(publicKeyFormat: PublicKeyFormat, encodedPublicKey: String): BigInteger
  suspend fun retrieveMsaId(publicKey: PublicKeyDto): BigInteger
  suspend fun getProviderName(msaId: BigInteger): String
  suspend fun getProviderRegistryEntryV2(msaId: BigInteger): CommonPrimitivesProviderRegistryEntry
  suspend fun validateMsa(providerMsaId: BigInteger, providerPublicKeyDto: PublicKeyDto)
  suspend fun findSignUpRequestBySessionId(sessionId: String): SignUpRequest
  suspend fun findLoginRequestBySessionId(sessionId: String): LoginRequest
  suspend fun findSessionInfoBySessionId(sessionId: String): SessionInfo
  suspend fun findWebsiteSessionBySessionId(sessionId: String): WebsiteSession
  suspend fun findWebsiteSessionBySessionIdAndAuthorizationCode(sessionId: String, authorizationCode: String): WebsiteSession
  suspend fun findWebsiteSessionBySessionIdAndVerificationCode(sessionId: String, verificationCode: String): WebsiteSession
  suspend fun findBatchPayloadToSignRequestBySessionIdAndAuthenticationCode(sessionId: String, authenticationCode: String): BatchPayloadToSignRequest
  suspend fun findBatchPayloadToSignRequestBySessionIdAndAuthorizationCode(sessionId: String, authorizationCode: String): BatchPayloadToSignRequest
  /** Asserts that a given URL is a legitimate URL for the custodial wallet application */
  fun verifyWhitelistedApplicationUrl(url: String)

  // NOTE(Julian, 2025-01-22): These methods are being phased out because they do not take into account a provider
  // having multiple 'applications' (e.g., MeWe has website and camera app), but they are used extensively in our
  // 'dying' webview code, so they remain as vestigial methods until the webview code can be removed (and then set
  // on fire and tossed in a deep well).
  // See: #1142 (and milestone 26)
  @Deprecated("This method is not aware of providers having multiple applications")
  suspend fun verifyWhitelistedProviderMsaId(providerMsaId: BigInteger)
  @Deprecated("This method is not aware of providers having multiple applications", ReplaceWith("lookupService.getProviderMetaDataForApplication(providerMsaId, applicationVerifiedCredentialUrl)"))
  suspend fun getProviderMetaData(providerMsaId: BigInteger): ProviderMetadata?
  @Deprecated("This method is not aware of providers having multiple applications", ReplaceWith("lookupService.getProviderMetaDataForApplication(providerMsaId, applicationVerifiedCredentialUrl)"))
  suspend fun getProviderMetaDataOrThrow(providerMsaId: BigInteger): ProviderMetadata

  suspend fun getProviderMetadataForApplication(providerMsaId: BigInteger, applicationVerifiedCredentialUrl: URI): ProviderMetadata?
  fun verifyUrlWhitelistedByProviderMetadata(url: URI, providerMetadata: ProviderMetadata)
  suspend fun getDecryptedPrivateKey(userKeyData: UserKeyData): ByteArray
  suspend fun getDecryptedAccountKeyPair(userKeyData: UserKeyData): AccountKeyPair
  fun getIdentifierOfTypeOrThrow(sessionId: String, userIdentifiers: List<UserIdentifier>, type: UserIdentifierType): UserIdentifier
  suspend fun getVerifiedMillisForPhone(userIdentifier: UserIdentifier): BigInteger?
  suspend fun findUserAccountByUserIdentifier(userIdentifier: UserIdentifier): UserAccount?
  suspend fun getGrantedSchemasByPublicKey(userPublicKey: ByteArray, providerMsaId: BigInteger): List<Int>
  suspend fun getGraphKeysRegisteredOnChainForUser(msaId: BigInteger, loggingContext: Map<String, Any> = emptyMap()): List<ByteArray>
  suspend fun findSiwaSessionOrThrow(sessionId: String): SiwaSession
  suspend fun findSiwaSession(sessionId: String): SiwaSession?
  suspend fun findAuthenticatedSiwaSessionOrThrow(sessionId: String): AuthenticatedSiwaSession
  /**
   * Finds an AuthenticatedSiwaSession or 'logged in' WebsiteSession and returns the associated user data.
   *
   * Note: If two sessions are present the `WebsiteSession` takes precedence.
   */
  suspend fun findWebsiteOrSiwaAuthenticatedUserDataOrThrow(sessionId: String): AuthenticatedUserData
  suspend fun getLatestSchemaIdForIntent(intentName: String): Int
  suspend fun getUniversalAddressesByMsaId(msaId: BigInteger): List<ByteArray>
  suspend fun getUserAccountIdByMsaIdOrThrow(msaId: BigInteger): BigInteger
}
