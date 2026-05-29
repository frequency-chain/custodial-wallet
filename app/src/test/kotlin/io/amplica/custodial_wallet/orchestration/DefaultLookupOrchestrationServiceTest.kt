package io.amplica.custodial_wallet.orchestration

import com.strategyobject.substrateclient.crypto.ss58.SS58AddressFormat
import com.strategyobject.substrateclient.crypto.ss58.SS58Codec
import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.client.kms.EncryptedKey
import io.amplica.custodial_wallet.client.kms.KmsClient
import io.amplica.custodial_wallet.client.kms.KmsDecryptionKey
import io.amplica.custodial_wallet.client.kms.KmsEncryptionAlgorithm
import io.amplica.custodial_wallet.client.redis.CustodialWalletRedisClient
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.db.repository.KeyUsageType
import io.amplica.custodial_wallet.db.repository.UserDetailType
import io.amplica.custodial_wallet.db.repository.UserKeyData
import io.amplica.custodial_wallet.dto.GetHandleResponse
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.orchestration.siwa.EMAIL_IDENTIFIER
import io.amplica.custodial_wallet.orchestration.siwa.SIWA_REQUEST
import io.amplica.custodial_wallet.service.organization.OriginDescriptor
import io.amplica.custodial_wallet.service.provider_metadata.ProviderMetadata
import io.amplica.custodial_wallet.service.provider_metadata.ProviderMetadataService
import io.amplica.custodial_wallet.util.base58DecodeAndExtractPublicKey
import io.amplica.custodial_wallet.util.fromHex
import io.amplica.custodial_wallet.util.key_creation.*
import io.amplica.custodial_wallet.util.toHex
import io.amplica.custodial_wallet.web.LoggingAttributes
import io.amplica.frequency.client.*
import io.amplica.frequency.crypto.provider.Secp256K1CryptoProvider
import io.amplica.frequency.crypto.provider.Sr25519CryptoProvider
import io.amplica.frequency.crypto.provider.X25519CryptoProvider
import io.amplica.frequency.util.GraphHelper
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.CompletableFuture

class DefaultLookupOrchestrationServiceTest {
  private val mockDatabaseService: CustodialWalletDatabaseService = mock()
  private val mockFrequencyClient: FrequencyClient = mock()
  private val mockRedisClient: CustodialWalletRedisClient = mock()
  private val mockProviderMetadataService: ProviderMetadataService = mock()
  private val mockKmsClient: KmsClient = mock()
  private val mockGraphHelper: GraphHelper = mock()
  private lateinit var lookupOrchestrationService: LookupOrchestrationService

  private val defaultMaxRecords = 1
  private val sS58AddressFormat = SS58AddressFormat.SUBSTRATE_ACCOUNT
  private val reservedWords = setOf("admin", "everyone", "all", "administrator", "mod", "moderator", "here", "channel")
  private val blockedCharacters = setOf('"', '#', '%', '(', ')', ',', '.', ':', ';', '<', '>', '@', '\\', '`', '{', '}')
  private val userIdentifier = UserIdentifier("5556667777", UserIdentifierType.PHONE_NUMBER)

  private lateinit var publicKeyDto: PublicKeyDto
  private lateinit var publicKeyBytes: ByteArray
  private lateinit var signUpRequest: SignUpRequest
  private lateinit var batchPayloadToSignRequest: BatchPayloadToSignRequest

  val signature = Signature(SignatureKeyPairType.SR25519, Encoding.HEX, "0x3259258c")

  private val providerMsaId = 1.toBigInteger()
  private val unauthorizedMsaId = 111.toBigInteger()
  private val providerExternalId = "providerExternalId"
  private val sessionId = "sessionId"

  private val keypair = Sr25519KeyPairCreator.createKeyPair()
  private val privateKeyEncrypted = keypair.privateKeyBytes

  private val walletId = 1.toBigInteger()
  private val publicKeyHex = toHex(keypair.publicKeyBytes)
  private val encryptedPrivateKeyHex = toHex(privateKeyEncrypted)
  private val encryptedPrivateKeyType = KeyPairType.SR25519
  private val accountKeyUsageType = KeyUsageType.ACCOUNT
  private val kmsEncryptionKeyId = "8c67db3ad31b432b86843ea633fca71555bf624e"
  private val kmsEncryptionKeyIdType = KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT
  private val createdAt = Instant.now().toEpochMilli().toBigInteger()
  private val lastModified = createdAt

  private val fullUserKeyData = UserKeyData(
    providerMsaId,
    walletId,
    publicKeyHex,
    encryptedPrivateKeyHex,
    encryptedPrivateKeyType,
    kmsEncryptionKeyId,
    kmsEncryptionKeyIdType,
    accountKeyUsageType,
    null,
    createdAt,
    lastModified,
    1.toBigInteger()
  )

  private val userAccountId = BigInteger.ONE
  private val userDetailValue = "bob@gmail.com"
  private val userDetailType = UserDetailType.EMAIL

  private val callback = "https://amplicaaccess.com"
  private val userKeyPairType = KeyPairType.SR25519

  private val testProviderMetadata = ProviderMetadata(
    "Test Provider",
    "test",
    listOf(OriginDescriptor("https", "test-provider.com")),
    emptyMap()
  )

  private val addProviderSignature = Signature(SignatureKeyPairType.SR25519, Encoding.HEX, "0x3259258c")
  private val addProviderType = PayloadType.ADD_PROVIDER
  private val addProviderPayload = AddProviderPayloadRequest(providerMsaId, listOf(1,2), null)
  private val typedAddProviderPayload = TypedPayloadRequestWithSignature(addProviderSignature, addProviderType, addProviderPayload)

  private val claimHandleSignature = Signature(SignatureKeyPairType.SR25519, Encoding.HEX, "0x3259258c")
  private val claimHandleType = PayloadType.CLAIM_HANDLE
  private val claimHandlePayload = HandlePayloadRequest("sampleHandle")
  private val typedClaimHandlePayload = TypedPayloadRequestWithSignature(claimHandleSignature, claimHandleType, claimHandlePayload)

