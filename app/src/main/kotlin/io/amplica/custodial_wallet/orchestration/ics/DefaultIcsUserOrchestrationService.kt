package io.amplica.custodial_wallet.orchestration.ics

import com.strategyobject.substrateclient.crypto.ss58.SS58AddressFormat
import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.client.kms.EncryptedData
import io.amplica.custodial_wallet.client.kms.KmsDecryptionKey
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.db.repository.*
import io.amplica.custodial_wallet.db.spring.DelegatingTransactionalOperator
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.frequency.FrequencyIntent
import io.amplica.custodial_wallet.orchestration.LookupOrchestrationService
import io.amplica.custodial_wallet.orchestration.payload.IcsContextItemKeyRequest
import io.amplica.custodial_wallet.orchestration.payload.IcsMsaIdRequest
import io.amplica.custodial_wallet.orchestration.signing.SigningOrchestrationService
import io.amplica.custodial_wallet.service.ics.*
import io.amplica.custodial_wallet.service.ics_whitelist.IcsWhitelistService
import io.amplica.custodial_wallet.service.key.KeyService
import io.amplica.custodial_wallet.type.IdentifiedValue
import io.amplica.custodial_wallet.util.decodeValueToBytes
import io.amplica.custodial_wallet.util.fromHex
import io.amplica.custodial_wallet.util.key_creation.*
import io.amplica.custodial_wallet.util.toHex
import io.amplica.frequency.payload.ItemizedSignaturePayloadV2
import io.amplica.frequency.serialization.FrequencySerializable
import java.math.BigInteger
import java.util.*

