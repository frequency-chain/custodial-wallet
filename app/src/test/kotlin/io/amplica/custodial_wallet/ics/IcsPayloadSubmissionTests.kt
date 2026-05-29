package io.amplica.custodial_wallet.ics

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.amplica.custodial_wallet.container.FrequencyTestContainer
import io.amplica.custodial_wallet.container.FrequencyVersion
import io.amplica.custodial_wallet.service.ics.generateRandomByteArrayOfSize
import io.amplica.custodial_wallet.util.fromHex
import io.amplica.frequency.client.*
import io.amplica.frequency.crypto.AccountKeyPair
import io.amplica.frequency.crypto.provider.Sr25519CryptoProvider
import io.amplica.frequency.crypto.toUniversalAddress
import io.amplica.frequency.payload.AddItemAction
import io.amplica.frequency.payload.AddProviderPayload
import io.amplica.frequency.payload.ItemizedSignaturePayloadV2
import io.amplica.frequency.payload.PaginatedUpsertSignaturePayloadV2
import io.amplica.frequency.serialization.Environment
import io.amplica.frequency.serialization.JacksonBasedEip712ObjectMapper
import io.amplica.frequency.serialization.SubstrateScaleObjectMapper
import io.amplica.frequency.service.DefaultSigningService
import io.amplica.frequency.util.arrow.getOrThrow
import io.projectliberty.icssdk.Ics
import io.projectliberty.icssdk.keys.IcsKeyType
import io.projectliberty.icssdk.storages.IcsContextGroupAcl
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.shaded.org.bouncycastle.util.BigIntegers
import java.util.random.RandomGenerator


@Testcontainers
class IcsPayloadSubmissionTests {

  companion object {
    @Container
    val frequency = FrequencyTestContainer(FrequencyVersion.CURRENT)

    @DynamicPropertySource
    @JvmStatic
    fun registerContainerProperties(registry: DynamicPropertyRegistry) {
      frequency.registerDynamicProperties(registry)
    }
  }

  private val signingService = DefaultSigningService(
    SubstrateScaleObjectMapper(
      listOf("io.amplica.frequency.signing_service")
    ),
    JacksonBasedEip712ObjectMapper(
      jacksonObjectMapper(),
      Environment.TEST
    )
  )

