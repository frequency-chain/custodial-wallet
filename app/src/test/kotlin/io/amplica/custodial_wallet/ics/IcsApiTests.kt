package io.amplica.custodial_wallet.ics

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.EncryptedKeyData
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.container.CustodialWalletE2ETestStack
import io.amplica.custodial_wallet.container.FrequencyTestProvider
import io.amplica.custodial_wallet.db.repository.KeyUsageType
import io.amplica.custodial_wallet.db.repository.UserDetail
import io.amplica.custodial_wallet.db.repository.UserDetailType
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiErrorDto
import io.amplica.custodial_wallet.orchestration.payload.IcsContextItemKeyRequest
import io.amplica.custodial_wallet.orchestration.payload.IcsMsaIdRequest
import io.amplica.custodial_wallet.orchestration.signing.SigningOrchestrationService
import io.amplica.custodial_wallet.service.ics.JavaSdkIcsService
import io.amplica.custodial_wallet.service.key.KeyService
import io.amplica.custodial_wallet.util.*
import io.amplica.custodial_wallet.util.key_creation.Encoding
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto
import io.amplica.custodial_wallet.util.key_creation.Sr25519KeyPairBytes
import io.amplica.frequency.client.FrequencyClient
import io.amplica.frequency.client.SpRuntimeMultiSignatureType
import io.amplica.frequency.crypto.KeyPair
import io.amplica.frequency.crypto.provider.Sr25519CryptoProvider
import io.amplica.frequency.crypto.toUniversalAddress
import io.amplica.frequency.payload.AddItemAction
import io.amplica.frequency.payload.AddProviderPayload
import io.amplica.frequency.payload.ItemizedSignaturePayloadV2
import io.amplica.frequency.serialization.Environment
import io.amplica.frequency.serialization.JacksonBasedEip712ObjectMapper
import io.amplica.frequency.serialization.SubstrateScaleObjectMapper
import io.amplica.frequency.service.DefaultSigningService
import io.amplica.frequency.util.arrow.getOrThrow
import io.projectliberty.icssdk.Ics
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.postForEntity
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import java.math.BigInteger
import java.util.*

@CustodialWalletE2ESpringTestConfiguration
@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
class IcsApiTests {
  companion object {

    object SchemaIds {
      const val PUBLIC_KEY_REGISTRATION = 21
      const val CONTEXT_GROUP_ACL = 22
    }

    @Container
    val containers = CustodialWalletE2ETestStack()

    private lateinit var providerKeyPair: KeyPair<Sr25519CryptoProvider>
    private lateinit var providerKeyPairWrapper: SubstrateOrAccountKeyPair
    private lateinit var icsProvider: FrequencyTestProvider
    private lateinit var providerPublicKeyDto: PublicKeyDto
    private lateinit var providerKeyPairBytes: Sr25519KeyPairBytes

    @DynamicPropertySource
    @JvmStatic
    fun registerContainerProperties(registry: DynamicPropertyRegistry) {
      containers.registerDynamicProperties(registry)
    }

    @JvmStatic
    @BeforeAll
    fun beforeAll(): Unit = runBlocking {
      providerKeyPair = Sr25519CryptoProvider.createKeyPair()
      providerKeyPairWrapper = SubstrateOrAccountKeyPair.SubstrateKeyPairWrapper(
        com.strategyobject.substrateclient.crypto.KeyPair.fromBytes(providerKeyPair.privateKeyBytes.bytes + providerKeyPair.publicKeyBytes.bytes)
      )
      providerPublicKeyDto = convertToPublicKeyDto(providerKeyPairWrapper)
      providerKeyPairBytes = Sr25519KeyPairBytes(
        providerKeyPair.publicKeyBytes.bytes,
        providerKeyPair.privateKeyBytes.bytes
      )
      icsProvider = containers.frequency.createProvider(
        providerKeyPair,
        "HCP Provider"
      )

      val mockFrequencyClient: FrequencyClient = mock()

      val icsService = JavaSdkIcsService(mockFrequencyClient)
      val providerIcsSeed = icsService.deriveMasterSeed(icsService.generateMasterMnemonicSeedPhrase())
      val providerIcsKeyPair = icsService.deriveMasterKeyPair(providerIcsSeed)

      val publicKeySchema = containers.frequency.aliceProviderClient
        .getLatestSchemaIdByIntentName("ics.public-key-key-agreement")
        .await()
        .getOrThrow()
      val expiration = containers.frequency.aliceProviderClient.getLastBlockNumber().await().toLong() + 100
      val payload = icsService.serializePublicKey(providerIcsKeyPair.publicKey)

      val itemizedPayload = ItemizedSignaturePayloadV2(
        publicKeySchema.value,
        0.toBigInteger(),
        expiration,
        listOf(
          AddItemAction(payload),
        )
      )

      val signingService = DefaultSigningService(
        SubstrateScaleObjectMapper(listOf("io.amplica.frequency.signing_service")),
        JacksonBasedEip712ObjectMapper(jacksonObjectMapper(), Environment.TEST)
      )

      val signature = signingService.signPayload(providerKeyPair, itemizedPayload)

      containers.frequency.aliceProviderClient.createApplyItemActionsWithSignatureV2(
        providerKeyPair.toUniversalAddress(),
        SpRuntimeMultiSignatureType.SR25519,
        signature.bytes,
        itemizedPayload.toScaleObject()
      ).await().getOrThrow()
    }
  }