  @BeforeEach
  fun setUp() {
    lookupOrchestrationService = DefaultLookupOrchestrationService(
      DefaultLookupOrchestrationServiceProperties(
        defaultMaxRecords,
        sS58AddressFormat,
        reservedWords,
        blockedCharacters,
        "http://localhost",
        true
      ),
      mockDatabaseService,
      mockFrequencyClient,
      mockRedisClient,
      mockProviderMetadataService,
      mockKmsClient,
      mockGraphHelper,
    )

    val publicKey = "This public key will be 32bytes!"
    publicKeyBytes = publicKey.toByteArray(StandardCharsets.US_ASCII)
    val someSignature = Signature(SignatureKeyPairType.SR25519, Encoding.HEX, "0xCAFEBABE")

    publicKeyDto = PublicKeyDto(
      SS58Codec.encode(publicKeyBytes, SS58AddressFormat.SUBSTRATE_ACCOUNT),
      Encoding.BASE_58,
      PublicKeyFormat.SS58,
      KeyPairType.SR25519
    )
    signUpRequest = SignUpRequest("someExternalUserId", emptyList(), publicKeyDto, someSignature, AddProviderPayloadRequest(
      providerMsaId, emptyList(), null), HandleRequest(someSignature, HandlePayloadRequest("sampleHandle"))
    )
    batchPayloadToSignRequest = BatchPayloadToSignRequest(
      null,
      providerExternalId,
      userIdentifier,
      publicKeyDto,
      callback,
      listOf(typedAddProviderPayload, typedClaimHandlePayload),
      null,
      null,
    )

  }

  @Test
  fun findPublicKeysInSuccess(): Unit = runBlocking {
    //GIVEN
    val keyPairType = KeyPairType.SR25519
    val keyUsageType = KeyUsageType.ACCOUNT
    val publicKeyHex = "0xCAFEBABE"
    val publicKeyDto = PublicKeyDto(publicKeyHex, Encoding.HEX, PublicKeyFormat.SS58, keyPairType)
    val publicKeyRequest = PublicKeyRequest(publicKeyDto)
    val publicKeysRequest = PublicKeysRequest(listOf(publicKeyRequest))
    val userKeyDataList = listOf(UserKeyData.create(BigInteger.ONE, publicKeyHex.toByteArray(StandardCharsets.US_ASCII), EncryptedKey(ByteArray(1) { 0 }, KmsDecryptionKey("1", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)), keyPairType, keyUsageType))
    whenever(mockDatabaseService.findUserKeyDataByKeyPairTypeAndPublicKeys(keyPairType, listOf(publicKeyHex))).thenReturn(userKeyDataList)
    val expectedPublicKeyResponse = PublicKeyResponse(publicKeyDto, false)

    //WHEN
    val retVal = lookupOrchestrationService.findPublicKeysIn(publicKeysRequest)

    //THEN
    val publicKeyResponseList = retVal.publicKeys
    Assertions.assertThat(publicKeyResponseList).hasSize(1)
    val publicKeyResponse = publicKeyResponseList[0]
    Assertions.assertThat(publicKeyResponse).isNotNull
    Assertions.assertThat(publicKeyResponse).isEqualTo(expectedPublicKeyResponse)
  }

  @Test
  fun findPublicKeysInFailedTooManyRecords(): Unit = runBlocking {
    //GIVEN
    val publicKeyRequest = PublicKeyRequest(
      PublicKeyDto(
        "myEncodedValue",
        Encoding.HEX,
        PublicKeyFormat.SS58,
        KeyPairType.SR25519
      )
    )
    val publicKeysRequest = PublicKeysRequest(listOf(publicKeyRequest, publicKeyRequest))

    //WHEN THEN
    val result = runCatching {
      lookupOrchestrationService.findPublicKeysIn(publicKeysRequest)
    }.onFailure {
      Assertions.assertThat(it)
        .isInstanceOf(ApiException::class.java)
        .hasFieldOrPropertyWithValue("apiError", ApiError.TOO_MANY_RECORDS_REQUESTED)
        .hasFieldOrPropertyWithValue("message", "Too many publicKeys are being searched for there were ${publicKeysRequest.publicKeys.size} which is greater than $defaultMaxRecords")
    }
    Assertions.assertThat(result.isFailure).isTrue()
  }

  @Test
  fun findUserKeyDataSuccess(): Unit = runBlocking {
    // GIVEN
    val userAccountId = 34.toBigInteger()
    whenever(
      mockDatabaseService.findUserKeyDataByUserAccountIdKeyUsageTypeAndKeyPairType(
        userAccountId,
        accountKeyUsageType,
        encryptedPrivateKeyType,
      )
    ).thenReturn(
      listOf(fullUserKeyData)
    )

    // WHEN
    val result = lookupOrchestrationService.findUserKeyData(userAccountId, accountKeyUsageType, encryptedPrivateKeyType)

    // THEN
    Assertions.assertThat(result).isEqualTo(fullUserKeyData)
  }

  @Test
  fun findUserKeyDataMultipleKeyDataRows(): Unit = runBlocking {
    // GIVEN
    val userAccountId = 34.toBigInteger()
    val staleUserKeyData = UserKeyData(
      90.toBigInteger(),
      userAccountId,
      publicKeyHex,
      encryptedPrivateKeyHex,
      encryptedPrivateKeyType,
      kmsEncryptionKeyId,
      kmsEncryptionKeyIdType,
      accountKeyUsageType,
      null,
      1739850730000.toBigInteger(),
      1739850730000.toBigInteger(),
      1.toBigInteger(),
    )

    whenever(
      mockDatabaseService.findUserKeyDataByUserAccountIdKeyUsageTypeAndKeyPairType(
        userAccountId,
        accountKeyUsageType,
        encryptedPrivateKeyType,
      )
    ).thenReturn(
      listOf(staleUserKeyData, fullUserKeyData)
    )

    // WHEN
    val result = lookupOrchestrationService.findUserKeyData(userAccountId, accountKeyUsageType, encryptedPrivateKeyType)

    // THEN
    Assertions.assertThat(result).isEqualTo(fullUserKeyData)
  }

