package io.amplica.custodial_wallet.orchestration.ics

import com.strategyobject.substrateclient.crypto.ss58.SS58AddressFormat
import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.client.kms.EncryptedData
import io.amplica.custodial_wallet.client.kms.EncryptedKey
import io.amplica.custodial_wallet.client.kms.KmsDecryptionKey
import io.amplica.custodial_wallet.client.kms.KmsEncryptionAlgorithm
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifier
import io.amplica.custodial_wallet.db.repository.*
import io.amplica.custodial_wallet.db.spring.DelegatingTransactionalOperator
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.orchestration.LookupOrchestrationService
import io.amplica.custodial_wallet.orchestration.signing.SigningOrchestrationService
import io.amplica.custodial_wallet.orchestration.siwa.io.amplica.custodial_wallet.orchestration.ics.MockIcsPayloads
import io.amplica.custodial_wallet.service.ics.*
import io.amplica.custodial_wallet.service.ics_whitelist.IcsWhitelistService
import io.amplica.custodial_wallet.service.key.KeyService
import io.amplica.custodial_wallet.util.createTransactionalOperatorDouble
import io.amplica.custodial_wallet.util.fromHex
import io.amplica.custodial_wallet.util.generateUserIdentifier
import io.amplica.custodial_wallet.util.key_creation.*
import io.amplica.custodial_wallet.util.toHex
import io.amplica.frequency.crypto.AccountKeyPair
import io.amplica.frequency.crypto.provider.Sr25519CryptoProvider
import io.amplica.frequency.payload.ItemizedSignaturePayloadV2
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.*
import java.math.BigInteger
import java.time.Instant
import java.util.*

class DefaultIcsUserOrchestrationServiceTest {

  companion object {
    const val PUBLIC_KEY_INTENT = "ics.public-key-key-agreement"
    const val CONTEXT_GROUP_ACL_INTENT = "ics.context-group-acl"
  }

  private val randomGenerator = Random()

  // Dependencies
  private val properties = DefaultIcsUserOrchestrationProperties(
    10,
    "https://example.com",
    false,
  )
  private val mockLookupService: LookupOrchestrationService = mock()
  private val mockSigningOrchestrationService: SigningOrchestrationService = mock()
  private val mockKeyService: KeyService = mock()
  private val mockIcsService: IcsService = mock()
  private val sS58AddressFormat: SS58AddressFormat = SS58AddressFormat.SUBSTRATE_ACCOUNT
  private val mockIcsWhitelistService: IcsWhitelistService = mock()
  private val mockDatabaseService: CustodialWalletDatabaseService = mock()

  private val transactionalOperatorTestDouble = createTransactionalOperatorDouble()
  private val delegatingTransactionalOperator = DelegatingTransactionalOperator(transactionalOperatorTestDouble, transactionalOperatorTestDouble)

  private val service = DefaultIcsUserOrchestrationService(
    properties,
    mockLookupService,
    mockSigningOrchestrationService,
    mockKeyService,
    mockIcsService,
    sS58AddressFormat,
    mockIcsWhitelistService,
    mockDatabaseService,
    delegatingTransactionalOperator,
  )

  private val currentBlockNumber = 34.toBigInteger()
  private val publicKeySchemaId = 7
  private val contextGroupAclSchemaId = 8

  private lateinit var userIdentifier: UserIdentifier
  private lateinit var userMsaId: BigInteger
  private lateinit var providerControlKeyPair: AccountKeyPair
  private lateinit var providerPublicKeyDto: PublicKeyDto
  private lateinit var nonce: String
  private lateinit var requestSignature: ByteArray

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    userIdentifier = generateUserIdentifier()
    userMsaId = BigInteger(generateRandomByteArrayOfSize(randomGenerator, 8))
    providerControlKeyPair = Sr25519CryptoProvider.createKeyPair()
    providerPublicKeyDto = Sr25519KeyPairBytes(
      providerControlKeyPair.publicKeyBytes.bytes,
      providerControlKeyPair.privateKeyBytes.bytes,
    ).toPublicKeyDto(SS58AddressFormat.SUBSTRATE_ACCOUNT)
    nonce = UUID.randomUUID().toString()
    requestSignature = generateRandomByteArrayOfSize(randomGenerator, 32)

    whenever(
      mockLookupService.retrieveCurrentBlockNumber()
    ).thenReturn(currentBlockNumber.toLong())