  @Autowired
  private lateinit var testRestTemplate: TestRestTemplate

  @Autowired
  lateinit var databaseService: CustodialWalletDatabaseService

  @Autowired
  lateinit var keyService: KeyService

  @Autowired
  private lateinit var signingOrchestrationService: SigningOrchestrationService

  private lateinit var userMsaId: BigInteger

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    val userControlKeyPair = Sr25519CryptoProvider.createKeyPair()

    val encryptedControlPrivateKey = keyService.encryptPrivateKey(userControlKeyPair.privateKeyBytes.bytes)

    databaseService.saveNewUserData(
      icsProvider.msaId,
      UUID.randomUUID().toString(),
      listOf(
        UserDetail("fake-email@me.com", UserDetailType.EMAIL)
      ),
      listOf(
        EncryptedKeyData(
          userControlKeyPair.publicKeyBytes.bytes,
          encryptedControlPrivateKey,
          KeyPairType.SR25519,
          KeyUsageType.ACCOUNT,
        )
      )
    )

    val expiration = containers.frequency.aliceProviderClient.getLastBlockNumber().await().toLong() + 100
    val addProviderPayload = AddProviderPayload(
      icsProvider.msaId,
      emptyList(),
      expiration,
    )

    val signature = signingOrchestrationService.signPayload(
      Sr25519KeyPairBytes(
        userControlKeyPair.publicKeyBytes.bytes,
        userControlKeyPair.privateKeyBytes.bytes,
      ),
      addProviderPayload,
      Encoding.HEX,
    )

    val delegationsGranted = icsProvider.client.createSponsoredAccountWithDelegationWithCapacity(
      userControlKeyPair.toUniversalAddress(),
      SpRuntimeMultiSignatureType.SR25519,
      signature.toSignatureBytes(),
      addProviderPayload.toScaleObject(),
    ).await().getOrThrow()

    userMsaId = delegationsGranted.delegator!!.value