  @ParameterizedTest
  @CsvSource(
    value = [
      "all, 102",
      "eth, 91",
      "sr, 102",
    ]
  )
  fun findUserKeyDataOrThrow(subset: String, expectedUserKeyDataId: Int): Unit = runBlocking {
    // GIVEN
    val userAccountId = 34.toBigInteger()
    val usage = KeyUsageType.ACCOUNT
    val preferredKeyPairType = KeyPairType.SR25519

    fun createTestUserKeyData(id: Long, type: KeyPairType, createdAt: Long): UserKeyData {
      return UserKeyData(
        id.toBigInteger(),
        userAccountId,
        publicKeyHex,
        encryptedPrivateKeyHex,
        type,
        kmsEncryptionKeyId,
        kmsEncryptionKeyIdType,
        usage,
        null,
        createdAt.toBigInteger(),
        createdAt.toBigInteger(),
        1.toBigInteger(),
      )
    }

    val allUserKeyData = listOf(
      createTestUserKeyData(90, KeyPairType.SECP256K1, 1700000000000),
      createTestUserKeyData(91, KeyPairType.SECP256K1, 1800000000000),
      createTestUserKeyData(101, KeyPairType.SR25519, 1700000000000),
      createTestUserKeyData(102, KeyPairType.SR25519, 1800000000000),
    )

    val selectedUserKeyData = when (subset) {
      "all" -> allUserKeyData
      "eth" -> allUserKeyData.filter { it.encryptedPrivateKeyType == KeyPairType.SECP256K1 }
      "sr" -> allUserKeyData.filter { it.encryptedPrivateKeyType == KeyPairType.SR25519 }
      else -> throw UnsupportedOperationException("Undefined subset '$subset'")
    }

    whenever(
      mockDatabaseService.findUserKeyDataByUserAccountIdAndKeyUsageType(userAccountId, usage)
    ).thenReturn(selectedUserKeyData)

    // WHEN
    val result = lookupOrchestrationService.findUserKeyDataOrThrow(userAccountId, accountKeyUsageType, preferredKeyPairType)

    // THEN
    Assertions.assertThat(result.id).isEqualTo(expectedUserKeyDataId)
  }

  @Test
  fun findUserDetailsFromUserAccountId(): Unit = runBlocking {
    whenever(mockDatabaseService.findUserIdentifiersByUserAccount(userAccountId)).thenReturn(
      listOf(
        io.amplica.custodial_wallet.db.repository.UserIdentifier(
          userDetailValue,
          userDetailType,
          Instant.now().toEpochMilli().toBigInteger(),
          BigInteger.ONE,
          BigInteger.ONE
        )
      )
    )
    val userDetailList = lookupOrchestrationService.findUserDetailsFromUserAccountId(userAccountId)
    Assertions.assertThat(userDetailList.size).isEqualTo(1)
    Assertions.assertThat(userDetailList[0].value).isEqualTo(userDetailValue)
    Assertions.assertThat(userDetailList[0].type).isEqualTo(userDetailType)
  }

  @Test
  fun getFinalizedHeadBlockNumberSuccess(): Unit = runBlocking {
    whenever(mockFrequencyClient.getFinalizedHeadBlockNumber()).thenReturn(CompletableFuture.completedFuture(1.toBigInteger()))

    // WHEN
    val retVal = lookupOrchestrationService.getFinalizedHeadNumber()

    // THEN
    Assertions.assertThat(retVal).isNotNull
    Assertions.assertThat(retVal.finalizedHeadNumber.toInt() == 1).isTrue()
  }

  @Test
  fun getLatestBlockNumberSuccess(): Unit = runBlocking {
    whenever(mockFrequencyClient.getLastBlockNumber()).thenReturn(CompletableFuture.completedFuture(2.toBigInteger()))

    // WHEN
    val retVal = lookupOrchestrationService.getLatestBlockNumber()

    // THEN
    Assertions.assertThat(retVal).isNotNull
    Assertions.assertThat(retVal.latestBlockNumber.toInt() == 2).isTrue()
  }

  @Test
  fun getHandleSuccess(): Unit = runBlocking {
    val handleResult: CompletableFuture<HandleResponseResult> =
      CompletableFuture.supplyAsync { HandleResponseResult("test", "test", 45) }
    whenever(mockFrequencyClient.getHandle(BigInteger.ONE)).thenReturn(handleResult)
    val fullHandle = lookupOrchestrationService.getHandle(BigInteger.ONE)
    Assertions.assertThat(fullHandle).isEqualTo(GetHandleResponse("test", "test", 45))
  }

  @Test
  fun getHandleSuccessNoHandleForUser(): Unit = runBlocking {
    val fullHandle = lookupOrchestrationService.getHandle(BigInteger.TWO)
    Assertions.assertThat(fullHandle).isEqualTo(GetHandleResponse("", "", 0))
  }

  @Test
  fun getSchemaIdsSuccess(): Unit = runBlocking {
    val grantedSchemas: CompletableFuture<List<SchemaGrantResponse>> = CompletableFuture.supplyAsync {
      listOf(
        SchemaGrantResponse(7, BigInteger.valueOf(100)),
        SchemaGrantResponse(8, BigInteger.valueOf(100)),
        SchemaGrantResponse(9, BigInteger.valueOf(100)),
        SchemaGrantResponse(10, BigInteger.valueOf(100))
      )
    }
    whenever(mockFrequencyClient.getGrantedSchemasByMsaId(BigInteger.TWO, BigInteger.ONE)).thenReturn(grantedSchemas)
    val schemaIdsList = lookupOrchestrationService.getGrantedSchemasByMsaId(BigInteger.TWO, BigInteger.ONE)
    Assertions.assertThat(schemaIdsList).isEqualTo(listOf(7, 8, 9, 10))
  }

  @Test
  fun getSchemaIdsSuccessNoneGranted(): Unit = runBlocking {
    val grantedSchemas: CompletableFuture<List<SchemaGrantResponse>> = CompletableFuture.supplyAsync {
      emptyList()
    }
    whenever(mockFrequencyClient.getGrantedSchemasByMsaId(BigInteger.TWO, BigInteger.ONE)).thenReturn(grantedSchemas)
    val schemaIdsList = lookupOrchestrationService.getGrantedSchemasByMsaId(BigInteger.TWO, BigInteger.ONE)
    Assertions.assertThat(schemaIdsList).isNotNull
    Assertions.assertThat(schemaIdsList).isEmpty()
  }