class DefaultIcsUserOrchestrationService(
  private val properties: DefaultIcsUserOrchestrationProperties,
  private val lookupService: LookupOrchestrationService,
  private val signingOrchestrationService: SigningOrchestrationService,
  private val keyService: KeyService,
  private val icsService: IcsService,
  private val sS58AddressFormat: SS58AddressFormat,
  private val icsWhitelistService: IcsWhitelistService,
  private val databaseService: CustodialWalletDatabaseService,
  private val transactionalOperator: DelegatingTransactionalOperator,
) : IcsUserOrchestrationService {
  companion object {
    const val CONTEXT_ITEM_KEY_DERIVATION_PATH_PREFIX_TEMPLATE = "off-chain|context-item|%s"
  }

  private suspend fun createSignedPublicKeyRegistrationPayloadResponse(
    publicKey: IcsKeyPair,
    controlKeyPairBytes: KeyPairBytes,
    expirationBlockNumber: Long,
  ): TypedPayloadResponseWithSignature<ItemizedSignaturePayloadResponse> {
    val schemaId = lookupService.getLatestSchemaIdForIntent(FrequencyIntent.ICS_PUBLIC_KEY)

    require(publicKey.type == IcsKeyType.ED25519) {
      "`serializePublicKey` only supports Ed25519 public keys"
    }
    val payload = icsService.serializePublicKey(publicKey.publicKey)

    //payload objects
    val registerKeyItemAction = AddItemAction(toHex(payload))
    val registerKeyItemizedSignaturePayloadResponse = ItemizedSignaturePayloadResponse(
      schemaId,
      0,
      expirationBlockNumber,
      listOf<ItemAction>(registerKeyItemAction)
    )

    //signature objects
    val registerKeyScaleItemAction = io.amplica.frequency.payload.AddItemAction(
      decodeValueToBytes(
        registerKeyItemAction.payloadHex,
        Encoding.HEX
      )
    )
    val registerKeyItemizedSignaturePayloadV2 = ItemizedSignaturePayloadV2(
      registerKeyItemizedSignaturePayloadResponse.schemaId,
      registerKeyItemizedSignaturePayloadResponse.targetHash.toBigInteger(),
      registerKeyItemizedSignaturePayloadResponse.expiration,
      listOf(registerKeyScaleItemAction)
    )
    val registerKeyItemizedSignaturePayloadResponseSignature = signingOrchestrationService.signPayload(
      controlKeyPairBytes,
      registerKeyItemizedSignaturePayloadV2,
    )

    //payload signed object
    return TypedPayloadResponseWithSignature(
      registerKeyItemizedSignaturePayloadResponseSignature,
      FrequencyEndpoint(Pallet.StatefulStorage, Extrinsic.ApplyItemActionsWithSignatureV2),
      DebugDescription.HCP_PUBLIC_KEY_PAYLOAD,
      PayloadType.ITEM_ACTIONS,
      registerKeyItemizedSignaturePayloadResponse
    )
  }

  private suspend fun createSignedAclPayloadResponse(
    acl: ContextGroupAcl,
    controlKeyPairBytes: KeyPairBytes,
    expirationBlockNumber: Long,
  ): TypedPayloadResponseWithSignature<ItemizedSignaturePayloadResponse> {
    val schemaId = lookupService.getLatestSchemaIdForIntent(FrequencyIntent.ICS_CONTEXT_GROUP_ACL)

    //payload objects
    val payloadBytes = icsService.serializeContextGroupAcl(acl)
    val aclAddItemAction = AddItemAction(toHex(payloadBytes))
    val aclAddItemActionSignaturePayloadResponse = ItemizedSignaturePayloadResponse(
      schemaId,
      0,
      expirationBlockNumber,
      listOf<ItemAction>(aclAddItemAction),
    )

    //signature objects
    val aclScaleItemAction = io.amplica.frequency.payload.AddItemAction(
      decodeValueToBytes(
        aclAddItemAction.payloadHex,
        Encoding.HEX,
      )
    )
    val aclItemizedSignaturePayloadV2 = ItemizedSignaturePayloadV2(
      aclAddItemActionSignaturePayloadResponse.schemaId,
      aclAddItemActionSignaturePayloadResponse.targetHash.toBigInteger(),
      aclAddItemActionSignaturePayloadResponse.expiration,
      listOf(aclScaleItemAction),
    )
    val aclItemizedSignaturePayloadResponseSignature = signingOrchestrationService.signPayload(
      controlKeyPairBytes,
      aclItemizedSignaturePayloadV2,
    )

    //payload signed object
    return TypedPayloadResponseWithSignature(
      aclItemizedSignaturePayloadResponseSignature,
      FrequencyEndpoint(Pallet.StatefulStorage, Extrinsic.ApplyItemActionsWithSignatureV2),
      DebugDescription.HCP_ACL_PAYLOAD,
      PayloadType.ITEM_ACTIONS,
      aclAddItemActionSignaturePayloadResponse,
    )
  }

  fun assertProviderMsaIdWhitelistedOrThrow(providerMsaId: BigInteger) {
    if (!icsWhitelistService.providerIsWhitelisted(providerMsaId)) {
      throw ApiException(
        ApiError.MSA_ID_NOT_ON_WHITELIST_ERROR,
        "The provider (msaID=$providerMsaId) is not authorized to use any ICS endpoints"
      )
    }
  }

  private suspend fun generateAndPersistMasterSeed(userAccountId: BigInteger, usageType: SeedUsageType): IdentifiedValue<BigInteger, ByteArray> {
    // Generate a random mnemonic phrase
    val userSeedPhrase = icsService.generateMasterMnemonicSeedPhrase()
    val userSeed = icsService.deriveMasterSeed(userSeedPhrase)

    val encryptedSeedPhrase = keyService.encryptData(userSeedPhrase.encodeToByteArray())
    val encryptedSeed = keyService.encryptData(userSeed)

    if (encryptedSeedPhrase.kmsDecryptionKey != encryptedSeed.kmsDecryptionKey) {
      throw IllegalStateException("The KeyService returned incompatible encrypted seed and key decryption values")
    }
    val decryptionKey = encryptedSeedPhrase.kmsDecryptionKey

    // Persist the mnemonic phrase and seed
    val encryptedSeedPhraseHex = toHex(encryptedSeedPhrase.value)
    val encryptedSeedHex = toHex(encryptedSeed.value)
    val usd = UserSeedData.create(
      userAccountId,
      usageType,
      encryptedSeedPhraseHex,
      encryptedSeedHex,
      decryptionKey.decryptionKeyId,
      decryptionKey.decryptionAlgorithm
    )

    val savedUserSeedData = databaseService.saveUserSeedData(usd)

    return IdentifiedValue(savedUserSeedData.id!!, userSeed)
  }

  private suspend fun deriveAndPersistIcsMasterKeyPair(
    userAccountId: BigInteger,
    masterSeed: IdentifiedValue<BigInteger, ByteArray>,
  ): IcsKeyPair {
    // Derive an ICS keypair
    val userIcsKeyPair = icsService.deriveMasterKeyPair(masterSeed.data)
    val keyType = when (userIcsKeyPair.type) {
      IcsKeyType.ED25519 -> KeyPairType.ED25519
    }

    val encryptedIcsPrivateKey = keyService.encryptPrivateKey(userIcsKeyPair.privateKey)

    // Persist the ICS keypair
    val ukd = UserKeyData.create(
      userAccountId,
      userIcsKeyPair.publicKey,
      encryptedIcsPrivateKey,
      keyType,
      KeyUsageType.ICS,
      masterSeed.id
    )
    databaseService.saveUserKeyData(ukd)

    return userIcsKeyPair
  }

  private suspend fun deriveAndPersistIcsOnChainDataKey(masterSeed: IdentifiedValue<BigInteger, ByteArray>): Derived<ByteArray> {
    // Derive an ICS keypair
    val derivedOnChainSymmetricKey = icsService.deriveUserChainDataSymmetricKey(masterSeed.data)
    val encryptedOnChainSymmetricKey = keyService.encryptPrivateKey(derivedOnChainSymmetricKey.value)

    // Persist the ICS keypair
    val udkd = UserDerivedKeyData.create(
      masterSeed.id,
      derivedOnChainSymmetricKey.path,
      DerivedKeyUsageType.ON_CHAIN,
      toHex(encryptedOnChainSymmetricKey.encryptedValue),
      encryptedOnChainSymmetricKey.kmsDecryptionKey.decryptionKeyId,
      encryptedOnChainSymmetricKey.kmsDecryptionKey.decryptionAlgorithm
    )
    databaseService.saveUserDerivedKeyData(udkd)

    return derivedOnChainSymmetricKey
  }

  private suspend fun deriveAndPersistIcsContextGroupKey(
    masterSeed: IdentifiedValue<BigInteger, ByteArray>,
    contextGroupId: ByteArray,
  ): Derived<ByteArray> {
    // Derive a Context Group keypair
    val derivedContextGroupSymmetricKey = icsService.deriveContextGroupSymmetricKey(masterSeed.data, contextGroupId)
    val encryptedKey = keyService.encryptPrivateKey(derivedContextGroupSymmetricKey.value)

    // Persist the Context Group keypair
    val udkd = UserDerivedKeyData.create(
      masterSeed.id,
      derivedContextGroupSymmetricKey.path,
      DerivedKeyUsageType.CONTEXT_GROUP,
      toHex(encryptedKey.encryptedValue),
      encryptedKey.kmsDecryptionKey.decryptionKeyId,
      encryptedKey.kmsDecryptionKey.decryptionAlgorithm
    )
    databaseService.saveUserDerivedKeyData(udkd)

    return derivedContextGroupSymmetricKey
  }

  private suspend fun deriveAndPersistIcsContextItemKey(
    masterSeed: IdentifiedValue<BigInteger, ByteArray>,
    contextItemId: String,
    contextItemTag: String,
  ): Derived<ByteArray> {
    val derivedContextItemSymmetricKey = icsService.deriveContextItemSymmetricKey(
      masterSeed.data,
      contextItemId,
      contextItemTag,
    )
    val encryptedKey = keyService.encryptPrivateKey(derivedContextItemSymmetricKey.value)

    val udkd = UserDerivedKeyData.create(
      masterSeed.id,
      derivedContextItemSymmetricKey.path,
      DerivedKeyUsageType.CONTEXT_ITEM,
      toHex(encryptedKey.encryptedValue),
      encryptedKey.kmsDecryptionKey.decryptionKeyId,
      encryptedKey.kmsDecryptionKey.decryptionAlgorithm
    )
    databaseService.saveUserDerivedKeyData(udkd)

    return derivedContextItemSymmetricKey
  }

  suspend fun determineIcsPublicKeyState(userMsaId: BigInteger, currentIcsPublicKey: IcsPublicKey): IcsPublicKeyState {
    // Check what ICS keys--if any--the user has registered on chain
    val registeredIcsPublicKeys = icsService.getIcsPublicKeys(userMsaId)
    val matchingPublicKey = registeredIcsPublicKeys.firstOrNull {
      it.value.publicKey.contentEquals(currentIcsPublicKey.publicKey)
    }

    return when (matchingPublicKey) {
      // The existing keypair has not yet been registered on chain
      null -> {
        // Make an (educated) guess about what the keyId will be
        val maxKeyId = registeredIcsPublicKeys.maxByOrNull { it.index }?.index
        val nextKeyId = maxKeyId?.plus(1) ?: 0

        IcsPublicKeyState.Unregistered(nextKeyId)
      }

      // The existing keypair is already registered on chain and we know the keyId
      else -> IcsPublicKeyState.Registered(matchingPublicKey.index)
    }
  }

  private fun icsKeyPairToPublicKeyDto(keyPair: IcsKeyPair): PublicKeyDto {
    val keyType = when (keyPair.type) {
      IcsKeyType.ED25519 -> KeyPairType.ED25519
    }
    return PublicKeyDto(
      toHex(keyPair.publicKey),
      Encoding.HEX,
      PublicKeyFormat.BARE,
      keyType
    )
  }

  private fun parseMsaId(msaIdString: String): BigInteger {
    return try {
      msaIdString.toBigInteger()
    } catch (e: Exception) {
      throw ApiException(ApiError.INVALID_REQUEST, "Unable to parse MSA ID as U64: $msaIdString", e)
    }
  }

  private suspend fun decryptUserSeedData(userSeedData: UserSeedData): ByteArray {
    val encryptedSeed = EncryptedData(
      fromHex(userSeedData.encryptedSeedHex),
      KmsDecryptionKey(
        userSeedData.kmsEncryptionKeyId,
        userSeedData.kmsEncryptionAlgorithm
      ),
    )

    return keyService.decryptData(encryptedSeed)
  }

  private suspend fun decryptUserDerivedKeyData(userDerivedKeyData: UserDerivedKeyData): Derived<ByteArray> {
    val key = keyService.decryptData(
      EncryptedData(
        fromHex(userDerivedKeyData.encryptedKeyHex),
        KmsDecryptionKey(
          userDerivedKeyData.kmsEncryptionKeyId,
          userDerivedKeyData.kmsEncryptionAlgorithm
        ),
      )
    )

    return Derived(
      key,
      userDerivedKeyData.derivationPath
    )
  }

  private fun assertSignatureValidOrThrow(
    payload: FrequencySerializable<Any>,
    publicKeyDto: PublicKeyDto,
    signature: Signature,
  ) {
    val signatureIsValid = signingOrchestrationService.verifySignedPayload(
      publicKeyDto,
      payload,
      signature,
    )

    if (!signatureIsValid) {
      throw ApiException(
        ApiError.INVALID_SIGNATURE,
        "The provided signature does not match the public key and payload",
        mapOf(
          "publicKey" to publicKeyDto.encodedValue,
          "signature" to signature.encodedValue,
        )
      )
    }
  }

  override suspend fun retrieveUserPayloads(request: IcsRetrievePayloadsSignedRequest): IcsRetrievePayloadsResponse {
    val userMsaId = parseMsaId(request.payload.msaId)
    val providerMsaId = lookupService.retrieveMsaId(request.publicKey)

    assertProviderMsaIdWhitelistedOrThrow(providerMsaId)
    assertSignatureValidOrThrow(
      IcsMsaIdRequest(userMsaId, request.payload.nonce),
      request.publicKey,
      request.signature,
    )

    val (_, providerPublicIcsExchangeKey) = icsService.getLatestIcsPublicKey(providerMsaId) ?: throw ApiException(
      ApiError.NO_PUBLIC_KEY_FOUND,
      "Could not find an ICS public key for provider (msaID=$providerMsaId)",
    )

    val userAccountId = lookupService.getUserAccountIdByMsaIdOrThrow(userMsaId)

    // Fetch the user's account and control keypair for signing payloads
    val ukd = lookupService.findUserKeyDataOrThrow(
      userAccountId,
      KeyUsageType.ACCOUNT,
      KeyPairType.SR25519,
    )
    val controlKeyPairBytes = keyService.decryptUserAccountKeyData(ukd)

    // Ensure the user has an ICS user master seed
    val masterSeed = transactionalOperator.executeReadWrite {
      val existingMasterUserSeedData = databaseService.findMostRecentUserSeedDataByUserAccountIdAndSeedUsageType(
        userAccountId,
        SeedUsageType.HCP_MASTER,
      )

      when (existingMasterUserSeedData) {
        null -> generateAndPersistMasterSeed(userAccountId, SeedUsageType.HCP_MASTER)
        else -> {
          val seed = decryptUserSeedData(existingMasterUserSeedData)

          IdentifiedValue(existingMasterUserSeedData.id!!, seed)
        }
      }
    }

    // Check if the user has a *context item* master seed
    transactionalOperator.executeReadWrite {
      val existingContextItemUserSeedData = databaseService.findMostRecentUserSeedDataByUserAccountIdAndSeedUsageType(
        userAccountId,
        SeedUsageType.CONTEXT_ITEM_MASTER,
      )

      // Generate a master context item seed eagerly so that only one endpoint is inserting `UserSeedData` rows
      if (existingContextItemUserSeedData == null) {
        generateAndPersistMasterSeed(userAccountId, SeedUsageType.CONTEXT_ITEM_MASTER)
      }
    }

    val existingUserIcsKeyData = databaseService.findMostRecentUserKeyDataByUserSeedDataId(masterSeed.id)
    val userIcsKeyPair = when {
      // If the user already has an ICS keypair we use that
      existingUserIcsKeyData != null -> {
        val icsKeyPairBytes = keyService.decryptUserIcsKeyData(existingUserIcsKeyData)
        val keyPairType = when (val type = icsKeyPairBytes.keyPairType) {
          KeyPairType.ED25519 -> IcsKeyType.ED25519
          else -> throw AssertionError("Unsupported key pair for for ICS: $type")
        }
        IcsKeyPair(icsKeyPairBytes.publicKeyBytes, icsKeyPairBytes.privateKeyBytes, keyPairType)
      }
      // Otherwise, derive and store a key pair
      else -> deriveAndPersistIcsMasterKeyPair(userAccountId, masterSeed)
    }

    // Compare the user's ICS keypair against the chain to determine if it has been registered and what it's 'key ID' is
    val icsPublicKeyState = determineIcsPublicKeyState(userMsaId, userIcsKeyPair.toPublicKey())

    // Create a signed payload to register the user's ICS public if it has not already been registered on the chain
    val expirationBlockNumber = lookupService.retrieveCurrentBlockNumber() + properties.signupBlockExpiration
    val publicKeyRegistrationPayload = when (icsPublicKeyState) {
      is IcsPublicKeyState.Registered -> null
      is IcsPublicKeyState.Unregistered -> createSignedPublicKeyRegistrationPayloadResponse(
        userIcsKeyPair,
        controlKeyPairBytes,
        expirationBlockNumber,
      )
    }

    val keyId = when (icsPublicKeyState) {
      is IcsPublicKeyState.Registered -> icsPublicKeyState.keyId
      // NOTE(Julian, 2026-01-28): Assuming the public key registration is successful, the keyId will be the next one
      // available.
      is IcsPublicKeyState.Unregistered -> icsPublicKeyState.nextAvailableKeyId
    }

    val contextGroupId = icsService.deriveContextGroupId(
      userMsaId,
      providerMsaId,
      userIcsKeyPair,
      providerPublicIcsExchangeKey,
    )

    val existingOnChainUserDerivedKeyData =
      databaseService.findMostRecentUserDerivedKeyDataByUserSeedDataIdAndUsageType(
        masterSeed.id,
        DerivedKeyUsageType.ON_CHAIN,
      )

    val userOnChainEncryptionKey = when (existingOnChainUserDerivedKeyData) {
      null -> deriveAndPersistIcsOnChainDataKey(masterSeed)
      else -> decryptUserDerivedKeyData(existingOnChainUserDerivedKeyData)
    }

    // Check to see if this `ContextGroupAcl` has already been registered on chain
    val existingContextGroupAcls = icsService.getIcsContextGroupAcls(userMsaId)
    val contextGroupAclAlreadyRegistered = existingContextGroupAcls.any { indexedAcl ->
      // Ignore the non-deterministic `nonce` and `encryptedProviderId`
      indexedAcl.value.contextGroupId.contentEquals(contextGroupId) && indexedAcl.value.keyId == keyId
    }

    val aclPayload = when {
      contextGroupAclAlreadyRegistered -> null
      else -> {
        val providerMsaIdEncryptionResult = icsService.encryptProviderMsaId(
          userOnChainEncryptionKey.value,
          providerMsaId,
        )
        val acl = ContextGroupAcl(
          contextGroupId,
          keyId,
          providerMsaIdEncryptionResult.nonce,
          providerMsaIdEncryptionResult.data,
        )

        createSignedAclPayloadResponse(acl, controlKeyPairBytes, expirationBlockNumber)
      }
    }

    return IcsRetrievePayloadsResponse(
      controlKeyPairBytes.toPublicKeyDto(sS58AddressFormat),
      icsKeyPairToPublicKeyDto(userIcsKeyPair),
      listOfNotNull(
        publicKeyRegistrationPayload,
        aclPayload,
      )
    )
  }

  /**
   * Fetch the user's ICS seed/keys and validate against the chain that the user has authorized a context group
   * relationship with the provider.
   */
  private suspend fun getAndValidateAuthorizedContextGroup(
    providerMsaId: BigInteger,
    userMsaId: BigInteger,
  ): IcsAuthorizedContextGroup {
    assertProviderMsaIdWhitelistedOrThrow(providerMsaId)

    val (_, providerPublicIcsExchangeKey) = icsService.getLatestIcsPublicKey(providerMsaId) ?: throw ApiException(
      ApiError.NO_PUBLIC_KEY_FOUND,
      "Could not find an ICS public key for provider (msaID=$providerMsaId)",
    )

    val userAccountId = lookupService.getUserAccountIdByMsaIdOrThrow(userMsaId)

    val exceptionContext = mapOf(
      "providerMsaId" to providerMsaId,
      "msaId" to userMsaId,
    )
    val missingKeyMessage = "The user does not have the necessary ICS key pairs (call `hcp/api/user/payloads`)"

    // Ensure the user has an ICS user master seed
    val masterUserSeedData = databaseService.findMostRecentUserSeedDataByUserAccountIdAndSeedUsageType(
      userAccountId,
      SeedUsageType.HCP_MASTER,
    ) ?: throw ApiException(
      ApiError.ICS_INVALID_STATE_ERROR,
      missingKeyMessage,
      exceptionContext,
    )

    // Ensure the user has a context item master seed
    val contextItemMasterUserSeedData = databaseService.findMostRecentUserSeedDataByUserAccountIdAndSeedUsageType(
      userAccountId,
      SeedUsageType.CONTEXT_ITEM_MASTER,
    ) ?: throw ApiException(
      ApiError.ICS_INVALID_STATE_ERROR,
      missingKeyMessage,
      exceptionContext,
    )

    // Get the user's latest ICS keypair controlled by the CW
    val existingUserIcsKeyData = databaseService.findMostRecentUserKeyDataByUserSeedDataId(masterUserSeedData.id!!)
      ?: throw ApiException(
        ApiError.ICS_INVALID_STATE_ERROR,
        missingKeyMessage,
        exceptionContext,
      )
    val icsKeyPairBytes = keyService.decryptUserIcsKeyData(existingUserIcsKeyData)
    val keyPairType = when (val type = icsKeyPairBytes.keyPairType) {
      KeyPairType.ED25519 -> IcsKeyType.ED25519
      else -> throw AssertionError("Unsupported key pair for for ICS: $type")
    }
    val userIcsKeyPair = IcsKeyPair(
      icsKeyPairBytes.publicKeyBytes,
      icsKeyPairBytes.privateKeyBytes,
      keyPairType,
    )

    val contextGroupId = icsService.deriveContextGroupId(
      userMsaId,
      providerMsaId,
      userIcsKeyPair,
      providerPublicIcsExchangeKey
    )

    // Fetch the Context Group ACL from chain
    val existingUserIcsOnChainDerivedKeyData =
      databaseService.findMostRecentUserDerivedKeyDataByUserSeedDataIdAndUsageType(
        masterUserSeedData.id!!,
        DerivedKeyUsageType.ON_CHAIN,
      ) ?: throw ApiException(
        ApiError.ICS_INVALID_STATE_ERROR,
        missingKeyMessage,
        exceptionContext,
      )

    val icsOnChainSymmetricKey = decryptUserDerivedKeyData(existingUserIcsOnChainDerivedKeyData).value

    val validContextGroupAclExists = icsService.getIcsContextGroupAcls(userMsaId).firstOrNull {
      it.value.contextGroupId.contentEquals(contextGroupId)
              && icsService.decryptProviderMsaId(
        icsOnChainSymmetricKey,
        IcsEncryptionResult(it.value.encryptedProviderId, it.value.nonce),
      ) == providerMsaId
    } != null

    if (!validContextGroupAclExists) {
      throw ApiException(
        ApiError.ICS_ACL_CHECK_ERROR,
        "Unable to find a valid Context Group ACL matching the user's and provider's current keys, possibly due " +
                "to key rotation (call `hcp/api/user/payloads`).",
        exceptionContext,
      )
    }

    return IcsAuthorizedContextGroup(
      contextGroupId,
      IdentifiedValue(
        masterUserSeedData.id!!,
        decryptUserSeedData(masterUserSeedData)
      ),
      IdentifiedValue(
        contextItemMasterUserSeedData.id!!,
        decryptUserSeedData(contextItemMasterUserSeedData)
      )
    )
  }

  override suspend fun retrieveContextGroupKey(request: IcsContextGroupKeySignedRequest): IcsContextGroupKeyResponse {
    val userMsaId = parseMsaId(request.payload.msaId)
    val providerMsaId = lookupService.retrieveMsaId(request.publicKey)

    assertProviderMsaIdWhitelistedOrThrow(providerMsaId)
    assertSignatureValidOrThrow(
      IcsMsaIdRequest(userMsaId, request.payload.nonce),
      request.publicKey,
      request.signature,
    )

    val authorizedContextGroup = getAndValidateAuthorizedContextGroup(providerMsaId, userMsaId)

    // Check DB for existing 'Context Group' derived key for the user, otherwise derive and persist one
    val existingContextGroupDerivedKeyData =
      databaseService.findMostRecentUserDerivedKeyDataByUserSeedDataIdAndUsageType(
        authorizedContextGroup.userSeed.id,
        DerivedKeyUsageType.CONTEXT_GROUP,
      )
    val contextGroupSymmetricKey: Derived<ByteArray> = when (existingContextGroupDerivedKeyData) {
      null -> deriveAndPersistIcsContextGroupKey(
        authorizedContextGroup.userSeed,
        authorizedContextGroup.contextGroupId,
      )

      else -> decryptUserDerivedKeyData(existingContextGroupDerivedKeyData)
    }

    return IcsContextGroupKeyResponse(
      IcsSymmetricKey(
        toHex(contextGroupSymmetricKey.value),
        Encoding.HEX,
        contextGroupSymmetricKey.path
      )
    )
  }

  override suspend fun retrieveContextItemKey(request: IcsContextItemKeySignedRequest): IcsContextItemKeyResponse {
    val userMsaId = parseMsaId(request.payload.msaId)
    val providerMsaId = lookupService.retrieveMsaId(request.publicKey)

    assertProviderMsaIdWhitelistedOrThrow(providerMsaId)
    assertSignatureValidOrThrow(
      IcsContextItemKeyRequest(
        userMsaId,
        request.payload.contextItemId,
        request.payload.nonce,
      ),
      request.publicKey,
      request.signature,
    )

    val authorizedContextGroup = getAndValidateAuthorizedContextGroup(providerMsaId, userMsaId)

    return transactionalOperator.executeReadWrite {
      val contextItemId = request.payload.contextItemId
      var contextItemKey: Derived<ByteArray>? = null
      val existingContextItemDerivedKeyData =
        databaseService.findMostRecentUserDerivedKeyDataByDerivationPathPrefixed(
          String.format(CONTEXT_ITEM_KEY_DERIVATION_PATH_PREFIX_TEMPLATE, contextItemId),
        )
      if (properties.canReturnExistingContextItemKey) {
        if (existingContextItemDerivedKeyData != null) {
          contextItemKey = decryptUserDerivedKeyData(existingContextItemDerivedKeyData)
        }
      } else if (existingContextItemDerivedKeyData != null) { //no override and the key already exists
        throw ApiException(
          ApiError.ICS_CONTEXT_ITEM_KEY_RESUBMISSION,
          "Cannot resend a contextItemKey",
          structuredArguments = mapOf(
            "providerMsaId" to providerMsaId,
            "msaId" to userMsaId,
            "contextItemId" to contextItemId,
          ),
        )
      }

      if (contextItemKey == null) {
        val contextItemTag = UUID.randomUUID().toString()
        contextItemKey = deriveAndPersistIcsContextItemKey(
          authorizedContextGroup.contextItemSeed,
          contextItemId,
          contextItemTag,
        )
      }

      IcsContextItemKeyResponse(
        IcsSymmetricKey(
          toHex(contextItemKey.value), //It is correct to explode if this isn't accurate
          Encoding.HEX,
          contextItemKey.path,
        )
      )
    }
  }
}