    whenever(
      mockLookupService.getLatestSchemaIdForIntent(eq(PUBLIC_KEY_INTENT))
    ).thenReturn(publicKeySchemaId)
    whenever(
      mockLookupService.getLatestSchemaIdForIntent(eq(CONTEXT_GROUP_ACL_INTENT))
    ).thenReturn(contextGroupAclSchemaId)
  }

  private fun createRetrievePayloadsSignedRequest(): IcsRetrievePayloadsSignedRequest {
    val payload = IcsMsaIdRequestPayload(userMsaId.toString(), nonce)

    val signature = Signature(
      SignatureKeyPairType.SR25519,
      Encoding.HEX,
      toHex(requestSignature),
    )

    return IcsRetrievePayloadsSignedRequest(
      providerPublicKeyDto,
      signature,
      payload,
    )
  }

  private fun createGetContextGroupKeySignedRequest(): IcsContextGroupKeySignedRequest {
    val signature = Signature(
      SignatureKeyPairType.SR25519,
      Encoding.HEX,
      toHex(requestSignature),
    )
    val payload = IcsMsaIdRequestPayload(
      userMsaId.toString(),
      UUID.randomUUID().toString(),
    )

    return IcsContextGroupKeySignedRequest(providerPublicKeyDto, signature, payload)
  }

  private fun createGetContextItemKeySignedRequest(): IcsContextItemKeySignedRequest {
    val signature = Signature(
      SignatureKeyPairType.SR25519,
      Encoding.HEX,
      toHex(requestSignature),
    )
    val payload = IcsContextItemKeyRequestPayload(
      userMsaId.toString(),
      UUID.randomUUID().toString(),
      UUID.randomUUID().toString(),
    )

    return IcsContextItemKeySignedRequest(providerPublicKeyDto, signature, payload)
  }

  @Test
  fun retrieveUserPayloadsDeniesNonWhitelistedProvider(): Unit = runBlocking {
    // GIVEN
    val request = createRetrievePayloadsSignedRequest()
    val providerMsaId = 24.toBigInteger()

    whenever(
      mockLookupService.retrieveCurrentBlockNumber()
    ).thenReturn(currentBlockNumber.toLong())

    whenever(
      mockLookupService.retrieveMsaId(any())
    ).thenReturn(providerMsaId)

    // WHEN / THEN
    Assertions.assertThatThrownBy {
      runBlocking {
        service.retrieveUserPayloads(request)
      }
    }.isInstanceOf(ApiException::class.java)
      .hasFieldOrPropertyWithValue("apiError", ApiError.MSA_ID_NOT_ON_WHITELIST_ERROR)
      .hasMessageEndingWith("not authorized to use any ICS endpoints")
  }

  @Nested
  inner class WithAWhitelistedUnregisteredProvider {

    private val providerMsaId = 24.toBigInteger()

    @BeforeEach
    fun setUp(): Unit = runBlocking {
      // Provider has MSA ID on chain
      whenever(
        mockLookupService.retrieveMsaId(any())
      ).thenReturn(providerMsaId)

      // Provider MSA ID is whitelisted
      whenever(
        mockIcsWhitelistService.providerIsWhitelisted(eq(providerMsaId))
      ).thenReturn(true)
      // Provider's signature is valid
      whenever(
        mockSigningOrchestrationService.verifySignedPayload(
          eq(providerPublicKeyDto),
          any(),
          eq(
            Signature(
              SignatureKeyPairType.SR25519,
              Encoding.HEX,
              toHex(requestSignature),
            )
          )
        )
      ).thenReturn(true)
    }

    /** Fails because provider doesn't have an ICS public key registered */
    @Test
    fun retrieveUserPayloadsFails(): Unit = runBlocking {
      // GIVEN
      val request = createRetrievePayloadsSignedRequest()

      // WHEN / THEN
      Assertions.assertThatThrownBy {
        runBlocking {
          service.retrieveUserPayloads(request)
        }
      }.isInstanceOf(ApiException::class.java).hasFieldOrPropertyWithValue("apiError", ApiError.NO_PUBLIC_KEY_FOUND)
        .hasMessageStartingWith("Could not find an ICS public key for provider")
    }
  }

  @Nested
  inner class WithAWhitelistedIcsProvider {

    private val providerMsaId = 24.toBigInteger()
    private val providerIcsPublicKey = IcsPublicKey(
      generateRandomByteArrayOfSize(randomGenerator, 32), IcsKeyType.ED25519
    )

    @BeforeEach
    fun setUp(): Unit = runBlocking {
      whenever(
        mockLookupService.retrieveCurrentBlockNumber()
      ).thenReturn(currentBlockNumber.toLong())

      // Provider has MSA ID on chain
      whenever(
        mockLookupService.retrieveMsaId(any())
      ).thenReturn(providerMsaId)

      // Provider MSA ID is whitelisted
      whenever(
        mockIcsWhitelistService.providerIsWhitelisted(eq(providerMsaId))
      ).thenReturn(true)
      // Provider's signature is valid
      whenever(
        mockSigningOrchestrationService.verifySignedPayload(
          eq(providerPublicKeyDto),
          any(),
          eq(
            Signature(
              SignatureKeyPairType.SR25519,
              Encoding.HEX,
              toHex(requestSignature),
            )
          )
        )
      ).thenReturn(true)

      // Provider has ICS public key registered
      whenever(
        mockIcsService.getLatestIcsPublicKey(eq(providerMsaId))
      ).thenReturn(IndexedValue(0, providerIcsPublicKey))

      whenever(
        mockIcsService.encryptProviderMsaId(any(), eq(providerMsaId))
      ).thenReturn(IcsEncryptionResult(
        generateRandomByteArrayOfSize(randomGenerator, 32),
        generateRandomByteArrayOfSize(randomGenerator, 12)
      ))
    }

    /** Fails because a user account does not exist */
    @Test
    fun retrieveUserPayloadsFails(): Unit = runBlocking {
      // GIVEN
      val request = createRetrievePayloadsSignedRequest()

      whenever(
        mockLookupService.getUniversalAddressesByMsaId(eq(userMsaId))
      ).thenReturn(listOf(generateRandomByteArrayOfSize(randomGenerator, 32)))

      whenever(
        mockLookupService.getUserAccountIdByMsaIdOrThrow(eq(userMsaId))
      ).thenThrow(
        ApiException(ApiError.NO_USER_ACCOUNT_ID_FOUND, "Could not find a userAccountId")
      )

      // WHEN / THEN
      Assertions.assertThatThrownBy {
        runBlocking {
          service.retrieveUserPayloads(request)
        }
      }.isInstanceOf(ApiException::class.java)
        .hasFieldOrPropertyWithValue("apiError", ApiError.NO_USER_ACCOUNT_ID_FOUND)
    }

    @Nested
    inner class AndACustodialWalletUser {

      private val userAccountId = 12.toBigInteger()

      // User's Sr25519 control key pair
      private val userControlKeyPair = Sr25519KeyPairBytes(
        generateRandomByteArrayOfSize(randomGenerator, 32),
        generateRandomByteArrayOfSize(randomGenerator, 32),
      )
      private val userControlUserKeyData = UserKeyData.create(
        userAccountId,
        userControlKeyPair.publicKeyBytes,
        EncryptedKey(
          generateRandomByteArrayOfSize(randomGenerator, 32),
          KmsDecryptionKey("decryption-key", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT),
        ),
        KeyPairType.SR25519,
        KeyUsageType.ACCOUNT,
        null,
      )

      private lateinit var contextGroupId: ByteArray

      @BeforeEach
      fun setUp(): Unit = runBlocking {

        contextGroupId = generateRandomByteArrayOfSize(randomGenerator, 8)

        // User has an account with a control keypair
        whenever(
          mockLookupService.getUserAccountIdByMsaIdOrThrow(eq(userMsaId))
        ).thenReturn(userAccountId)
        whenever(
          mockLookupService.findUserKeyDataOrThrow(
            eq(userControlUserKeyData.userAccountId),
            eq(userControlUserKeyData.keyUsageType),
            eq(userControlUserKeyData.encryptedPrivateKeyType)
          ),
        ).thenReturn(userControlUserKeyData)
        whenever(
          mockKeyService.decryptUserAccountKeyData(
            argThat { this.publicKeyHex == userControlUserKeyData.publicKeyHex })
        ).thenReturn(userControlKeyPair)

        // User has an MSA ID
        whenever(
          mockLookupService.getMsaIdByUserIdentifier(eq(userIdentifier))
        ).thenReturn(userMsaId)

        whenever(
          mockIcsService.deriveContextGroupId(
            eq(userMsaId),
            eq(providerMsaId),
            any(),
            any()
          )
        ).thenReturn(contextGroupId)
      }

      private fun mockIcsPayloadCalls(userIcsKeyPair: IcsKeyPair): MockIcsPayloads {
        val expirationBlockNumber = currentBlockNumber.toLong() + properties.signupBlockExpiration

        val serializedIcsPublicKey = generateRandomByteArrayOfSize(randomGenerator, 32)
        val mockPublicKeyRegistrationPayload = TypedPayloadResponseWithSignature(
          Signature(
            userControlKeyPair.keyPairType.signatureKeyPairType,
            Encoding.HEX,
            toHex(generateRandomByteArrayOfSize(randomGenerator, 32)),
          ),
          FrequencyEndpoint(Pallet.StatefulStorage, Extrinsic.ApplyItemActionsWithSignatureV2),
          DebugDescription.HCP_PUBLIC_KEY_PAYLOAD,
          PayloadType.ITEM_ACTIONS,
          ItemizedSignaturePayloadResponse(
            publicKeySchemaId, 0, expirationBlockNumber, listOf(AddItemAction(toHex(serializedIcsPublicKey)))
          )
        )

        val serializedContextGroupAcl = generateRandomByteArrayOfSize(randomGenerator, 64)
        val mockContextGroupAclPayload = TypedPayloadResponseWithSignature(
          Signature(
            userControlKeyPair.keyPairType.signatureKeyPairType,
            Encoding.HEX,
            toHex(generateRandomByteArrayOfSize(randomGenerator, 32)),
          ),
          FrequencyEndpoint(Pallet.StatefulStorage, Extrinsic.ApplyItemActionsWithSignatureV2),
          DebugDescription.HCP_ACL_PAYLOAD,
          PayloadType.ITEM_ACTIONS,
          ItemizedSignaturePayloadResponse(
            contextGroupAclSchemaId,
            0,
            expirationBlockNumber,
            listOf<ItemAction>(AddItemAction(toHex(serializedContextGroupAcl))),
          ),
        )

        val mockPayloads = MockIcsPayloads(
          mockPublicKeyRegistrationPayload,
          mockContextGroupAclPayload,
        )

        whenever(
          mockIcsService.serializePublicKey(
            eq(userIcsKeyPair.publicKey),
          )
        ).thenReturn(serializedIcsPublicKey)
        whenever(
          mockSigningOrchestrationService.signPayload(
            any(),
            argThat { this is ItemizedSignaturePayloadV2 && this.schemaId == publicKeySchemaId },
            eq(Encoding.HEX),
          )
        ).thenReturn(mockPublicKeyRegistrationPayload.signature)

        whenever(
          mockIcsService.serializeContextGroupAcl(
            argThat { toHex(this.contextGroupId) == toHex(contextGroupId) },
          )
        ).thenReturn(serializedContextGroupAcl)
        whenever(
          mockSigningOrchestrationService.signPayload(
            any(),
            argThat { this is ItemizedSignaturePayloadV2 && this.schemaId == contextGroupAclSchemaId },
            eq(Encoding.HEX),
          )
        ).thenReturn(mockContextGroupAclPayload.signature)

        return mockPayloads
      }

      @Nested
      inner class WithAnIcsKeyPair {

        private val userSeedDataId = 42.toBigInteger()
        private val userSeed = generateRandomByteArrayOfSize(randomGenerator, 32)
        private val userSeedData = UserSeedData.create(
          userAccountId,
          SeedUsageType.HCP_MASTER,
          toHex(generateRandomByteArrayOfSize(randomGenerator, 32)),
          toHex(generateRandomByteArrayOfSize(randomGenerator, 32)),
          "decryption-key",
          KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
        ).apply { this.id = userSeedDataId }

        private val contextItemSeedDataId = 42.toBigInteger()
        private val contextItemSeed = generateRandomByteArrayOfSize(randomGenerator, 32)
        private val contextItemSeedData = UserSeedData.create(
          userAccountId,
          SeedUsageType.CONTEXT_ITEM_MASTER,
          toHex(generateRandomByteArrayOfSize(randomGenerator, 32)),
          toHex(generateRandomByteArrayOfSize(randomGenerator, 32)),
          "decryption-key",
          KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
        ).apply { this.id = contextItemSeedDataId }

        private val userIcsKeyPairBytes = Ed25519KeyPairBytes(
          generateRandomByteArrayOfSize(randomGenerator, 32),
          generateRandomByteArrayOfSize(randomGenerator, 64),
        )
        private val icsUserKeyData = UserKeyData.create(
          userAccountId, userIcsKeyPairBytes.publicKeyBytes, EncryptedKey(
            generateRandomByteArrayOfSize(randomGenerator, 64),
            KmsDecryptionKey("decryption-key", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT),
          ), KeyPairType.ED25519, KeyUsageType.ICS, userSeedDataId
        ).apply { this.id = 45.toBigInteger() }

        private val userIcsKeyPair = IcsKeyPair(
          userIcsKeyPairBytes.publicKeyBytes,
          userIcsKeyPairBytes.privateKeyBytes,
          IcsKeyType.ED25519,
        )

        private val onChainSymmetricKey = generateRandomByteArrayOfSize(randomGenerator, 32)
        private val onChainDerivedKeyData = UserDerivedKeyData.create(
          userSeedDataId,
          "deriviation/path",
          DerivedKeyUsageType.ON_CHAIN,
          toHex(generateRandomByteArrayOfSize(randomGenerator, 32)),
          "decryption-key",
          KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
        )

        private val providerIdEncryptionResult = IcsEncryptionResult(
          generateRandomByteArrayOfSize(randomGenerator, 8),
          generateRandomByteArrayOfSize(randomGenerator, 12),
        )

        @BeforeEach
        fun setUp(): Unit = runBlocking {
          whenever(
            mockDatabaseService.findMostRecentUserSeedDataByUserAccountIdAndSeedUsageType(
              eq(userSeedData.userAccountId),
              eq(userSeedData.seedUsageType),
            )
          ).thenReturn(userSeedData)
          whenever(
            mockDatabaseService.findMostRecentUserSeedDataByUserAccountIdAndSeedUsageType(
              eq(contextItemSeedData.userAccountId),
              eq(contextItemSeedData.seedUsageType),
            )
          ).thenReturn(contextItemSeedData)
          whenever(
            mockDatabaseService.findMostRecentUserKeyDataByUserSeedDataId(
              eq(userSeedData.id!!),
            )
          ).thenReturn(icsUserKeyData)
          whenever(
            mockDatabaseService.findMostRecentUserDerivedKeyDataByUserSeedDataIdAndUsageType(
              eq(userSeedData.id!!),
              eq(DerivedKeyUsageType.ON_CHAIN),
            )
          ).thenReturn(onChainDerivedKeyData)

          whenever(
            mockKeyService.decryptData(
              argThat { toHex(this.value) == userSeedData.encryptedSeedHex })
          ).thenReturn(userSeed)
          whenever(
            mockKeyService.decryptData(
              argThat { toHex(this.value) == contextItemSeedData.encryptedSeedHex })
          ).thenReturn(contextItemSeed)
          whenever(
            mockKeyService.decryptUserIcsKeyData(
              argThat { this.publicKeyHex == icsUserKeyData.publicKeyHex })
          ).thenReturn(userIcsKeyPairBytes)
          whenever(
            mockKeyService.decryptData(
              argThat { toHex(this.value) == onChainDerivedKeyData.encryptedKeyHex })
          ).thenReturn(onChainSymmetricKey)

          // By default, the public key is not registered on chain
          whenever(
            mockIcsService.getIcsPublicKeys(eq(userMsaId))
          ).thenReturn(emptyList())
          whenever(
            mockIcsService.encrypt(
              eq(onChainSymmetricKey),
              any(),
            )
          ).thenReturn(providerIdEncryptionResult)

          // By default, no ACLs or metadata are registered on chain
          whenever(
            mockIcsService.getIcsContextGroupAcls(eq(userMsaId))
          ).thenReturn(emptyList())
        }

        @Test
        fun retrieveHcpUserPayloadsSendsBackPayloadsCorrectly(): Unit = runBlocking {
          // GIVEN
          val request = createRetrievePayloadsSignedRequest()
          val mockPayloads = mockIcsPayloadCalls(userIcsKeyPair)

          // WHEN
          val result = service.retrieveUserPayloads(request)

          // THEN
          Assertions.assertThat(result.userPublicKey.toPublicKeyBytes()).isEqualTo(userControlKeyPair.publicKeyBytes)
          Assertions.assertThat(result.userIcsPublicKey.toPublicKeyBytes()).isEqualTo(userIcsKeyPair.publicKey)
          Assertions.assertThat(result.payloads).hasSize(2)

          val publicKeyPayload = result.payloads.first {
            it.debugDescription == DebugDescription.HCP_PUBLIC_KEY_PAYLOAD
          }
          Assertions.assertThat(publicKeyPayload).usingRecursiveComparison()
            .isEqualTo(mockPayloads.publicKeyRegistration)

          val aclPayload = result.payloads.first {
            it.debugDescription == DebugDescription.HCP_ACL_PAYLOAD
          }
          Assertions.assertThat(aclPayload).usingRecursiveComparison()
            .isEqualTo(mockPayloads.contextGroupAcl)
        }

        @Nested
        inner class AndIcsPublicKeyOnChain {

          @BeforeEach
          fun setUp(): Unit = runBlocking {
            // ICS public key *is* registered on chain
            whenever(
              mockIcsService.getIcsPublicKeys(eq(userMsaId))
            ).thenReturn(listOf(IndexedValue(0, userIcsKeyPair.toPublicKey())))
          }

          @Test
          fun retrieveHcpUserPayloadsSendsBackPayloadsCorrectly(): Unit = runBlocking {
            // GIVEN
            val request = createRetrievePayloadsSignedRequest()
            val mockPayloads = mockIcsPayloadCalls(userIcsKeyPair)

            // WHEN
            val result = service.retrieveUserPayloads(request)

            // THEN
            Assertions.assertThat(result.userPublicKey.toPublicKeyBytes()).isEqualTo(userControlKeyPair.publicKeyBytes)
            // Expect NOT to get public key payload back
            Assertions.assertThat(result.payloads).hasSize(1)

            val aclPayload = result.payloads.first {
              it.debugDescription == DebugDescription.HCP_ACL_PAYLOAD
            }
            Assertions.assertThat(aclPayload).usingRecursiveComparison()
              .isEqualTo(mockPayloads.contextGroupAcl)
          }

          @Test
          fun getIcsContextGroupKeyThrows(): Unit = runBlocking {
            // GIVEN
            val request = createGetContextGroupKeySignedRequest()

            // WHEN / THEN
            Assertions.assertThatThrownBy {
              runBlocking {
                service.retrieveContextGroupKey(request)
              }
            }.isInstanceOf(ApiException::class.java)
              .hasFieldOrPropertyWithValue("apiError", ApiError.ICS_ACL_CHECK_ERROR)

          }

          @Nested
          inner class AndContextGroupAcl {

            private lateinit var encryptedProviderMsaId: IcsEncryptionResult

            @BeforeEach
            fun setUp(): Unit = runBlocking {
              encryptedProviderMsaId = IcsEncryptionResult(
                generateRandomByteArrayOfSize(randomGenerator, 8),
                generateRandomByteArrayOfSize(randomGenerator, 12),
              )

              whenever(
                mockIcsService.getIcsContextGroupAcls(eq(userMsaId))
              ).thenReturn(
                listOf(
                  IndexedValue(
                    1,
                    ContextGroupAcl(
                      contextGroupId,
                      1,
                      encryptedProviderMsaId.nonce,
                      encryptedProviderMsaId.data,
                    )
                  )
                )
              )

              whenever(
                mockIcsService.decryptProviderMsaId(
                  argThat {
                    toHex(this) == toHex(onChainSymmetricKey)
                  },
                  argThat {
                    this.data.contentEquals(encryptedProviderMsaId.data) &&
                        this.nonce.contentEquals(encryptedProviderMsaId.nonce)
                  }
                )
              ).thenReturn(providerMsaId)
            }

            @Test
            fun getIcsContextGroupKeySucceeds(): Unit = runBlocking {
              // GIVEN
              val request = createGetContextGroupKeySignedRequest()

              val contextGroupKey = generateRandomByteArrayOfSize(randomGenerator, 32)
              val derivationPath = "context-group/path"
              whenever(
                mockIcsService.deriveContextGroupSymmetricKey(any(), any())
              ).thenReturn(Derived(contextGroupKey, derivationPath))

              val encryptedKey = EncryptedKey(
                generateRandomByteArrayOfSize(randomGenerator, 32),
                KmsDecryptionKey(
                  "decryption-key",
                  KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
                )
              )
              whenever(
                mockKeyService.encryptPrivateKey(
                  eq(contextGroupKey),
                )
              ).thenReturn(encryptedKey)

              // WHEN
              val result = service.retrieveContextGroupKey(request)

              // THEN
              Assertions.assertThat(result.contextGroupSymmetricKey.derivationPath).isEqualTo(derivationPath)
              Assertions.assertThat(result.contextGroupSymmetricKey.encodedKeyValue).isEqualTo(toHex(contextGroupKey))
              Assertions.assertThat(result.contextGroupSymmetricKey.encoding).isEqualTo(Encoding.HEX)

              verify(mockDatabaseService, times(1)).saveUserDerivedKeyData(
                argThat { udkd ->
                  Assertions.assertThat(udkd.userSeedDataId).isEqualTo(userSeedDataId)
                  Assertions.assertThat(udkd.encryptedKeyHex).isEqualTo(toHex(encryptedKey.encryptedValue))
                  Assertions.assertThat(udkd.derivedKeyUsageType).isEqualTo(DerivedKeyUsageType.CONTEXT_GROUP)

                  true
                }
              )
            }

            @Test
            fun getIcsContextItemKeySucceeds(): Unit = runBlocking {
              // GIVEN
              val request = createGetContextItemKeySignedRequest()

              val contextItemKey = generateRandomByteArrayOfSize(randomGenerator, 32)
              val derivationPath = "foo-bar"
              whenever(
                mockIcsService.deriveContextItemSymmetricKey(any(), eq(request.payload.contextItemId), any())
              ).thenReturn(Derived(contextItemKey, derivationPath))

              val encryptedKey = EncryptedKey(
                generateRandomByteArrayOfSize(randomGenerator, 32),
                KmsDecryptionKey(
                  "decryption-key",
                  KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
                )
              )
              whenever(
                mockKeyService.encryptPrivateKey(
                  eq(contextItemKey),
                )
              ).thenReturn(encryptedKey)

              // WHEN
              val result = service.retrieveContextItemKey(request)

              // THEN
              Assertions.assertThat(result.contextItemSymmetricKey.derivationPath).isEqualTo(derivationPath)
              Assertions.assertThat(result.contextItemSymmetricKey.encodedKeyValue).isEqualTo(toHex(contextItemKey))
              Assertions.assertThat(result.contextItemSymmetricKey.encoding).isEqualTo(Encoding.HEX)

              verify(mockDatabaseService, times(1)).saveUserDerivedKeyData(
                argThat { udkd ->
                  Assertions.assertThat(udkd.userSeedDataId).isEqualTo(userSeedDataId)
                  Assertions.assertThat(udkd.encryptedKeyHex).isEqualTo(toHex(encryptedKey.encryptedValue))
                  Assertions.assertThat(udkd.derivedKeyUsageType).isEqualTo(DerivedKeyUsageType.CONTEXT_ITEM)

                  true
                }
              )
            }

            @Test
            fun getIcsContextItemKeySucceedsReRequest(): Unit = runBlocking {
              // GIVEN
              val reRequestEnabled = DefaultIcsUserOrchestrationProperties(
                10,
                "https://example.com",
                true,
              )

              val serviceWithReRequestEnabled = DefaultIcsUserOrchestrationService(
                reRequestEnabled,
                mockLookupService,
                mockSigningOrchestrationService,
                mockKeyService,
                mockIcsService,
                sS58AddressFormat,
                mockIcsWhitelistService,
                mockDatabaseService,
                delegatingTransactionalOperator,
              )
              val request = createGetContextItemKeySignedRequest()

              val contextItemKey = generateRandomByteArrayOfSize(randomGenerator, 32)
              val derivationPath = String.format(
                DefaultIcsUserOrchestrationService.CONTEXT_ITEM_KEY_DERIVATION_PATH_PREFIX_TEMPLATE,
                "foo-bar"
              )
              whenever(
                mockIcsService.deriveContextItemSymmetricKey(any(), eq(request.payload.contextItemId), any())
              ).thenReturn(Derived(contextItemKey, derivationPath))

              val encryptedKey = EncryptedKey(
                generateRandomByteArrayOfSize(randomGenerator, 32),
                KmsDecryptionKey(
                  "decryption-key",
                  KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
                )
              )

              val now = Instant.now().toEpochMilli().toBigInteger()
              val contextItemUserDerivedKey = UserDerivedKeyData(
                1.toBigInteger(),
                userSeedDataId,
                derivationPath,
                DerivedKeyUsageType.CONTEXT_ITEM,
                toHex(encryptedKey.encryptedValue),
                encryptedKey.kmsDecryptionKey.decryptionKeyId,
                encryptedKey.kmsDecryptionKey.decryptionAlgorithm,
                now,
                now,
                1.toBigInteger()
              )
              val encryptedData = EncryptedData(
                fromHex(contextItemUserDerivedKey.encryptedKeyHex),
                KmsDecryptionKey(
                  contextItemUserDerivedKey.kmsEncryptionKeyId,
                  contextItemUserDerivedKey.kmsEncryptionAlgorithm
                ),
              )

              whenever(mockKeyService.decryptData(encryptedData)).thenReturn(contextItemKey)
              whenever(
                mockDatabaseService.findMostRecentUserDerivedKeyDataByDerivationPathPrefixed(
                  String.format(
                    DefaultIcsUserOrchestrationService.CONTEXT_ITEM_KEY_DERIVATION_PATH_PREFIX_TEMPLATE,
                    request.payload.contextItemId
                  )
                )
              ).thenReturn(contextItemUserDerivedKey)

              // WHEN
              val result = serviceWithReRequestEnabled.retrieveContextItemKey(request)

              // THEN
              Assertions.assertThat(result.contextItemSymmetricKey.derivationPath).isEqualTo(derivationPath)
              Assertions.assertThat(result.contextItemSymmetricKey.encodedKeyValue).isEqualTo(toHex(contextItemKey))
              Assertions.assertThat(result.contextItemSymmetricKey.encoding).isEqualTo(Encoding.HEX)
            }

            @Test
            fun getIcsContextItemKeySucceedsWithReRequestEnabled(): Unit = runBlocking {
              // GIVEN
              val reRequestEnabled = DefaultIcsUserOrchestrationProperties(
                10,
                "https://example.com",
                true,
              )
              val serviceWithReRequestEnabled = DefaultIcsUserOrchestrationService(
                reRequestEnabled,
                mockLookupService,
                mockSigningOrchestrationService,
                mockKeyService,
                mockIcsService,
                sS58AddressFormat,
                mockIcsWhitelistService,
                mockDatabaseService,
                delegatingTransactionalOperator,
              )
              val request = createGetContextItemKeySignedRequest()

              val contextItemKey = generateRandomByteArrayOfSize(randomGenerator, 32)
              val derivationPath = "foo-bar"
              whenever(
                mockIcsService.deriveContextItemSymmetricKey(any(), eq(request.payload.contextItemId), any())
              ).thenReturn(Derived(contextItemKey, derivationPath))

              val encryptedKey = EncryptedKey(
                generateRandomByteArrayOfSize(randomGenerator, 32),
                KmsDecryptionKey(
                  "decryption-key",
                  KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
                )
              )
              whenever(
                mockKeyService.encryptPrivateKey(
                  eq(contextItemKey),
                )
              ).thenReturn(encryptedKey)

              // WHEN
              val result = serviceWithReRequestEnabled.retrieveContextItemKey(request)

              // THEN
              Assertions.assertThat(result.contextItemSymmetricKey.derivationPath).isEqualTo(derivationPath)
              Assertions.assertThat(result.contextItemSymmetricKey.encodedKeyValue).isEqualTo(toHex(contextItemKey))
              Assertions.assertThat(result.contextItemSymmetricKey.encoding).isEqualTo(Encoding.HEX)

              verify(mockDatabaseService, times(1)).saveUserDerivedKeyData(
                argThat { udkd ->
                  Assertions.assertThat(udkd.userSeedDataId).isEqualTo(userSeedDataId)
                  Assertions.assertThat(udkd.encryptedKeyHex).isEqualTo(toHex(encryptedKey.encryptedValue))
                  Assertions.assertThat(udkd.derivedKeyUsageType).isEqualTo(DerivedKeyUsageType.CONTEXT_ITEM)

                  true
                }
              )
            }
          }
        }
      }

      @Nested
      inner class WithoutAnIcsKeyPair {

        private val mnemonicPhrase = "airplane banana croissant dinosaur eucalyptus frost"
        private val masterSeed = generateRandomByteArrayOfSize(randomGenerator, 32)

        private val encryptedMnemonic = EncryptedData(
          generateRandomByteArrayOfSize(randomGenerator, 32),
          KmsDecryptionKey("decryption-key", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT),
        )
        private val encryptedSeed = EncryptedData(
          generateRandomByteArrayOfSize(randomGenerator, 32),
          KmsDecryptionKey("decryption-key", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT),
        )

        private val userSeedData = UserSeedData.create(
          userAccountId,
          SeedUsageType.HCP_MASTER,
          toHex(encryptedMnemonic.value),
          toHex(encryptedSeed.value),
          "decryption-key",
          KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
        ).apply { this.id = 94.toBigInteger() }

        private val contextItemSeedData = UserSeedData.create(
          userAccountId,
          SeedUsageType.CONTEXT_ITEM_MASTER,
          toHex(encryptedMnemonic.value),
          toHex(encryptedSeed.value),
          "decryption-key",
          KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
        ).apply { this.id = 901.toBigInteger() }

        private val userIcsKeyPair = IcsKeyPair(
          generateRandomByteArrayOfSize(randomGenerator, 32),
          generateRandomByteArrayOfSize(randomGenerator, 64),
          IcsKeyType.ED25519,
        )
        private val usrIcsEncryptedPrivateKey = EncryptedKey(
          generateRandomByteArrayOfSize(randomGenerator, 64),
          KmsDecryptionKey("decryption-key", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT),
        )
        private val icsUserKeyData = UserKeyData.create(
          userAccountId,
          userIcsKeyPair.publicKey,
          usrIcsEncryptedPrivateKey,
          KeyPairType.ED25519,
          KeyUsageType.ICS,
          userSeedData.id!!
        ).apply { this.id = 45.toBigInteger() }

        private val onChainSymmetricKey = generateRandomByteArrayOfSize(randomGenerator, 32)
        private val onChainUserDerivedKeyData = UserDerivedKeyData.create(
          userSeedData.id!!,
          "derivation/path",
          DerivedKeyUsageType.ON_CHAIN,
          toHex(generateRandomByteArrayOfSize(randomGenerator, 32)),
          "decryption-key",
          KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
        )

        private val providerIdEncryptionResult = IcsEncryptionResult(
          generateRandomByteArrayOfSize(randomGenerator, 8),
          generateRandomByteArrayOfSize(randomGenerator, 12),
        )

        @BeforeEach
        fun setUp(): Unit = runBlocking {
          whenever(
            mockLookupService.findUserKeyData(
              eq(userAccountId), eq(KeyUsageType.ICS), any()
            )
          ).thenReturn(null)

          whenever(
            mockIcsService.generateMasterMnemonicSeedPhrase()
          ).thenReturn(mnemonicPhrase)
          whenever(
            mockIcsService.deriveMasterSeed(eq(mnemonicPhrase))
          ).thenReturn(masterSeed)

          // No other ICS payloads registered on-chain
          whenever(
            mockIcsService.getIcsContextGroupAcls(eq(userMsaId))
          ).thenReturn(emptyList())

          whenever(
            mockKeyService.encryptData(eq(mnemonicPhrase.encodeToByteArray()))
          ).thenReturn(encryptedMnemonic)
          whenever(
            mockKeyService.encryptData(eq(masterSeed))
          ).thenReturn(encryptedSeed)
          whenever(
            mockDatabaseService.saveUserSeedData(argThat { usd ->
              usd?.userAccountId == userAccountId &&
                      usd.seedUsageType == userSeedData.seedUsageType &&
                      usd.encryptedSeedHex == userSeedData.encryptedSeedHex &&
                      usd.encryptedSeedPhraseHex == userSeedData.encryptedSeedPhraseHex
            })
          ).thenReturn(userSeedData)
          whenever(
            mockDatabaseService.saveUserSeedData(argThat { usd ->
              usd?.userAccountId == userAccountId &&
                      usd.seedUsageType == contextItemSeedData.seedUsageType &&
                      usd.encryptedSeedHex == contextItemSeedData.encryptedSeedHex &&
                      usd.encryptedSeedPhraseHex == contextItemSeedData.encryptedSeedPhraseHex
            })
          ).thenReturn(contextItemSeedData)

          whenever(
            mockIcsService.deriveMasterKeyPair(eq(masterSeed))
          ).thenReturn(userIcsKeyPair)
          whenever(
            mockKeyService.encryptPrivateKey(eq(userIcsKeyPair.privateKey))
          ).thenReturn(usrIcsEncryptedPrivateKey)
          whenever(
            mockDatabaseService.saveUserKeyData(argThat { ukd ->
              ukd.userAccountId == icsUserKeyData.userAccountId &&
                      ukd.publicKeyHex == icsUserKeyData.publicKeyHex &&
                      ukd.encryptedPrivateKeyHex == icsUserKeyData.encryptedPrivateKeyHex &&
                      ukd.keyUsageType == icsUserKeyData.keyUsageType &&
                      ukd.userSeedDataId == icsUserKeyData.userSeedDataId
            })
          ).thenReturn(icsUserKeyData.id!!)

          whenever(
            mockIcsService.getIcsPublicKeys(eq(userMsaId))
          ).thenReturn(emptyList())

          whenever(
            mockIcsService.deriveUserChainDataSymmetricKey(eq(masterSeed))
          ).thenReturn(Derived(onChainSymmetricKey, onChainUserDerivedKeyData.derivationPath))
          whenever(
            mockKeyService.encryptPrivateKey(
              eq(onChainSymmetricKey),
            )
          ).thenReturn(
            EncryptedKey(
              fromHex(onChainUserDerivedKeyData.encryptedKeyHex),
              KmsDecryptionKey(
                onChainUserDerivedKeyData.kmsEncryptionKeyId,
                onChainUserDerivedKeyData.kmsEncryptionAlgorithm
              )
            )
          )

          whenever(
            mockIcsService.encrypt(
              eq(onChainSymmetricKey),
              any(),
            )
          ).thenReturn(providerIdEncryptionResult)
        }

        @Test
        fun retrieveHcpUserPayloadsSucceeds(): Unit = runBlocking {
          // GIVEN
          val request = createRetrievePayloadsSignedRequest()
          val mockPayloads = mockIcsPayloadCalls(userIcsKeyPair)

          // WHEN
          val result = service.retrieveUserPayloads(request)

          // THEN
          val captor = argumentCaptor<UserSeedData>()
          verify(mockDatabaseService, times(2)).saveUserSeedData(captor.capture())
          Assertions.assertThat(captor.allValues.map { it.encryptedSeedHex }).containsExactlyInAnyOrder(
            userSeedData.encryptedSeedHex,
            contextItemSeedData.encryptedSeedHex
          )

          verify(mockDatabaseService, times(1)).saveUserKeyData(argThat { ukd ->
            ukd.encryptedPrivateKeyHex == icsUserKeyData.encryptedPrivateKeyHex
          })
          verify(mockDatabaseService, times(1)).saveUserDerivedKeyData(argThat { udkd ->
            udkd.encryptedKeyHex == onChainUserDerivedKeyData.encryptedKeyHex
          })

          Assertions.assertThat(result.userPublicKey.toPublicKeyBytes()).isEqualTo(userControlKeyPair.publicKeyBytes)
          Assertions.assertThat(result.userIcsPublicKey.toPublicKeyBytes()).isEqualTo(userIcsKeyPair.publicKey)
          Assertions.assertThat(result.payloads).hasSize(2)

          val publicKeyPayload = result.payloads.first {
            it.debugDescription == DebugDescription.HCP_PUBLIC_KEY_PAYLOAD
          }
          Assertions.assertThat(publicKeyPayload).usingRecursiveComparison()
            .isEqualTo(mockPayloads.publicKeyRegistration)

          val aclPayload = result.payloads.first {
            it.debugDescription == DebugDescription.HCP_ACL_PAYLOAD
          }
          Assertions.assertThat(aclPayload).usingRecursiveComparison()
            .isEqualTo(mockPayloads.contextGroupAcl)
        }
      }
    }
  }

  @ParameterizedTest
  @CsvSource(
    value = [
      "  |   | false | 0", // Nothing registered on chain
      "0 | 0 | true  | 0", // Current key is registered on chain
      "3 | 1 | true  | 1", // Multiple keys including the current are registered
      "2 |   | false | 3", // Multiple keys excluding the current are registered
    ],
    delimiter = '|',
  )
  fun determineIcsPublicKeyState(
    maxRegisteredKeyId: Int?,
    currentPublicKeyId: Int?,
    expectedStateIsRegistered: Boolean,
    expectedPublicKeyId: Int,
  ): Unit = runBlocking {
    // GIVEN
    val userMsaId = 82.toBigInteger()
    val currentPublicKey = IcsPublicKey(
      generateRandomByteArrayOfSize(randomGenerator, 32),
      IcsKeyType.ED25519,
    )

    val registeredKeys = if (maxRegisteredKeyId != null) {
      (0..maxRegisteredKeyId).map { keyId ->
        val key = when {
          keyId == currentPublicKeyId -> currentPublicKey.publicKey
          else -> generateRandomByteArrayOfSize(randomGenerator, 32)
        }
        IndexedValue(keyId, IcsPublicKey(key, IcsKeyType.ED25519))
      }.shuffled()
    } else emptyList()

    whenever(
      mockIcsService.getIcsPublicKeys(eq(userMsaId))
    ).thenReturn(registeredKeys)

    // WHEN
    val state = service.determineIcsPublicKeyState(userMsaId, currentPublicKey)

    // THEN
    when (state) {
      is IcsPublicKeyState.Registered -> {
        Assertions.assertThat(true).isEqualTo(expectedStateIsRegistered)
        Assertions.assertThat(state.keyId).isEqualTo(expectedPublicKeyId)
      }

      is IcsPublicKeyState.Unregistered -> {
        Assertions.assertThat(false).isEqualTo(expectedStateIsRegistered)
        Assertions.assertThat(state.nextAvailableKeyId).isEqualTo(expectedPublicKeyId)
      }
    }
  }

}