  @Test
  fun getProviderName(): Unit = runBlocking {
    // GIVEN
    val msaId = BigInteger.ONE
    val providerName = "MeWe"
    val providerRegistryEntry = CommonPrimitivesProviderRegistryEntry(
      providerName,
      emptyMap(),
      "bafkreidgvpkjawlxz6sffxzwgooowe5yt7i6wsyg236mfoks77nywkptdq",
      emptyMap(),
    )

    whenever(mockFrequencyClient.getProviderToRegistryEntryV2(eq(msaId))).thenReturn(
      CompletableFuture.supplyAsync { providerRegistryEntry }
    )

    // WHEN
    val result = lookupOrchestrationService.getProviderName(msaId)

    // THEN
    Assertions.assertThat(result).isEqualTo(providerName)
  }

  @Test
  fun getProviderNameNotFound(): Unit = runBlocking {
    // GIVEN
    val msaId = BigInteger.ONE
    whenever(mockFrequencyClient.getProviderToRegistryEntryV2(eq(msaId))).thenReturn(
      CompletableFuture.supplyAsync { null }
    )

    // WHEN / THEN
    Assertions.assertThatThrownBy {
      runBlocking {
        lookupOrchestrationService.getProviderName(msaId)
      }
    }.hasFieldOrPropertyWithValue("apiError", ApiError.PROVIDER_NOT_FOUND)
      .hasMessage("No provider is registered on chain with msaId/providerId=$msaId")
  }

  @Test
  fun getProviderRegistryEntryV2(): Unit = runBlocking {
    // GIVEN
    val msaId = BigInteger.ONE
    val providerRegistryEntry = CommonPrimitivesProviderRegistryEntry(
      "MeWe",
      emptyMap(),
      "bafkreidgvpkjawlxz6sffxzwgooowe5yt7i6wsyg236mfoks77nywkptdq",
      emptyMap(),
    )
    whenever(mockFrequencyClient.getProviderToRegistryEntryV2(eq(msaId))).thenReturn(
      CompletableFuture.supplyAsync { providerRegistryEntry }
    )

    // WHEN
    val result = lookupOrchestrationService.getProviderRegistryEntryV2(msaId)

    // THEN
    Assertions.assertThat(result).isEqualTo(providerRegistryEntry)
  }

  @Test
  fun getProviderRegistryEntryV2NotFound(): Unit = runBlocking {
    // GIVEN
    val msaId = BigInteger.ONE
    whenever(mockFrequencyClient.getProviderToRegistryEntryV2(eq(msaId))).thenReturn(
      CompletableFuture.supplyAsync { null }
    )

    // WHEN / THEN
    Assertions.assertThatThrownBy {
      runBlocking {
        lookupOrchestrationService.getProviderRegistryEntryV2(msaId)
      }
    }.hasFieldOrPropertyWithValue("apiError", ApiError.PROVIDER_NOT_FOUND)
      .hasMessage("No provider is registered on chain with msaId/providerId=$msaId")
  }

  @Test
  fun validateHandleFailedInvalidWord() : Unit = runBlocking {
    val handle = "admin"

    whenever(
      mockFrequencyClient.getNextSuffixes(handle, 1)
    ).thenReturn(CompletableFuture.completedFuture(NextSuffixesResult(handle, listOf(0))))

    val result = runCatching {
      lookupOrchestrationService.validateHandle(handle)
    }.onFailure {
      Assertions.assertThat(it)
        .isInstanceOf(ApiException::class.java)
        .hasFieldOrPropertyWithValue("apiError", ApiError.INVALID_HANDLE)
        .hasFieldOrPropertyWithValue(
          "message",
          "Handle admin contains an invalid word, please change handle"
        )
    }
    Assertions.assertThat(result.isFailure).isTrue()
  }

  @Test
  fun validateHandleFailedInvalidChar() : Unit = runBlocking {
    val handle = "sample#"

    whenever(
      mockFrequencyClient.getNextSuffixes(handle, 1)
    ).thenReturn(CompletableFuture.completedFuture(NextSuffixesResult(handle, listOf(0))))

    val result = runCatching {
      lookupOrchestrationService.validateHandle(handle)
    }.onFailure {
      Assertions.assertThat(it)
        .isInstanceOf(ApiException::class.java)
        .hasFieldOrPropertyWithValue("apiError", ApiError.INVALID_HANDLE)
        .hasFieldOrPropertyWithValue(
          "message",
          "Handle sample# contains an invalid character, please change handle"
        )
    }
    Assertions.assertThat(result.isFailure).isTrue()
  }

  @Test
  fun validateHandleFailedRpcValidation() : Unit = runBlocking {
    val handle = "example"

    whenever(
      mockFrequencyClient.validateHandle(handle)
    ).thenReturn(CompletableFuture.completedFuture(false))

    whenever(
      mockFrequencyClient.getNextSuffixes(handle, 1)
    ).thenReturn(CompletableFuture.completedFuture(NextSuffixesResult(handle, listOf(0))))

    val result = runCatching {
      lookupOrchestrationService.validateHandle(handle)
    }.onFailure {
      Assertions.assertThat(it)
        .isInstanceOf(ApiException::class.java)
        .hasFieldOrPropertyWithValue("apiError", ApiError.INVALID_HANDLE)
        .hasFieldOrPropertyWithValue(
          "message",
          "Handle example contains an invalid character or word, or is otherwise not valid, please change handle"
        )
    }
    Assertions.assertThat(result.isFailure).isTrue()
  }