    // Wait for the off-chain index to catch up
    // TODO: Make a ticket to work with BC team on a fix
    delay(500)
  }

  private fun createRetrievePayloadsSignedRequest(): IcsRetrievePayloadsSignedRequest {
    val icsMsaIdRequest = IcsMsaIdRequest(userMsaId, UUID.randomUUID().toString())

    val signature = signingOrchestrationService.signPayload(
      providerKeyPairBytes,
      icsMsaIdRequest
    )

    val icsMsaIdRequestPayload = IcsMsaIdRequestPayload(
      icsMsaIdRequest.msaId.toString(),
      icsMsaIdRequest.nonce
    )

    return IcsRetrievePayloadsSignedRequest(
      providerPublicKeyDto,
      signature,
      icsMsaIdRequestPayload
    )
  }

  private fun retrieveUserPayloads(signedRequest: IcsRetrievePayloadsSignedRequest): IcsRetrievePayloadsResponse {
    val httpEntity = HttpEntity(signedRequest, createDefaultHttpHeaders())
    val retVal = testRestTemplate.postForEntity<IcsRetrievePayloadsResponse>(
      "/hcp/api/user/payloads",
      httpEntity,
    )

    Assertions.assertThat(retVal.statusCode).isEqualTo(HttpStatus.OK)
    return retVal.body
      ?: throw AssertionError("`retVal.body` must not be null")
  }

  @Test
  fun retrieveUserPayloadsSucceeds() = runBlocking {
    // GIVEN
    val signedRequest = createRetrievePayloadsSignedRequest()

    // WHEN
    val response = retrieveUserPayloads(signedRequest)

    // THEN
    Assertions.assertThat(response.userPublicKey.type).isEqualTo(KeyPairType.SR25519)
    Assertions.assertThat(response.userIcsPublicKey.type).isEqualTo(KeyPairType.ED25519)

    val signedPayloads = response.payloads
    val payloads = signedPayloads.map { it.payload }
    Assertions.assertThat(payloads).hasExactlyElementsOfTypes(
      ItemizedSignaturePayloadResponse::class.java,
      ItemizedSignaturePayloadResponse::class.java,
    )

    for (signedPayload in signedPayloads) {
      assertPayload(signedPayload)
    }

    submitPayloads(response)
  }

  @ParameterizedTest
  @CsvSource(
    value = [
      "true, false, false",
      "false, true, false",
      "false, false, true",
      "true, true, false",
      "false, true, true",
    ]
  )
  fun retrieveUserPayloadsReturnsOnlyNeededPayloads(
    publicKeySubmitted: Boolean,
    aclSubmitted: Boolean,
    metadataSubmitted: Boolean
  ) = runBlocking {
    // GIVEN
    val signedRequest = createRetrievePayloadsSignedRequest()

    val initialPayloadsResponse = retrieveUserPayloads(signedRequest)
    val trimmedPayloads = initialPayloadsResponse.copy(payloads = initialPayloadsResponse.payloads.filter {
        (it.debugDescription == DebugDescription.HCP_PUBLIC_KEY_PAYLOAD && publicKeySubmitted)
                || (it.debugDescription == DebugDescription.HCP_ACL_PAYLOAD && aclSubmitted)
    })

    submitPayloads(trimmedPayloads)

    // WHEN
    val response = retrieveUserPayloads(signedRequest)

    // THEN
    Assertions.assertThat(response.userPublicKey.type).isEqualTo(KeyPairType.SR25519)
    Assertions.assertThat(response.userIcsPublicKey.type).isEqualTo(KeyPairType.ED25519)

    val signedPayloads = response.payloads

    val hasPublicKeyPayload = signedPayloads.any { it.debugDescription == DebugDescription.HCP_PUBLIC_KEY_PAYLOAD }
    Assertions.assertThat(hasPublicKeyPayload).isEqualTo(!publicKeySubmitted)

    val hasAclPayload = signedPayloads.any { it.debugDescription == DebugDescription.HCP_ACL_PAYLOAD }
    Assertions.assertThat(hasAclPayload).isEqualTo(!aclSubmitted)

    for (signedPayload in signedPayloads) {
      assertPayload(signedPayload)
    }

    submitPayloads(response)
  }

  @Nested
  inner class WithRegisteredIcsPayloads {

    @BeforeEach
    fun setUp() = runBlocking {
      val signedRequest = createRetrievePayloadsSignedRequest()
      val response = retrieveUserPayloads(signedRequest)
      submitPayloads(response)
    }

    @Test
    fun retrieveContextGroupKey() {
      //GIVEN
      val scaleRequest = IcsMsaIdRequest(userMsaId, UUID.randomUUID().toString())

      val signature = signingOrchestrationService.signPayload(providerKeyPairBytes, scaleRequest)

      val hcpUserRequestPayload = IcsMsaIdRequestPayload(scaleRequest.msaId.toString(), scaleRequest.nonce)
      val icsContextGroupKeySignedRequest = IcsContextGroupKeySignedRequest(
        providerPublicKeyDto,
        signature,
        hcpUserRequestPayload,
      )

      //WHEN
      val httpEntity = HttpEntity(icsContextGroupKeySignedRequest, createDefaultHttpHeaders())
      val retVal =
        testRestTemplate.postForEntity("/hcp/api/key/contextGroup", httpEntity, IcsContextGroupKeyResponse::class.java)

      //THEN
      Assertions.assertThat(retVal.statusCode).isEqualTo(HttpStatus.OK)
      val body = retVal.body
      Assertions.assertThat(body).isNotNull
      if (body != null) {
        val contextGroupKey = body.contextGroupSymmetricKey
        Assertions.assertThat(contextGroupKey.encoding).isSameAs(Encoding.HEX)
        Assertions.assertThat(fromHex(contextGroupKey.encodedKeyValue)).isNotNull

        val derivationPath = contextGroupKey.derivationPath
        Assertions.assertThat(derivationPath).startsWith("off-chain|context-group|")

        val derivationPathTokens = derivationPath.split("|")
        Assertions.assertThat(derivationPathTokens.size).isEqualTo(3)

        fromHex(derivationPathTokens[2]) // Doesn't explode
      }
    }

    private fun createIcsContextItemKeySignedRequest(): IcsContextItemKeySignedRequest {
      val request = IcsContextItemKeyRequest(
        userMsaId,
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
      )

      val signature = signingOrchestrationService.signPayload(
        providerKeyPairBytes, request
      )
      val hcpContextItemKeyPayload = IcsContextItemKeyRequestPayload(
        request.msaId.toString(),
        request.contextItemId,
        request.nonce
      )
      return IcsContextItemKeySignedRequest(providerPublicKeyDto, signature, hcpContextItemKeyPayload)
    }

    private fun assertContextItemKeySuccess(icsContextItemKeySignedRequest: IcsContextItemKeySignedRequest, contextItemSymmetricKey: IcsSymmetricKey) {
      Assertions.assertThat(contextItemSymmetricKey.encoding).isSameAs(Encoding.HEX)
      Assertions.assertThat(fromHex(contextItemSymmetricKey.encodedKeyValue)).isNotNull
      val derivationPath = contextItemSymmetricKey.derivationPath
      Assertions.assertThat(derivationPath).startsWith("off-chain|context-item|")
      val derivationPathTokens = derivationPath.split("|")
      Assertions.assertThat(derivationPathTokens.size).isEqualTo(4)
      Assertions.assertThat(derivationPathTokens[2]).isEqualTo(icsContextItemKeySignedRequest.payload.contextItemId)
      UUID.fromString(derivationPathTokens[3]) // doesn't explode
    }

    @Test
    fun retrieveContextItemKey() {
      //GIVEN
      val icsContextItemKeySignedRequest = createIcsContextItemKeySignedRequest()

      //WHEN
      val httpEntity = HttpEntity(icsContextItemKeySignedRequest, createDefaultHttpHeaders())
      val retVal =
        testRestTemplate.postForEntity("/hcp/api/key/contextItem", httpEntity, IcsContextItemKeyResponse::class.java)

      //THEN
      Assertions.assertThat(retVal.statusCode).isEqualTo(HttpStatus.OK)
      val body = retVal.body
      Assertions.assertThat(body).isNotNull
      if (body != null) {
        val contextItemSymmetricKey = body.contextItemSymmetricKey
        assertContextItemKeySuccess(icsContextItemKeySignedRequest, contextItemSymmetricKey)
      }
    }

    @Test
    fun retrieveContextItemKeyTwiceIsForbidden() {
      //GIVEN
      val icsContextItemKeySignedRequest = createIcsContextItemKeySignedRequest()

      //WHEN REQUESTED ONCE
      val httpEntity = HttpEntity(icsContextItemKeySignedRequest, createDefaultHttpHeaders())
      val retVal =
        testRestTemplate.postForEntity("/hcp/api/key/contextItem", httpEntity, IcsContextItemKeyResponse::class.java)

      //THEN REQUESTED ONCE
      Assertions.assertThat(retVal.statusCode).isEqualTo(HttpStatus.OK)
      val body = retVal.body
      Assertions.assertThat(body).isNotNull
      if (body != null) {
        val contextItemSymmetricKey = body.contextItemSymmetricKey
        assertContextItemKeySuccess(icsContextItemKeySignedRequest, contextItemSymmetricKey)
      }

      //WHEN REQUESTED AGAIN
      val secondResponseEntity =
        testRestTemplate.postForEntity("/hcp/api/key/contextItem", httpEntity, ApiErrorDto::class.java)

      //THEN REQUESTED AGAIN
      Assertions.assertThat(secondResponseEntity.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
      val secondResponseBody = secondResponseEntity.body
      Assertions.assertThat(secondResponseBody).isNotNull
      if (secondResponseBody != null) {
        Assertions.assertThat(secondResponseBody.id).isEqualTo(ApiError.ICS_CONTEXT_ITEM_KEY_RESUBMISSION.id)
      }
    }
  }

  private fun assertPayload(signedPayload: TypedPayloadResponseWithSignature<*>) {
    val semanticType = signedPayload.debugDescription

    when (val payload = signedPayload.payload) {
      is AddProviderPayloadResponse -> {
        Assertions.assertThat(semanticType).isNull()

        Assertions.assertThat(payload.schemaIds).isEmpty()
        Assertions.assertThat(payload.authorizedMsaId).isEqualTo(icsProvider.msaId)
        Assertions.assertThat(payload.expiration).isGreaterThan(0L)
      }

      is ItemizedSignaturePayloadResponse -> {
        val actions = payload.actions
        Assertions.assertThat(actions).hasSize(1)
        val addItemAction = actions[0] as io.amplica.custodial_wallet.client.redis.dto.AddItemAction
        val payloadHex = addItemAction.payloadHex

        when (payload.schemaId) {
          SchemaIds.PUBLIC_KEY_REGISTRATION -> {
            Assertions.assertThat(semanticType).isEqualTo(DebugDescription.HCP_PUBLIC_KEY_PAYLOAD)

            // Parsing doesn't explode
            Ics.deserializePublicKey(fromHex(payloadHex))
          }

          SchemaIds.CONTEXT_GROUP_ACL -> {
            Assertions.assertThat(semanticType).isEqualTo(DebugDescription.HCP_ACL_PAYLOAD)

            // Parsing doesn't explode
            Ics.deserializeContextGroupAcl(fromHex(payloadHex))
          }
        }
      }

      else -> Assertions.fail("Don't know how to handle payload of type '${signedPayload.type}'")
    }
  }

  private suspend fun submitPayloads(hcpProvisionUserSignedResponse: IcsRetrievePayloadsResponse) {
    val userAccountId = base58DecodeAndExtractPublicKey(hcpProvisionUserSignedResponse.userPublicKey.encodedValue)

    for (payloadResponse in hcpProvisionUserSignedResponse.payloads) {
      when (val payload = payloadResponse.payload) {
        is ItemizedSignaturePayloadResponse -> {
          val frequencyPayload = ItemizedSignaturePayloadV2(
            payload.schemaId,
            payload.targetHash.toBigInteger(),
            payload.expiration,
            payload.actions.map { action ->
              val addAction = action as io.amplica.custodial_wallet.client.redis.dto.AddItemAction
              AddItemAction(fromHex(addAction.payloadHex))
            }
          )

          containers.frequency.aliceProviderClient.createApplyItemActionsWithSignatureV2(
            userAccountId,
            SpRuntimeMultiSignatureType.SR25519,
            fromHex(payloadResponse.signature.encodedValue),
            frequencyPayload.toScaleObject(),
          ).await().getOrThrow()
        }

        else -> Assertions.fail("Unexpected payload of type '${payload}'")
      }
    }
  }

}