  @Nested
  inner class WithDelegatedUser {

    private val randomGenerator = RandomGenerator.getDefault()

    lateinit var userKeyPair: AccountKeyPair
    lateinit var userMsaId: MessageSourceId

    @BeforeEach
    fun beforeEach() = runBlocking {
      val seed = generateRandomByteArrayOfSize(randomGenerator, 32)
      userKeyPair = Sr25519CryptoProvider.createKeyPairFromSeed(seed)

      val addProviderPayload = AddProviderPayload(
        frequency.aliceProviderMsaId,
        emptyList(),
        100L
      )
      val addProviderSignature = signingService.signPayload(userKeyPair, addProviderPayload)

      val msaCreated = frequency.aliceProviderClient.createSponsoredAccountWithDelegationWithCapacity(
        userKeyPair.toUniversalAddress(),
        SpRuntimeMultiSignatureType.SR25519,
        addProviderSignature.bytes,
        addProviderPayload.toScaleObject()
      ).await().getOrThrow()

      userMsaId = msaCreated.delegator!!
    }

    private fun submitAddItemToItemizedStoragePayload(
      intentName: String,
      payload: ByteArray,
    ): ItemizedPageUpdated = runBlocking {
      val schemaId = frequency.aliceProviderClient.getLatestSchemaIdByIntentName(intentName).await().getOrThrow()

      val latestBlockNumber = frequency.aliceProviderClient.getLastBlockNumber().await().toLong()
      val expiration = latestBlockNumber + 100

      val itemizedPayload = ItemizedSignaturePayloadV2(
        schemaId.value,
        0.toBigInteger(),
        expiration,
        listOf(
          AddItemAction(payload),
        )
      )

      val signature = signingService.signPayload(userKeyPair, itemizedPayload)

      frequency.aliceProviderClient.createApplyItemActionsWithSignatureV2(
        userKeyPair.toUniversalAddress(),
        SpRuntimeMultiSignatureType.SR25519,
        signature.bytes,
        itemizedPayload.toScaleObject()
      ).await().getOrThrow()
    }

    private fun submitPaginatedStoragePayload(
      intentName: String,
      pageId: Int,
      payload: ByteArray,
    ): PaginatedPageUpdated = runBlocking {
      val schemaId = frequency.aliceProviderClient.getLatestSchemaIdByIntentName(intentName).await().getOrThrow()

      val latestBlockNumber = frequency.aliceProviderClient.getLastBlockNumber().await().toLong()
      val expiration = latestBlockNumber + 100

      val paginatedPayload = PaginatedUpsertSignaturePayloadV2(
        schemaId.value,
        pageId,
        0.toBigInteger(),
        expiration,
        payload
      )

      val signature = signingService.signPayload(userKeyPair, paginatedPayload)

      frequency.aliceProviderClient.upsertPageWithSignatureV2(
        userKeyPair.toUniversalAddress(),
        SpRuntimeMultiSignatureType.SR25519,
        signature.bytes,
        paginatedPayload.toScaleObject()
      ).await().getOrThrow()
    }

    @Test
    fun submitsIcsPublicKeyKeyAgreementItemizedStorage(): Unit = runBlocking {
      // GIVEN
      val intentName = "ics.public-key-key-agreement"
      val intentId = IntentId(21)

      val mnemonic = Ics.generateUserMasterMnemonic()
      val icsKeyPair = Ics.deriveUserMasterKeyPair(Ics.deriveUserMasterSeed(mnemonic), IcsKeyType.X25519)
      val payload = Ics.serializePublicKey(icsKeyPair.publicKey)

      // WHEN
      val result = submitAddItemToItemizedStoragePayload(intentName, payload)

      // THEN
      Assertions.assertThat(result.msaId).isEqualTo(userMsaId)
      Assertions.assertThat(result.intentId).isEqualTo(intentId)
      Assertions.assertThat(result.previousHash.value).isEqualTo(0L)
    }

    @Test
    fun submitsContextGroupAclItemizedStorage(): Unit = runBlocking {
      // GIVEN
      val intentName = "ics.context-group-acl"
      val intentId = IntentId(22)

      val userIcsKeyPair = Ics.deriveUserMasterKeyPair(
        Ics.deriveUserMasterSeed(Ics.generateUserMasterMnemonic()),
        IcsKeyType.X25519,
      )
      val userChainDataKey = Ics.deriveUserChainDataKey(Ics.deriveUserMasterSeed(Ics.generateUserMasterMnemonic()))
      val providerIcsKeyPair = Ics.deriveUserMasterKeyPair(
        Ics.deriveUserMasterSeed(Ics.generateUserMasterMnemonic()),
        IcsKeyType.X25519,
      )

      val contextGroupIdHex = Ics.deriveContextGroupIdHex(
        userMsaId.value.toLong(),
        frequency.aliceProviderMsaId.toLong(),
        userIcsKeyPair.secretKey,
        providerIcsKeyPair.publicKey
      )
      val nonce = fromHex("0x123412341234123412341234")
      val encryptedData = Ics.encrypt(
        userChainDataKey.key,
        BigIntegers.asUnsignedByteArray(8, frequency.aliceProviderMsaId)
      ).encryptedData

      val acl = IcsContextGroupAcl(
        contextGroupIdHex,
        0,
        nonce,
        encryptedData,
      )
      val payload = Ics.serializeContextGroupAcl(acl)

      // WHEN
      val result = submitAddItemToItemizedStoragePayload(intentName, payload)

      // THEN
      Assertions.assertThat(result.msaId).isEqualTo(userMsaId)
      Assertions.assertThat(result.intentId).isEqualTo(intentId)
      Assertions.assertThat(result.previousHash.value).isEqualTo(0L)
    }
  }

}