  @Test
  fun validateHandleFailedSuffixesExhausted() : Unit = runBlocking {
    val handle = "example"

    whenever(
      mockFrequencyClient.validateHandle(handle)
    ).thenReturn(CompletableFuture.completedFuture(true))

    whenever(
      mockFrequencyClient.getNextSuffixes(handle, 1)
    ).thenReturn(CompletableFuture.completedFuture(NextSuffixesResult(handle, emptyList())))

    val result = runCatching {
      lookupOrchestrationService.validateHandle(handle)
    }.onFailure {
      Assertions.assertThat(it)
        .isInstanceOf(ApiException::class.java)
        .hasFieldOrPropertyWithValue("apiError", ApiError.HANDLE_UNAVAILABLE)
        .hasFieldOrPropertyWithValue(
          "message",
          "Handle example does not have any available suffixes"
        )
    }
    Assertions.assertThat(result.isFailure).isTrue()
  }

  @Test
  fun validateMsaSuccess(): Unit = runBlocking {
    //GIVEN
    val publicKeyBytes = base58DecodeAndExtractPublicKey(publicKeyDto.encodedValue)
    val futureMsa = CompletableFuture.completedFuture(providerMsaId)
    whenever(mockFrequencyClient.getMsaIdByAccountId(publicKeyBytes)).thenReturn(futureMsa)
    whenever(mockProviderMetadataService.resolveProviderMetadata(providerMsaId)).thenReturn(testProviderMetadata)

    //WHEN
    lookupOrchestrationService.validateMsa(providerMsaId, publicKeyDto)
  }

  @Test
  fun validateMsaMismatchFailed(): Unit = runBlocking {
    //GIVEN
    val publicKeyBytes = base58DecodeAndExtractPublicKey(publicKeyDto.encodedValue)
    val futureMsa = CompletableFuture.completedFuture(BigInteger.valueOf(2))
    whenever(mockFrequencyClient.getMsaIdByAccountId(publicKeyBytes)).thenReturn(futureMsa)
    whenever(mockProviderMetadataService.resolveProviderMetadata(providerMsaId)).thenReturn(testProviderMetadata)

    //WHEN THEN
    Assertions.assertThatThrownBy {
      runBlocking {
        lookupOrchestrationService.validateMsa(providerMsaId, publicKeyDto)
      }
    }.isInstanceOf(ApiException::class.java)
      .hasFieldOrPropertyWithValue("apiError", ApiError.MSA_ID_MISMATCH_ERROR)
  }

  @Test
  fun validateMsaWhitelistFailed(): Unit = runBlocking {
    //GIVEN
    val publicKeyBytes = base58DecodeAndExtractPublicKey(publicKeyDto.encodedValue)
    val futureMsa = CompletableFuture.completedFuture(providerMsaId)
    whenever(mockFrequencyClient.getMsaIdByAccountId(publicKeyBytes)).thenReturn(futureMsa)
    whenever(mockProviderMetadataService.resolveProviderMetadata(providerMsaId)).thenReturn(null)

    //WHEN THEN
    Assertions.assertThatThrownBy {
      runBlocking {
        lookupOrchestrationService.validateMsa(providerMsaId, publicKeyDto)
      }
    }.isInstanceOf(ApiException::class.java)
      .hasFieldOrPropertyWithValue("apiError", ApiError.MSA_ID_NOT_ON_WHITELIST_ERROR)
  }

  @Test
  fun findWebsiteSessionBySessionIdSuccess (): Unit = runBlocking {
    //GIVEN
    val websiteSession = WebsiteSession(sessionId, null)
    whenever(mockRedisClient.findWebsiteSessionBySessionId(sessionId)).thenReturn(
      websiteSession
    )

    //WHEN
    val foundWebsiteSession = lookupOrchestrationService.findWebsiteSessionBySessionId(sessionId)

    //THEN
    Assertions.assertThat(foundWebsiteSession).isEqualTo(websiteSession)
  }

  @Test
  fun findWebsiteSessionBySessionIdFailedNoSessionFound(): Unit = runBlocking {
    //GIVEN

    //WHEN THEN
    Assertions.assertThatThrownBy {
      runBlocking {
        lookupOrchestrationService.findWebsiteSessionBySessionId(sessionId)
      }
    }.isInstanceOf(ApiException::class.java)
      .hasMessage("No WebsiteSession found for sessionId=${sessionId}")
      .extracting("apiError").isEqualTo(ApiError.NO_WEBSITE_SESSION_FOUND_ERROR)
  }

  @Test
  fun findWebsiteSessionBySessionIdAndVerificationCodeSuccess (): Unit = runBlocking {
    //GIVEN
    val verificationCode = "verCode"
    val websiteSession = WebsiteSession(sessionId, null)
    whenever(mockRedisClient.findWebsiteSessionBySessionIdAndVerificationCode(sessionId, verificationCode)).thenReturn(
      websiteSession
    )

    //WHEN
    val foundWebsiteSession = lookupOrchestrationService.findWebsiteSessionBySessionIdAndVerificationCode(sessionId, verificationCode)

    //THEN
    Assertions.assertThat(foundWebsiteSession).isEqualTo(websiteSession)
  }

  @Test
  fun findWebsiteSessionBySessionIdAndVerificationCodeFailedNoSessionFound(): Unit = runBlocking {
    //GIVEN
    val verificationCode = "verCode"

    //WHEN THEN
    Assertions.assertThatThrownBy {
      runBlocking {
        lookupOrchestrationService.findWebsiteSessionBySessionIdAndVerificationCode(sessionId, verificationCode)
      }
    }.isInstanceOf(ApiException::class.java)
      .hasMessage("No Website Session found for this session ID $sessionId and verificationCode $verificationCode")
      .extracting("apiError").isEqualTo(ApiError.NO_WEBSITE_SESSION_FOR_TOKEN_ERROR)
  }

  @Test
  fun findWebsiteSessionBySessionIdAndAuthorizationCodeSuccess (): Unit = runBlocking {
    //GIVEN
    val authorizationCode = "authCode"
    val websiteSession = WebsiteSession(sessionId, null)
    whenever(mockRedisClient.findWebsiteSessionBySessionIdAndAuthorizationCode(sessionId, authorizationCode)).thenReturn(
      websiteSession
    )

    //WHEN
    val foundWebsiteSession = lookupOrchestrationService.findWebsiteSessionBySessionIdAndAuthorizationCode(sessionId, authorizationCode)

    //THEN
    Assertions.assertThat(foundWebsiteSession).isEqualTo(websiteSession)
  }

