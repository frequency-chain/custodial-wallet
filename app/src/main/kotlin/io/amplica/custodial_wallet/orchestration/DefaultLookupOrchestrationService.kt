package io.amplica.custodial_wallet.orchestration

import com.google.common.collect.FluentIterable
import com.strategyobject.substrateclient.crypto.ss58.SS58AddressFormat
import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.client.kms.EncryptedKey
import io.amplica.custodial_wallet.client.kms.KmsClient
import io.amplica.custodial_wallet.client.kms.KmsDecryptionKey
import io.amplica.custodial_wallet.client.redis.CustodialWalletRedisClient
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifier
import io.amplica.custodial_wallet.controller.util.PublicKeyAndChainStateRequest
import io.amplica.custodial_wallet.controller.util.PublicKeyAndChainStateResponse
import io.amplica.custodial_wallet.db.repository.*
import io.amplica.custodial_wallet.dto.FinalizedHeadNumberResponse
import io.amplica.custodial_wallet.dto.GetHandleResponse
import io.amplica.custodial_wallet.dto.LatestBlockNumberResponse
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.service.provider_metadata.ProviderMetadata
import io.amplica.custodial_wallet.service.provider_metadata.ProviderMetadataService
import io.amplica.custodial_wallet.util.*
import io.amplica.custodial_wallet.util.key_creation.*
import io.amplica.custodial_wallet.web.LoggingAttributes
import io.amplica.frequency.client.CommonPrimitivesProviderRegistryEntry
import io.amplica.frequency.client.FrequencyClient
import io.amplica.frequency.crypto.AccountKeyPair
import io.amplica.frequency.crypto.KeyPair
import io.amplica.frequency.crypto.provider.Secp256K1CryptoProvider
import io.amplica.frequency.crypto.provider.Sr25519CryptoProvider
import io.amplica.frequency.crypto.toPrivateKeyBytes
import io.amplica.frequency.crypto.toPublicKeyBytes
import io.amplica.frequency.util.GraphHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import net.logstash.logback.argument.StructuredArguments
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.net.URI
import java.util.*

data class PublicKeyDtoWrapper(val publicKeyDto: PublicKeyDto, val publicKeyHex: String)

data class KeyPairTypeAndPublicKeyHex(val keyPairType: KeyPairType, val publicKeyHex: String)

data class DefaultLookupOrchestrationServiceProperties (
  val defaultMaxRecords: Int,
  val sS58AddressFormat: SS58AddressFormat,
  val reservedWords: Set<String>,
  val blockedCharacters: Set<Char>,
  val applicationOrigin: String,
  val providerWhitelistAllowLocalhost: Boolean
)