  @Test
  fun findWebsiteSessionBySessionIdAndAuthorizationCodeFailedNoSessionFound(): Unit = runBlocking {
    //GIVEN
    val authorizationCode = "authCode"

    //WHEN THEN
    Assertions.assertThatThrownBy {
      runBlocking {
        lookupOrchestrationService.findWebsiteSessionBySessionIdAndAuthorizationCode(sessionId, authorizationCode)
      }
    }.isInstanceOf(ApiException::class.java)
      .hasMessage("No Website Session found for this session ID $sessionId and/or authorizationCode $authorizationCode")
      .extracting("apiError").isEqualTo(ApiError.NO_WEBSITE_SESSION_FOR_TOKEN_ERROR)
  }

  @Test
  fun findBatchPayloadToSignRequestBySessionIdAndAuthenticationCodeSuccess (): Unit = runBlocking {
    //GIVEN
    val authenticationCode = "authCode"
    whenever(mockRedisClient.findBatchPayloadToSignRequestBySessionIdAndAuthenticationCode(sessionId, authenticationCode)).thenReturn(
      batchPayloadToSignRequest
    )

    //WHEN
    val foundBatchPayloadToSignRequest = lookupOrchestrationService.findBatchPayloadToSignRequestBySessionIdAndAuthenticationCode(sessionId, authenticationCode)

    //THEN
    Assertions.assertThat(foundBatchPayloadToSignRequest).isEqualTo(batchPayloadToSignRequest)
  }

  @Test
  fun findBatchPayloadToSignRequestBySessionIdAndAuthenticationCodeFailedNoSessionFound(): Unit = runBlocking {
    //GIVEN
    val authenticationCode = "authCode"

    //WHEN THEN
    Assertions.assertThatThrownBy {
      runBlocking {
        lookupOrchestrationService.findBatchPayloadToSignRequestBySessionIdAndAuthenticationCode(sessionId, authenticationCode)
      }
    }.isInstanceOf(ApiException::class.java)
      .hasMessage("No Batch payload to sign was found for authentication code=$authenticationCode sessionId=$sessionId")
      .extracting("apiError").isEqualTo(ApiError.NO_PAYLOAD_FOUND_ERROR)
  }

  @Test
  fun findBatchPayloadToSignRequestBySessionIdAndAuthorizationCodeSuccess (): Unit = runBlocking {
    //GIVEN
    val authorizationCode = "authCode"
    whenever(mockRedisClient.findBatchPayloadToSignRequestBySessionIdAndAuthorizationCode(sessionId, authorizationCode)).thenReturn(
      batchPayloadToSignRequest
    )

    //WHEN
    val foundBatchPayloadToSignRequest = lookupOrchestrationService.findBatchPayloadToSignRequestBySessionIdAndAuthorizationCode(sessionId, authorizationCode)

    //THEN
    Assertions.assertThat(foundBatchPayloadToSignRequest).isEqualTo(batchPayloadToSignRequest)
  }

  @Test
  fun findBatchPayloadToSignRequestBySessionIdAndAuthorizationCodeFailedNoSessionFound(): Unit = runBlocking {
    //GIVEN
    val authorizationCode = "authCode"

    //WHEN THEN
    Assertions.assertThatThrownBy {
      runBlocking {
        lookupOrchestrationService.findBatchPayloadToSignRequestBySessionIdAndAuthorizationCode(sessionId, authorizationCode)
      }
    }.isInstanceOf(ApiException::class.java)
      .hasMessage("No Batch payload to sign was found for authorization code=$authorizationCode sessionId=$sessionId")
      .extracting("apiError").isEqualTo(ApiError.NO_PAYLOAD_FOUND_ERROR)
  }

  @Test
  fun verifyWhitelistedRequestSuccess(): Unit = runBlocking {
    //GIVEN
    whenever(mockProviderMetadataService.resolveProviderMetadata(providerMsaId)).thenReturn(testProviderMetadata)

    //WHEN THEN
    assertDoesNotThrow {
      runBlocking { lookupOrchestrationService.verifyWhitelistedProviderMsaId(providerMsaId) }
    }
  }

  @Test
  fun verifyWhitelistedRequestFailedUnauthorizedMsaId(): Unit = runBlocking {
    //GIVEN
    whenever(mockProviderMetadataService.resolveProviderMetadata(providerMsaId)).thenReturn(null)

    //WHEN THEN
    Assertions.assertThatThrownBy {
      runBlocking { lookupOrchestrationService.verifyWhitelistedProviderMsaId(unauthorizedMsaId) }
    }.isInstanceOf(ApiException::class.java)
      .hasFieldOrPropertyWithValue("apiError", ApiError.MSA_ID_NOT_ON_WHITELIST_ERROR)
      .hasMessage("The following msa id is not whitelisted: $unauthorizedMsaId")
  }

  @Test
  fun getDecryptedPrivateKey(): Unit = runBlocking {
    // GIVEN
    val encryptedKey = EncryptedKey(
      fromHex(encryptedPrivateKeyHex),
      KmsDecryptionKey(kmsEncryptionKeyId, kmsEncryptionKeyIdType)
    )
    val fakePrivateKey = fromHex("0x5678")
    whenever(mockKmsClient.decryptPrivateKey(eq(encryptedKey))).thenReturn(fakePrivateKey)

    // WHEN
    val result = lookupOrchestrationService.getDecryptedPrivateKey(fullUserKeyData)

    // THEN
    Assertions.assertThat(result).isEqualTo(fakePrivateKey)
  }

  @ParameterizedTest
  @CsvSource(value = [
    "SR25519,ACCOUNT,",
    "SECP256K1,ACCOUNT,",
    "X25519,ACCOUNT,Invalid KeyPairType",
    "X25519,GRAPH,Invalid KeyUsageType",
  ])
  fun getDecryptedAccountKeyPair(userKeyPairType: KeyPairType, keyUsageType: KeyUsageType, errorMessage: String?): Unit = runBlocking {
    // GIVEN
    val keyPair = when (userKeyPairType) {
      KeyPairType.SR25519 -> Sr25519CryptoProvider.createKeyPair()
      KeyPairType.SECP256K1 -> Secp256K1CryptoProvider.createKeyPair()
      KeyPairType.X25519 -> X25519CryptoProvider.createKeyPair()
      else -> throw UnsupportedOperationException("Invalid user key pair type: $userKeyPairType")
    }
    val testEncryptedValue = fromHex("0x1234")
    val testEncryptedId = "test-id"
    val encryptedKey = EncryptedKey(
      testEncryptedValue,
      KmsDecryptionKey(testEncryptedId, KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT),
    )
    val userKeyData = UserKeyData.create(
      BigInteger.ONE,
      fromHex(toHex(keyPair.publicKeyBytes.bytes)),
      encryptedKey,
      userKeyPairType,
      keyUsageType,
    )

    whenever(mockKmsClient.decryptPrivateKey(eq(encryptedKey))).thenReturn(keyPair.privateKeyBytes.bytes)

    when (errorMessage) {
      null -> {
        // WHEN
        val result = lookupOrchestrationService.getDecryptedAccountKeyPair(userKeyData)

        // THEN
        Assertions.assertThat(result.publicKeyBytes.bytes).isEqualTo(keyPair.publicKeyBytes.bytes)
        Assertions.assertThat(result.privateKeyBytes.bytes).isEqualTo(keyPair.privateKeyBytes.bytes)
        Assertions.assertThat(result.cryptoProvider).isEqualTo(keyPair.cryptoProvider)
      }

      else -> {
        // WHEN / THEN
        Assertions.assertThatThrownBy {
          runBlocking {
            lookupOrchestrationService.getDecryptedAccountKeyPair(userKeyData)
          }
        }.hasMessageStartingWith(errorMessage)
      }
    }

  }

  @ParameterizedTest
  @CsvSource(
    value = [
      // A 'DNSP encoded public key' (the correct way according to the Frequency spec)
      "0x40a9c8947e170f72b29b8ac1eb143e3e87ee867114c2a68d156a9a2cfcadde8e7b, 0xa9c8947e170f72b29b8ac1eb143e3e87ee867114c2a68d156a9a2cfcadde8e7b",
      // A 'bare' public key (not technically correct, but we try to handle gracefully)
      "0xa9c8947e170f72b29b8ac1eb143e3e87ee867114c2a68d156a9a2cfcadde8e7b, 0xa9c8947e170f72b29b8ac1eb143e3e87ee867114c2a68d156a9a2cfcadde8e7b",
    ]
  )
  fun getGraphKeysRegisteredOnChainForUser(payloadHex: String, expectedHex: String): Unit = runBlocking {
    // GIVEN
    val msaId = 1.toBigInteger()
    val graphKeyStorageSchemaId = 18


    whenever(mockGraphHelper.getGraphKeySchemaId()).thenReturn(graphKeyStorageSchemaId)

    val response = ItemizedStoragePageResponseResult(
      msaId,
      graphKeyStorageSchemaId,
      78, // made-up value
      35, // made-up value
      listOf(
        ItemizedStorageResponseResult(0, payloadHex)
      )
    )
    whenever(mockFrequencyClient.getItemizedStorage(eq(msaId), eq(graphKeyStorageSchemaId))).thenReturn(
      CompletableFuture.completedFuture(response)
    )

    // WHEN
    val graphKeys = lookupOrchestrationService.getGraphKeysRegisteredOnChainForUser(msaId)

    // THEN
    Assertions.assertThat(graphKeys).hasSize(1)
    Assertions.assertThat(toHex(graphKeys.first())).isEqualTo(expectedHex)
  }

  @Test
  fun findSiwaSessionOrDeleteAndThrow_whenNull(): Unit = runBlocking {
    //GIVEN
    whenever(mockRedisClient.findSiwaSessionBySessionId(sessionId)).thenReturn(null)

    //WHEN
    Assertions.assertThatThrownBy {
      runBlocking {
        lookupOrchestrationService.findSiwaSessionOrThrow(sessionId)
      }
    }.isInstanceOf(ApiException::class.java)
      .hasFieldOrPropertyWithValue("apiError", ApiError.SIWA_SESSION_NOT_FOUND)
      .hasMessage("No SIWA session found for the given session ID")
      .extracting("structuredArguments").isEqualTo(mapOf("sessionId" to sessionId))
  }

  @Test
  fun findUnauthenticatedSiwaSessionOrDeleteAndThrow_whenSession(): Unit = runBlocking {
    //GIVEN
    val siwaRequest: SiwaRequest = mock()
    val unauthenticatedSiwaSession = UnauthenticatedSiwaSession(
      siwaRequest,
      sessionId,
      callback,
      userKeyPairType,
      SiwaFlowKind.SOCIAL,
    )
    whenever(mockRedisClient.findSiwaSessionBySessionId(sessionId)).thenReturn(unauthenticatedSiwaSession)

    //WHEN
    val siwaSession = lookupOrchestrationService.findSiwaSessionOrThrow(sessionId)

    //THEN
    Assertions.assertThat(siwaSession).isSameAs(unauthenticatedSiwaSession)
  }

  @Test
  fun findAuthenticatedSiwaSessionOrThrow_whenAuthenticatedSession(): Unit = runBlocking {
    //GIVEN
    val siwaRequest: SiwaRequest = mock()
    val authenticatedSiwaSession = AuthenticatedSiwaSession(
      siwaRequest,
      sessionId,
      userIdentifier,
      callback,
      userKeyPairType,
      SiwaFlowKind.SOCIAL,
    )
    whenever(mockRedisClient.findSiwaSessionBySessionId(sessionId)).thenReturn(authenticatedSiwaSession)

    //WHEN
    val siwaSession = lookupOrchestrationService.findAuthenticatedSiwaSessionOrThrow(sessionId)

    //THEN
    Assertions.assertThat(siwaSession).isSameAs(authenticatedSiwaSession)
  }