class DefaultLookupOrchestrationService(
  private val properties: DefaultLookupOrchestrationServiceProperties,
  private val databaseService: CustodialWalletDatabaseService,
  private val frequencyClient: FrequencyClient,
  private val redisClient: CustodialWalletRedisClient,
  private val providerMetadataService: ProviderMetadataService,
  private val kmsClient: KmsClient,
  private val graphHelper: GraphHelper
) : LookupOrchestrationService {

  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(DefaultLookupOrchestrationService::class.java)
  }

  override suspend fun findPublicKeysIn(publicKeysRequest: PublicKeysRequest): PublicKeysResponse {
    if (publicKeysRequest.publicKeys.size > properties.defaultMaxRecords) {
      throw ApiException(
        ApiError.TOO_MANY_RECORDS_REQUESTED,
        "Too many publicKeys are being searched for there were ${publicKeysRequest.publicKeys.size} which is greater than ${properties.defaultMaxRecords}"
      )
    }
    val allPublicKeyDtoWrappers = FluentIterable.from(publicKeysRequest.publicKeys).map {
      PublicKeyDtoWrapper(it.publicKey, normalizeToHex(it.publicKey))
    }

    val publicKeyHexToResultMap = HashMap<KeyPairTypeAndPublicKeyHex, UserKeyData>()
    val keyPairTypeToPublicKeyDtoWrappers = FluentIterable.from(allPublicKeyDtoWrappers).index { it.publicKeyDto.type }
    val keyPairTypes = keyPairTypeToPublicKeyDtoWrappers.keySet()
    val publicKeyResponses: MutableList<PublicKeyResponse> = LinkedList()
    val publicKeysResponse = PublicKeysResponse(publicKeyResponses)
    for (keyPairType in keyPairTypes) {
      val publicKeyDtoWrappersForKeyPairType = keyPairTypeToPublicKeyDtoWrappers[keyPairType]
      val publicKeysInHex = FluentIterable.from(publicKeyDtoWrappersForKeyPairType).map { it.publicKeyHex }
      val userKeyDataList: List<UserKeyData> =
        databaseService.findUserKeyDataByKeyPairTypeAndPublicKeys(keyPairType, publicKeysInHex)
      for (userKey in userKeyDataList) {
        publicKeyHexToResultMap[KeyPairTypeAndPublicKeyHex(userKey.encryptedPrivateKeyType, userKey.publicKeyHex)] =
          userKey
      }
    }

    for (publicKeyDtoWrapper in allPublicKeyDtoWrappers) {
      val userKeyData = publicKeyHexToResultMap[KeyPairTypeAndPublicKeyHex(
        publicKeyDtoWrapper.publicKeyDto.type,
        publicKeyDtoWrapper.publicKeyHex
      )]
      val isPresent = userKeyData != null
      publicKeyResponses.add(PublicKeyResponse(publicKeyDtoWrapper.publicKeyDto, isPresent))
    }

    return publicKeysResponse
  }

  override suspend fun findOneUserKeyDataWithPublicKeyOrThrow(
    providerMsaId: BigInteger,
    keyPairType: KeyPairType,
    publicKeyHex: String,
    keyUsageType: KeyUsageType,
    sessionId: String?
  ): UserKeyData {
    var message =
      "No ${keyUsageType.name} user key data found for provider_msa_id:$providerMsaId and user publicKey:${publicKeyHex}"
    if (sessionId != null) message += " with sessionId:$sessionId"
    return databaseService.findOneUserKeyData(providerMsaId, keyPairType, publicKeyHex, keyUsageType)
      ?: throw ApiException(
        ApiError.NO_USER_KEY_DATA_FOUND,
        message
      )
  }

  override suspend fun findUserKeyData(
    userAccountId: BigInteger,
    keyUsageType: KeyUsageType,
    keyPairType: KeyPairType,
  ): UserKeyData? {
    val matchingRows = databaseService.findUserKeyDataByUserAccountIdKeyUsageTypeAndKeyPairType(
      userAccountId,
      keyUsageType,
      keyPairType,
    )

    return when (matchingRows.size) {
      0 -> null
      1 -> matchingRows.first()
      else -> {
        LOG.warn(
          "More than one ${keyUsageType.name} user key data rows found for type=$keyPairType userAccountId=$userAccountId"
        )
        matchingRows.maxBy { it.createdAt }
      }
    }
  }

  override suspend fun findUserKeyDataOrThrow(
    userAccountId: BigInteger,
    usage: KeyUsageType,
    preferredKeyPairType: KeyPairType,
  ): UserKeyData {
    val accountKeyDataRows = findAllUserKeyDataByUserAccountIdAndKeyUsageType(userAccountId, usage)

    if (accountKeyDataRows.isEmpty()) {
      throw ApiException(
        ApiError.NO_USER_KEY_DATA_FOUND,
        "No ${KeyUsageType.ACCOUNT.name} user key data found for userAccountId=$userAccountId"
      )
    }

    val preferredRows = accountKeyDataRows.filter { it.encryptedPrivateKeyType == preferredKeyPairType }
    return when (preferredRows.size) {
      // The user doesn't have the preferred type of keypair so we fall back to the most recently-created keypair
      0 -> accountKeyDataRows.maxBy { it.createdAt }

      // The user has a single key of the preferred type (ideal case)
      1 -> preferredRows.first()

      // The user has multiple keys of the preferred type, and we take the most recently-created keypair
      else -> {
        LOG.warn(
          "More than one ${KeyUsageType.ACCOUNT.name} user key data rows found for type=$preferredKeyPairType userAccountId=$userAccountId"
        )
        preferredRows.maxBy { it.createdAt }
      }
    }
  }

  override suspend fun findAllUserKeyDataByUserAccountIdAndKeyUsageType(
    userAccountId: BigInteger,
    keyUsageType: KeyUsageType
  ): List<UserKeyData> {
    return databaseService.findUserKeyDataByUserAccountIdAndKeyUsageType(userAccountId, keyUsageType)
  }

  override suspend fun validateIdentifierNotFoundOrThrow(
    userIdentifier: UserIdentifier,
  ) {
    val userWithIdentifier = databaseService.findUserAccountByUserIdentifier(mapUserIdentifierToUserDetail(userIdentifier))
    if(userWithIdentifier != null) {
      throw ApiException(ApiError.IDENTIFIER_ALREADY_USED, "${userIdentifier.value} is already in use")
    }
  }

  override suspend fun findProviderExternalUserByProviderMsaIdAndKeyPairTypeAndPublicKeyHexAndKeyUsageType(
    providerMsaId: BigInteger,
    keyPairType: KeyPairType,
    publicKeyHex: String,
    keyUsageType: KeyUsageType
  ): ProviderExternalUser {
    return databaseService.findProviderExternalUserByProviderMsaIdAndKeyPairTypeAndPublicKeyHexAndKeyUsageType(
      providerMsaId,
      keyPairType,
      publicKeyHex,
      keyUsageType
    )
      ?: throw ApiException(
        ApiError.NO_USER_FOUND_ERROR,
        "No provider external user found for providerMsaId=${providerMsaId} and publicKeyHex=${publicKeyHex}"
      )
  }

  override suspend fun findAccountUserKeyDataByProviderMsaIdAndUserDetail(
    providerMsaId: BigInteger,
    userDetail: UserDetail
  ): UserKeyData {
    return databaseService.findAccountUserKeyDataByProviderMsaIdAndUserDetail(providerMsaId, userDetail)
      ?: throw ApiException(
        ApiError.NO_USER_KEY_DATA_FOUND,
        "No user key data found",
        mapOf("providerMsaId" to providerMsaId, "userIdentifier" to userDetail.value)
      )
  }

  override suspend fun findUserDetailsFromUserAccountId(userAccountId: BigInteger): List<UserDetail> {
      val accountUserIdentifiers = databaseService.findUserIdentifiersByUserAccount(userAccountId)
      return accountUserIdentifiers.map { userIdentifier ->
          UserDetail(userIdentifier.value, userIdentifier.type)
      }
  }

  override suspend fun getExistingAccountIdFromContactMethod(contactMethod: String, contactMethodType: UserIdentifierType): BigInteger? {
    val userDetailType = when (contactMethodType) {
      UserIdentifierType.EMAIL -> UserDetailType.EMAIL
      UserIdentifierType.PHONE_NUMBER -> UserDetailType.PHONE_NUMBER
    }

    return databaseService.findUserAccountByUserIdentifier(UserDetail(contactMethod, userDetailType))?.id
  }

  override suspend fun getUserDataForUserAccountId(userAccountId: BigInteger): List<UserData> {
    return databaseService.findUserDataByUserAccountIds(listOf(userAccountId))
  }

  override suspend fun getUserDataFromWebsiteSession(websiteSession: WebsiteSession): List<UserData> {
    val userAccountIds = websiteSession.userAccountIds
      ?: throw ApiException(ApiError.NO_USER_ACCOUNT_ID_FOUND, "No user account ids found in this web session")
    return databaseService.findUserDataByUserAccountIds(userAccountIds)
  }

  override suspend fun getPublicKeyAndChainState(publicKeyAndChainStateRequest: PublicKeyAndChainStateRequest): PublicKeyAndChainStateResponse {
    val userIdentifier = publicKeyAndChainStateRequest.userIdentifier
    val providerMsaId = retrieveMsaId(publicKeyAndChainStateRequest.providerPublicKey.format, publicKeyAndChainStateRequest.providerPublicKey.encodedValue)
    validateMsa(providerMsaId, publicKeyAndChainStateRequest.providerPublicKey)
    val userDetail = UserDetail(userIdentifier.value, UserDetailType.valueOf(userIdentifier.type.name))
    val userKeyData = databaseService.findAccountUserKeyDataByProviderMsaIdAndUserDetail(providerMsaId, userDetail)
    if(userKeyData == null) {
      throw ApiException(ApiError.NO_PUBLIC_KEY_FOUND, "No public key information found for user=${userIdentifier}")
    }else {
      val publicKeyHex = userKeyData.publicKeyHex
      val publicKeyBytes = fromHex(publicKeyHex)
      val msaId = frequencyClient.getMsaIdByAccountId(publicKeyBytes).await()
      val existsOnChain = msaId != null
      val encodeBase58String = Sr25519KeyPairCreator.encodeSr25519PublicKey(publicKeyBytes, properties.sS58AddressFormat)
      val userPublicKeyDto =
        PublicKeyDto(encodeBase58String, Encoding.BASE_58, PublicKeyFormat.SS58, userKeyData.encryptedPrivateKeyType)
      return PublicKeyAndChainStateResponse(userPublicKeyDto, existsOnChain)
    }
  }

  override suspend fun getFinalizedHeadNumber(): FinalizedHeadNumberResponse {
    val finalizedHeadNumber = frequencyClient.getFinalizedHeadBlockNumber().await()
      ?: throw ApiException (
        ApiError.NO_BLOCK_NUMBER_FOUND_ERROR,
        "No Finalized Block Number was found"
      )
    return FinalizedHeadNumberResponse(finalizedHeadNumber)
  }

  override suspend fun getLatestBlockNumber(): LatestBlockNumberResponse {
    val latestBlockNumber = frequencyClient.getLastBlockNumber().await()
      ?: throw ApiException (
        ApiError.NO_BLOCK_NUMBER_FOUND_ERROR,
        "No Latest Block Number was found"
      )
    return LatestBlockNumberResponse(latestBlockNumber)
  }

  override suspend fun retrieveCurrentBlockNumber(): Long {
    val blockNumber = withContext(Dispatchers.IO) {
      frequencyClient.getLastBlockNumber().await()
    }
      ?: throw ApiException(
        ApiError.NO_BLOCK_NUMBER_FOUND_ERROR,
        "No Finalized Block Number was found"
      )
    return blockNumber.toLong()
  }

  override suspend fun getHandle(userMsaId: BigInteger): GetHandleResponse {
    return try {
      val handleResponse = frequencyClient.getHandle(userMsaId).await()
      GetHandleResponse(handleResponse.baseHandle, handleResponse.canonicalBase, handleResponse.handleSuffix)
    } catch(_: java.lang.Exception) {
      LOG.info("User has not claimed a handle at this time")
      GetHandleResponse("", "", 0)
    }
  }

  override suspend fun validateHandle(handle: String) {
    if (handle.trim().isEmpty()) {
      throw ApiException(ApiError.INVALID_HANDLE, "Handle cannot be spaces/empty")
    }
    if (handle in properties.reservedWords) {
      throw ApiException(ApiError.INVALID_HANDLE, "Handle $handle contains an invalid word, please change handle")
    }
    if (handle.toSet().intersect(properties.blockedCharacters).isNotEmpty()) {
      throw ApiException(ApiError.INVALID_HANDLE, "Handle $handle contains an invalid character, please change handle")
    }

    val handleIsValid = frequencyClient.validateHandle(handle).await()
    if (!handleIsValid) {
      throw ApiException(
        ApiError.INVALID_HANDLE,
        "Handle $handle contains an invalid character or word, or is otherwise not valid, please change handle"
      )
    }

    val nextSuffixes = frequencyClient.getNextSuffixes(handle, 1).await().suffixes
    if (nextSuffixes.isEmpty()) {
      throw ApiException(ApiError.HANDLE_UNAVAILABLE, "Handle $handle does not have any available suffixes")
    }
  }

  override suspend fun getGrantedSchemasByMsaId(delegatorMsaId: BigInteger, providerMsaId: BigInteger): List<Int> {
    val schemaGrantResponse = frequencyClient.getGrantedSchemasByMsaId(delegatorMsaId, providerMsaId).await().map {
      it.schemaId
    }
    return schemaGrantResponse
  }

  override suspend fun getMsaIdByPublicKeyHex(publicKeyHex: String) : BigInteger {
    val publicKey = fromHex(publicKeyHex)
    return frequencyClient.getMsaIdByAccountId(publicKey).await() ?: throw ApiException(
      ApiError.NO_MSA_ID_FOUND_ERROR,
      "No Msa Id found for public key $publicKeyHex"
    )
  }

  override suspend fun getMsaIdByPublicKey(publicKey: ByteArray): BigInteger? {
    return frequencyClient.getMsaIdByAccountId(publicKey).await()
  }

  override suspend fun getMsaIdByUserIdentifier(userIdentifier: UserIdentifier): BigInteger {
    val userAccount = findUserAccountByUserIdentifier(userIdentifier) ?: throw ApiException(
      ApiError.NO_USER_ACCOUNT_ID_FOUND,
      "No user account found for UserIdentifier: $userIdentifier",
      mapOf("userIdentifier" to userIdentifier)
    )
    val userAccountId = userAccount.id!!
    val userKeyData =
      findUserKeyData(userAccountId, KeyUsageType.ACCOUNT, KeyPairType.SR25519) ?: throw ApiException(
        ApiError.NO_USER_KEY_DATA_FOUND,
        "No UserKeyData found for userAccountId: $userAccountId",
        mapOf("userAccountId" to userAccountId)
      )
    return getMsaIdByPublicKeyHex(userKeyData.publicKeyHex)
  }

  override suspend fun retrieveMsaId(publicKeyFormat: PublicKeyFormat, encodedPublicKey: String): BigInteger {
    when(publicKeyFormat) {
      PublicKeyFormat.SS58 -> {
        val publicKeyBytes = base58DecodeAndExtractPublicKey(encodedPublicKey)
        return frequencyClient.getMsaIdByAccountId(publicKeyBytes).await()
          ?: throw ApiException(ApiError.NO_MSA_ID_FOUND_ERROR, "PublicKey not found for SS58=${encodedPublicKey}")
      }
      else -> {
        throw IllegalArgumentException("Public key format ${publicKeyFormat.name} isn't supported")
      }
    }
  }

  override suspend fun retrieveMsaId(publicKey: PublicKeyDto): BigInteger {
    when (publicKey.encoding) {
      Encoding.BASE_58 -> return retrieveMsaId(publicKey.format, publicKey.encodedValue)
      //NOTE(9/6/25, Aziz): Didn't think it made sense to boil the ocean and change the retrieveMsaID method above
      //so just calling the client directly here if a key given is SECP256K1
      Encoding.HEX -> when (publicKey.type) {
        KeyPairType.SECP256K1 -> {
          val publicKeyBytes = publicKeyToUniversalAddress(fromHex(publicKey.encodedValue), publicKey.type)
          return frequencyClient.getMsaIdByAccountId(publicKeyBytes).await()
            ?: throw ApiException(ApiError.NO_MSA_ID_FOUND_ERROR, "PublicKey not found for BARE=${publicKey.encodedValue}")
        }

        else -> throw IllegalArgumentException("Public key type ${publicKey.type} isn't supported")
      }
      else -> {
        throw IllegalArgumentException("Public key encoding ${publicKey.encoding.encoding} isn't supported")
      }
    }
  }

  override suspend fun getProviderName(msaId: BigInteger): String {
    return getProviderRegistryEntryV2(msaId).defaultName
  }

  override suspend fun getProviderRegistryEntryV2(msaId: BigInteger): CommonPrimitivesProviderRegistryEntry {
    val providerRegistryEntry = frequencyClient.getProviderToRegistryEntryV2(msaId).await()
    if (providerRegistryEntry == null) {
      throw ApiException(
        ApiError.PROVIDER_NOT_FOUND,
        "No provider is registered on chain with msaId/providerId=${msaId}"
      )
    } else {
      return providerRegistryEntry
    }
  }

  override suspend fun validateMsa(providerMsaId: BigInteger, providerPublicKeyDto: PublicKeyDto) {
    val msaId = retrieveMsaId(providerPublicKeyDto.format, providerPublicKeyDto.encodedValue)
    if (msaId != providerMsaId) {
      throw ApiException(
        ApiError.MSA_ID_MISMATCH_ERROR,
        "The specified msaId ($providerMsaId) does not match the msaId registered for the specified keypair ($msaId)"
      )
    }

    verifyWhitelistedProviderMsaId(msaId)
  }

  override suspend fun findSignUpRequestBySessionId(sessionId: String): SignUpRequest {
    return redisClient.findSignUpRequestBySessionId(sessionId)
      ?: throw ApiException(ApiError.NO_SIGNUP_REQUEST_FOR_SESSION_ERROR, "No SignUpRequest data was found for sessionId=${sessionId}")
  }

  override suspend fun findLoginRequestBySessionId(sessionId: String): LoginRequest {
    return redisClient.findLoginRequestBySessionId(sessionId)
      ?: throw ApiException(ApiError.NO_LOGIN_REQUEST_FOR_SESSION_ERROR, "No LoginRequest data was found for sessionId=${sessionId}")
  }

  override suspend fun findSessionInfoBySessionId(sessionId: String): SessionInfo {
    val sessionInfo = redisClient.findSessionInfoBySessionId(sessionId)
    if(sessionInfo != null){
      return sessionInfo
    }else{
      throw ApiException(ApiError.NO_SESSION_INFO_FOUND_ERROR, "No SessionInfo was found for sessionId=$sessionId")
    }
  }

  override suspend fun findWebsiteSessionBySessionId(sessionId: String): WebsiteSession {
    return redisClient.findWebsiteSessionBySessionId(sessionId) ?: throw ApiException(ApiError.NO_WEBSITE_SESSION_FOUND_ERROR, "No WebsiteSession found for sessionId=${sessionId}")
  }

  override suspend fun findWebsiteSessionBySessionIdAndAuthorizationCode(sessionId: String, authorizationCode: String): WebsiteSession {
    return redisClient.findWebsiteSessionBySessionIdAndAuthorizationCode(sessionId, authorizationCode)
      ?: throw ApiException(
        ApiError.NO_WEBSITE_SESSION_FOR_TOKEN_ERROR,
        "No Website Session found for this session ID $sessionId and/or authorizationCode $authorizationCode"
      )
  }

  override suspend fun findWebsiteSessionBySessionIdAndVerificationCode(sessionId: String, verificationCode: String): WebsiteSession {
    return redisClient.findWebsiteSessionBySessionIdAndVerificationCode(sessionId, verificationCode)
      ?: throw ApiException(
        ApiError.NO_WEBSITE_SESSION_FOR_TOKEN_ERROR,
        "No Website Session found for this session ID $sessionId and verificationCode $verificationCode"
      )
  }

  override suspend fun findBatchPayloadToSignRequestBySessionIdAndAuthenticationCode(sessionId: String, authenticationCode: String): BatchPayloadToSignRequest {
    return redisClient.findBatchPayloadToSignRequestBySessionIdAndAuthenticationCode(sessionId, authenticationCode)
      ?: throw ApiException(
        ApiError.NO_PAYLOAD_FOUND_ERROR,
        "No Batch payload to sign was found for authentication code=$authenticationCode sessionId=$sessionId"
      )
  }

  override suspend fun findBatchPayloadToSignRequestBySessionIdAndAuthorizationCode(sessionId: String, authorizationCode: String): BatchPayloadToSignRequest {
    return redisClient.findBatchPayloadToSignRequestBySessionIdAndAuthorizationCode(sessionId, authorizationCode)
      ?: throw ApiException(
        ApiError.NO_PAYLOAD_FOUND_ERROR,
        "No Batch payload to sign was found for authorization code=$authorizationCode sessionId=$sessionId"
      )
  }

  override fun verifyWhitelistedApplicationUrl(url: String) {
    val uri = URI(url)
    when {
      properties.providerWhitelistAllowLocalhost && (uri.hostIsLocalhost || uri.hostIsLoopbackAddress) -> return
      uri.origin == properties.applicationOrigin -> return
      else -> throw ApiException(ApiError.URL_NOT_ON_WHITELIST_ERROR, "The following url is not whitelisted: $url")
    }
  }

  private fun isWhitelistedProviderUrl(url: URI, providerMetadata: ProviderMetadata): Boolean {
    if (properties.providerWhitelistAllowLocalhost && (url.hostIsLocalhost || url.hostIsLoopbackAddress)) {
      return true
    }

    // Check if any of the whitelisted origin 'descriptors' configured for the provider apply to the given URL
    return providerMetadata.whitelistedOrigins.map { it.matches(url) }.any()
  }

  @Deprecated(
    "This method is that aware of the 'Provider Application' abstraction (see #1142)",
    replaceWith = ReplaceWith("verifyProviderApplicationIsWhitelisted(providerMsaId, applicationVerifiedCredentialUrl)")
  )
  override suspend fun verifyWhitelistedProviderMsaId(providerMsaId: BigInteger) {
    val providerIsRegistered = providerMetadataService.resolveProviderMetadata(providerMsaId) != null
    if (!providerIsRegistered) {
      throw ApiException(
        ApiError.MSA_ID_NOT_ON_WHITELIST_ERROR,
        "The following msa id is not whitelisted: $providerMsaId"
      )
    }
  }

  @Deprecated(
    "This method is that aware of the 'Provider Application' abstraction (see #1142)",
    replaceWith = ReplaceWith("getProviderMetaDataForApplication(providerMsaId, applicationVerifiedCredentialUrl)")
  )
  override suspend fun getProviderMetaData(providerMsaId: BigInteger): ProviderMetadata? {
    return providerMetadataService.resolveProviderMetadata(providerMsaId)
  }

  @Deprecated(
    "This method is that aware of the 'Provider Application' abstraction (see #1142)",
    replaceWith = ReplaceWith("getProviderMetaDataForApplication(providerMsaId, applicationVerifiedCredentialUrl)")
  )
  override suspend fun getProviderMetaDataOrThrow(providerMsaId: BigInteger): ProviderMetadata {
    return getProviderMetaData(providerMsaId) ?:
      throw ApiException(ApiError.PROVIDER_METADATA_NOT_FOUND, "Provider metadata not found for msaId=$providerMsaId")
  }

  override suspend fun getProviderMetadataForApplication(
    providerMsaId: BigInteger,
    applicationVerifiedCredentialUrl: URI
  ): ProviderMetadata? {
    return providerMetadataService.resolveProviderMetadataForApplication(
      providerMsaId, applicationVerifiedCredentialUrl
    )
  }

  override fun verifyUrlWhitelistedByProviderMetadata(url: URI, providerMetadata: ProviderMetadata) {
    if (!isWhitelistedProviderUrl(url, providerMetadata)) {
      throw ApiException(ApiError.URL_NOT_ON_WHITELIST_ERROR, "The following url is not whitelisted: $url")
    }
  }

  override suspend fun getDecryptedPrivateKey(userKeyData: UserKeyData): ByteArray {
    val encryptedPrivateKey = fromHex(userKeyData.encryptedPrivateKeyHex)
    val kmsDecryptionKey = KmsDecryptionKey(userKeyData.kmsEncryptionKeyId, userKeyData.kmsEncryptionKeyIdType)
    return kmsClient.decryptPrivateKey(EncryptedKey(encryptedPrivateKey, kmsDecryptionKey))
  }

  override suspend fun getDecryptedAccountKeyPair(userKeyData: UserKeyData): AccountKeyPair {
    if (userKeyData.keyUsageType != KeyUsageType.ACCOUNT) {
      throw IllegalArgumentException("Invalid KeyUsageType: $userKeyData (expected ACCOUNT)")
    }

    val publicKeyBytes = fromHex(userKeyData.publicKeyHex).toPublicKeyBytes()
    val privateKeyBytes = getDecryptedPrivateKey(userKeyData).toPrivateKeyBytes()
    val cryptoProvider = when (userKeyData.encryptedPrivateKeyType) {
      KeyPairType.SR25519 -> Sr25519CryptoProvider
      KeyPairType.SECP256K1 -> Secp256K1CryptoProvider
      else -> throw UnsupportedOperationException(
        "Invalid KeyPairType for an account key usage: ${userKeyData.encryptedPrivateKeyType}"
      )
    }

    return KeyPair(publicKeyBytes, privateKeyBytes, cryptoProvider)
  }

  override fun getIdentifierOfTypeOrThrow(sessionId: String, userIdentifiers: List<UserIdentifier>, type: UserIdentifierType): UserIdentifier {
    return userIdentifiers.firstOrNull { it.type == type }
      ?: throw ApiException(
        ApiError.NULL_REQUIRED_FIELD_ERROR,
        "No User Identifier of type: ${type.name} found in request with sessionId=$sessionId"
      )
  }

  override suspend fun getVerifiedMillisForPhone(userIdentifier: UserIdentifier): BigInteger? {
    return databaseService.findUserIdentifier(UserDetail(userIdentifier.value, UserDetailType.PHONE_NUMBER))?.verifiedDate
  }

  override suspend fun findUserAccountByUserIdentifier(userIdentifier: UserIdentifier): UserAccount? {
    return databaseService.findUserAccountByUserIdentifier(mapUserIdentifierToUserDetail(userIdentifier))
  }

  override suspend fun getGrantedSchemasByPublicKey(userPublicKey: ByteArray, providerMsaId: BigInteger): List<Int> {
    val userMsaId = frequencyClient.getMsaIdByAccountId(userPublicKey).await()

    return when (userMsaId) {
      null -> emptyList()
      else -> getGrantedSchemasByMsaId(userMsaId, providerMsaId)
    }
  }

  override suspend fun getGraphKeysRegisteredOnChainForUser(msaId: BigInteger, loggingContext: Map<String, Any>): List<ByteArray> {
    val response = frequencyClient.getItemizedStorage(msaId, graphHelper.getGraphKeySchemaId()).await()
    return response.items.map { result ->
      val keyBytes = fromHex(result.payload)
      when {
        keyBytes.size <= 32 -> {
          // NOTE(Julian, 2024-11-14): Some keys got into the chain in the bare format--in the event the key appears
          // to be bare go ahead and parse it as such (see issue #991).
          LOG.warn("User has bare graph key: ${result.payload} {}", StructuredArguments.entries(loggingContext))
          keyBytes
        }
        else -> {
          // The key is stored on chain in the 'DSNP' format and needs to be deserialized to get the public key bytes.
          GraphHelper.deserializeDsnpKey(keyBytes)
        }
      }
    }
  }

  private fun mapUserIdentifierToUserDetail(userIdentifier: UserIdentifier): UserDetail {
    return UserDetail(userIdentifier.value,UserDetailType.valueOf(userIdentifier.type.name))
  }

  override suspend fun findSiwaSessionOrThrow(sessionId: String): SiwaSession {
    return findSiwaSession(sessionId) ?: throw ApiException(
      ApiError.SIWA_SESSION_NOT_FOUND,
      "No SIWA session found for the given session ID",
      mapOf("sessionId" to sessionId)
    )
  }

  override suspend fun findSiwaSession(
    sessionId: String
  ): SiwaSession? {
    val siwaSession = redisClient.findSiwaSessionBySessionId(sessionId)
    return siwaSession?.fold({ it }, {
      LOG.info("AuthenticatedSession found, continuing with that session sessionId=$sessionId")
      it
    })
  }

  override suspend fun findAuthenticatedSiwaSessionOrThrow(
    sessionId: String
  ): AuthenticatedSiwaSession {
    when (val siwaSession = redisClient.findSiwaSessionBySessionId(sessionId)) {
      is AuthenticatedSiwaSession -> return siwaSession

      else -> throw ApiException(
        ApiError.SIWA_SESSION_NOT_FOUND,
        "No SIWA session found for the given session ID",
        mapOf(LoggingAttributes.SESSION_ID to sessionId),
      )
    }
  }

  override suspend fun findWebsiteOrSiwaAuthenticatedUserDataOrThrow(sessionId: String): AuthenticatedUserData {
    redisClient.findWebsiteSessionBySessionId(sessionId)?.let { websiteSession ->
      if (websiteSession.loggedIn == UserState.LOGGED_IN) {
        val userAccountId = websiteSession.userAccountIds?.firstOrNull()
          ?: throw ApiException(
            ApiError.NO_USER_ACCOUNT_ID_FOUND,
            "'userAccountIds' empty for logged in WebsiteSession",
            mapOf(LoggingAttributes.SESSION_ID to sessionId)
          )

        return AuthenticatedUserData(userAccountId)
      }
    }

    redisClient.findSiwaSessionBySessionId(sessionId)?.let { siwaSession ->
      if (siwaSession is AuthenticatedSiwaSession) {
        val userAccountId = siwaSession.userAccountId
          ?: throw ApiException(
            ApiError.NO_USER_ACCOUNT_ID_FOUND,
            "'userAccountId' is null for AuthenticatedSiwaSession",
            mapOf(LoggingAttributes.SESSION_ID to sessionId)
          )
        return AuthenticatedUserData(userAccountId)
      }
    }

    throw ApiException(
      ApiError.NO_SESSION_INFO_FOUND_ERROR,
      "No logged in (i.e., authenticated) session--either Website or SIWA--found for the given sessionId",
      mapOf(LoggingAttributes.SESSION_ID to sessionId)
    )
  }

  override suspend fun getLatestSchemaIdForIntent(intentName: String): Int {
    return frequencyClient.getLatestSchemaIdByIntentName(intentName).await().fold(
      { throw IllegalStateException("Unable to get the latest schema ID for intent '$intentName'") },
      { it.value }
    )
  }

  override suspend fun getUniversalAddressesByMsaId(msaId: BigInteger): List<ByteArray> {
    return frequencyClient.getUniversalAddressesByMsaId(msaId).await().fold(
      { exception ->
        throw ApiException(
          ApiError.NO_PUBLIC_KEY_FOUND,
          "Failed to query the blockchain index for addresses associated with msaId=$msaId",
          exception
        )
      },
      { it }
    )
  }

  override suspend fun getUserAccountIdByMsaIdOrThrow(msaId: BigInteger): BigInteger {
    val addresses = getUniversalAddressesByMsaId(msaId)

    val matchingUserKeyDataRows = databaseService.findUserKeyDataByKeyPairTypeAndPublicKeys(
      KeyPairType.SR25519,
      addresses.map { toHex(it) }
    )

    val userAccountIds = matchingUserKeyDataRows.map { it.userAccountId }
    val unanimousAccountId = userAccountIds.reduceOrNull { acc, current ->
      if (current != acc) {
        throw ApiException(
          ApiError.MULTIPLE_IDENTITIES_FOUND,
          "Found multiple userAccountIds for MSA ID: $current, $acc",
          mapOf("msaId" to msaId)
        )
      }

      acc
    }

    return unanimousAccountId ?: throw ApiException(
      ApiError.NO_USER_ACCOUNT_ID_FOUND,
      "Could not find a userAccountId for msaId=$msaId",
      mapOf("msaId" to msaId)
    )
  }
}