  @Test
  fun findAuthenticatedSiwaSessionOrThrow_whenElse(): Unit = runBlocking {
    //GIVEN
    whenever(mockRedisClient.findSiwaSessionBySessionId(sessionId)).thenReturn(null)

    //WHEN THEN
    Assertions.assertThatThrownBy {
      runBlocking {
        lookupOrchestrationService.findAuthenticatedSiwaSessionOrThrow(sessionId)
      }
    }.isInstanceOf(ApiException::class.java)
      .hasFieldOrPropertyWithValue("apiError", ApiError.SIWA_SESSION_NOT_FOUND)
      .hasMessage("No SIWA session found for the given session ID")
      .extracting("structuredArguments").isEqualTo(mapOf(LoggingAttributes.SESSION_ID to sessionId))
  }

  @ParameterizedTest
  @CsvSource(value = [
    // Happy paths
    "true, single,",
    "true, multiple,",
    // Error paths
    "true, null, 42",
    "true, empty, 42",
    "false, null, 2",
  ])
  fun findWebsiteOrSiwaAuthenticatedUserDataOrThrow_findsWebsiteSession(
    loggedIn: Boolean,
    userAccountIdDescription: String,
    expectedApiErrorId: Int?
  ): Unit = runBlocking {
    // GIVEN
    val sessionId = "fake-session-uuid"
    val userAccountIds = when (userAccountIdDescription) {
      "null" -> null
      "empty" -> emptyList()
      "multiple" -> listOf(450.toBigInteger(), 700.toBigInteger())
      else -> listOf(450.toBigInteger())
    }
    val websiteSession = WebsiteSession(
      sessionId,
      callbackUrl = null,
      userAccountIds = userAccountIds,
      loggedIn = if (loggedIn) UserState.LOGGED_IN else UserState.LOGGED_OUT
    )

    whenever(mockRedisClient.findWebsiteSessionBySessionId(sessionId)).thenReturn(websiteSession)

    // WHEN / THEN
    when (expectedApiErrorId) {
      null -> {
        val result = lookupOrchestrationService.findWebsiteOrSiwaAuthenticatedUserDataOrThrow(sessionId)

        val expectedUserData = AuthenticatedUserData(userAccountIds?.first()!!)
        Assertions.assertThat(result).isEqualTo(expectedUserData)
      }
      else -> {
        val expectedApiError = ApiError.fromId(expectedApiErrorId)
        Assertions.assertThatThrownBy {
          runBlocking {
            lookupOrchestrationService.findWebsiteOrSiwaAuthenticatedUserDataOrThrow(sessionId)
          }
        }.isInstanceOf(ApiException::class.java).hasFieldOrPropertyWithValue("apiError", expectedApiError)
      }
    }
  }

  @ParameterizedTest
  @CsvSource(value = [
    "true, 450,", // Happy path
    "true, , 42",
    "false, , 2",
  ])
  fun findWebsiteOrSiwaAuthenticatedUserDataOrThrow_findsSiwaSession(
    isAuthenticated: Boolean,
    userAccountId: BigInteger?,
    expectedApiErrorId: Int?
  ): Unit = runBlocking {
    // GIVEN
    val sessionId = "fake-session-uuid"
    val authenticatedSession = AuthenticatedSiwaSession(
      SIWA_REQUEST,
      sessionId,
      EMAIL_IDENTIFIER,
      "example.com",
      userKeyPairType,
      SiwaFlowKind.SOCIAL,
      userAccountId = userAccountId
    )

    val siwaSession = when {
      isAuthenticated -> authenticatedSession
      else -> authenticatedSession.downgradeSiwaSession()
    }

    whenever(mockRedisClient.findSiwaSessionBySessionId(sessionId)).thenReturn(siwaSession)

    // WHEN / THEN
    when (expectedApiErrorId) {
      null -> {
        val result = lookupOrchestrationService.findWebsiteOrSiwaAuthenticatedUserDataOrThrow(sessionId)

        val expectedUserData = AuthenticatedUserData(userAccountId!!)
        Assertions.assertThat(result).isEqualTo(expectedUserData)
      }
      else -> {
        val expectedApiError = ApiError.fromId(expectedApiErrorId)
        Assertions.assertThatThrownBy {
          runBlocking {
            lookupOrchestrationService.findWebsiteOrSiwaAuthenticatedUserDataOrThrow(sessionId)
          }
        }.isInstanceOf(ApiException::class.java).hasFieldOrPropertyWithValue("apiError", expectedApiError)
      }
    }
  }

  @Test
  fun findWebsiteOrSiwaAuthenticatedUserDataOrThrow_findsTwoSessions(): Unit = runBlocking {
    // GIVEN
    val sessionId = "fake-session-uuid"
    val userAccountId = 450.toBigInteger()
    val websiteSession = WebsiteSession(
      sessionId,
      callbackUrl = null,
      userAccountIds = listOf(userAccountId),
      loggedIn = UserState.LOGGED_IN
    )
    val authenticatedSiwaSession = AuthenticatedSiwaSession(
      SIWA_REQUEST,
      sessionId,
      EMAIL_IDENTIFIER,
      "example.com",
      userKeyPairType,
      SiwaFlowKind.SOCIAL,
      userAccountId = 666.toBigInteger()
    )

    whenever(mockRedisClient.findWebsiteSessionBySessionId(sessionId)).thenReturn(websiteSession)
    whenever(mockRedisClient.findSiwaSessionBySessionId(sessionId)).thenReturn(authenticatedSiwaSession)

    // WHEN
    val result = lookupOrchestrationService.findWebsiteOrSiwaAuthenticatedUserDataOrThrow(sessionId)

    // THEN
    val expectedUserData = AuthenticatedUserData(userAccountId)
    Assertions.assertThat(result).isEqualTo(expectedUserData)
  }

  @Test
  fun findWebsiteOrSiwaAuthenticatedUserDataOrThrow_findsNoSessions(): Unit = runBlocking {
    // GIVEN
    val sessionId = "fake-session-uuid"

    // WHEN / THEN
    Assertions.assertThatThrownBy {
      runBlocking {
        lookupOrchestrationService.findWebsiteOrSiwaAuthenticatedUserDataOrThrow(sessionId)
      }
    }.isInstanceOf(ApiException::class.java).hasFieldOrPropertyWithValue("apiError", ApiError.NO_SESSION_INFO_FOUND_ERROR)
  }
}
