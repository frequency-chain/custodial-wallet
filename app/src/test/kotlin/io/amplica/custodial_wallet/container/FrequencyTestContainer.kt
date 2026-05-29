package io.amplica.custodial_wallet.container

import com.strategyobject.substrateclient.common.convert.HexConverter
import com.strategyobject.substrateclient.crypto.ss58.SS58AddressFormat
import com.strategyobject.substrateclient.transport.ws.ExponentialBackoffReconnectionPolicy
import io.amplica.custodial_wallet.util.fromHex
import io.amplica.custodial_wallet.util.key_creation.*
import io.amplica.frequency.client.FrequencyClient
import io.amplica.frequency.client.RetryingFrequencyClient
import io.amplica.frequency.client.SubstrateClientJavaFrequencyClient
import io.amplica.frequency.client.pallet.msa.CommonPrimitivesProviderRegistryEntry
import io.amplica.frequency.crypto.*
import io.amplica.frequency.crypto.provider.Sr25519CryptoProvider
import io.amplica.frequency.util.arrow.getOrThrow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.math.BigInteger
import java.time.Duration
import java.time.temporal.ChronoUnit
import com.strategyobject.substrateclient.crypto.KeyPair as SubstrateClientKeyPair

data class FrequencyTestProvider(val client: FrequencyClient, val msaId: BigInteger)

object FrequencyVersion {
  const val CURRENT = "v2.0.1"
}

class FrequencyTestContainer(version: String?) : GenericContainer<FrequencyTestContainer?>("$IMAGE:$version"),
  CustodialWalletTestContainer {
  companion object {
    private const val IMAGE = "frequencychain/standalone-node"
    private val LOG = LoggerFactory.getLogger(FrequencyTestContainer::class.java)
  }

  val aliceKeyPair: SubstrateClientKeyPair = SubstrateClientKeyPair.fromBytes(
    HexConverter.toBytes(
      "0x98319d4ff8a9508c4bb0cf0b5a78d760a0b2082c02775e6e82370816fedfff48" +
            "925a225d97aa00682d6a59b95b18780c10d7032336e88f3442b42361f4a66011" +
            "d43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d"
    )
  )

  val aliceAccountKeyPair: KeyPair<Sr25519CryptoProvider> = KeyPair(
    fromHex("0xd43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d")
      .toPublicKeyBytes(),
    fromHex("0x98319d4ff8a9508c4bb0cf0b5a78d760a0b2082c02775e6e82370816fedfff48925a225d97aa00682d6a59b95b18780c10d7032336e88f3442b42361f4a66011")
      .toPrivateKeyBytes(),
    Sr25519CryptoProvider
  )

  val aliceAccountId = aliceAccountKeyPair.toUniversalAddress()
  val alicePublicKeyBytes = aliceAccountKeyPair.publicKeyBytes


  //NOTE (Aziz 8/25/25): Keeping the old public key dto and encoded objects for compatibility with application tests
  val alicePublicKeyEncoded = Sr25519KeyPairCreator.encodeSr25519PublicKey(alicePublicKeyBytes.bytes, SS58AddressFormat.BARE_SR_25519)
  val aliceProviderPublicKeyDto =
    PublicKeyDto(alicePublicKeyEncoded, Encoding.BASE_58, PublicKeyFormat.SS58, KeyPairType.SR25519)

  val bobKeyPair = SubstrateClientKeyPair.fromBytes(
    HexConverter.toBytes(
      "0x081ff694633e255136bdb456c20a5fc8fed21f8b964c11bb17ff534ce80ebd59" +
            "41ae88f85d0c1bfc37be41c904e1dfc01de8c8067b0d6d5df25dd1ac0894a325" +
            "8eaf04151687736326c9fea17e25fc5287613693c912909cb226aa4794f26a48"
    )
  )

  val bobAccountKeyPair = KeyPair(
    fromHex("0x8eaf04151687736326c9fea17e25fc5287613693c912909cb226aa4794f26a48")
      .toPublicKeyBytes(),
    fromHex("0x081ff694633e255136bdb456c20a5fc8fed21f8b964c11bb17ff534ce80ebd5941ae88f85d0c1bfc37be41c904e1dfc01de8c8067b0d6d5df25dd1ac0894a325")
      .toPrivateKeyBytes(),
    Sr25519CryptoProvider
  )

  val bobAccountId = bobAccountKeyPair.toUniversalAddress()
  val bobPublicKeyBytes = bobAccountKeyPair.publicKeyBytes

  //NOTE (Aziz 8/25/25): Keeping the old public key dto and encoded objects for compatibility with application tests
  val bobPublicKeyEncoded = Sr25519KeyPairCreator.encodeSr25519PublicKey(bobPublicKeyBytes.bytes, SS58AddressFormat.BARE_SR_25519)
  val bobProviderPublicKeyDto =
    PublicKeyDto(bobPublicKeyEncoded, Encoding.BASE_58, PublicKeyFormat.SS58, KeyPairType.SR25519)

  lateinit var aliceProviderMsaId: BigInteger
  lateinit var bobProviderMsaId: BigInteger
  lateinit var aliceProviderClient: FrequencyClient
  lateinit var bobProviderClient: FrequencyClient
  lateinit var aliceProviderTestClient: FrequencyTestProvider
  lateinit var bobProviderTestClient: FrequencyTestProvider

  init {
    withCommand("--offchain-worker=always --enable-offchain-indexing=true")
    withExposedPorts(30333, 9944, 9933)
    waitingFor(
      Wait.forLogMessage(".*Running JSON-RPC server.*", 1)
    )
  }

  override fun start() {
    super.start()
    setUp()
  }

  val wsAddress: String get() = "ws://$host:${getMappedPort(9944)}"

  private fun createFrequencyClient(keyPair: AccountKeyPair) = RetryingFrequencyClient(
    SubstrateClientJavaFrequencyClient(
      wsAddress,
      keyPair,
      listOf(
        "io.amplica.custodial_wallet.util.orchestration",
        "io.amplica.frequency.signing_service",
        "io.amplica.frequency.client"
      ),
      Duration.of(1, ChronoUnit.SECONDS),
      Duration.of(1, ChronoUnit.SECONDS),
      ExponentialBackoffReconnectionPolicy.builder().notMoreThan(30).build()
    )
  )

  private fun setUpDevelopmentProvider(client: FrequencyClient, name: String, accountId: ByteArray, keyPair: AccountKeyPair): BigInteger = runBlocking {
    val msaCreated = client.createMsa().await()
    Assertions.assertTrue(msaCreated.isRight())

    val msaId = client.getMsaIdByAccountId(accountId).await()!!
    Assertions.assertNotNull(msaId)

    val providerRegistryEntryPayload = CommonPrimitivesProviderRegistryEntry(
      name,
      emptyList(),
      "bafkreidgvpkjawlxz6sffxzwgooowe5yt7i6wsyg236mfoks77nywkptdq",
      emptyList()
    )

    aliceProviderClient.createProviderViaGovernanceV2Sudo(keyPair, providerRegistryEntryPayload).await().getOrThrow()

    client.createStake(msaId, BigInteger.valueOf(10_000_000_000_000)).await().getOrThrow()

    msaId
  }

  private fun setUp() {
    aliceProviderClient = createFrequencyClient(aliceAccountKeyPair)
    aliceProviderMsaId = setUpDevelopmentProvider(aliceProviderClient, "Alice Provider", aliceAccountId, aliceAccountKeyPair)

    bobProviderClient = createFrequencyClient(bobAccountKeyPair)
    bobProviderMsaId = setUpDevelopmentProvider(bobProviderClient, "Bob Provider", bobAccountId, bobAccountKeyPair)

    aliceProviderTestClient = FrequencyTestProvider(aliceProviderClient, aliceProviderMsaId)
    bobProviderTestClient = FrequencyTestProvider(bobProviderClient, bobProviderMsaId)
  }

  fun createProvider(keyPair: AccountKeyPair, providerName: String): FrequencyTestProvider = runBlocking {
    val providerClient = createFrequencyClient(keyPair)

    val publicKeyBytes = keyPair.publicKeyBytes
    val accountId = keyPair.cryptoProvider.toUniversalAddress(publicKeyBytes)

    val transfer = aliceProviderClient.createTransferAllowDeath(
      accountId,
      BigInteger.valueOf(10_000_000_000_000)
    ).await()
    Assertions.assertTrue(transfer.isRight())

    val msaCreated = providerClient.createMsa().await()
    Assertions.assertTrue(msaCreated.isRight())

    val msaId = providerClient.getMsaIdByAccountId(accountId).await()!!
    Assertions.assertNotNull(msaId)

    val providerRegistryEntryPayload = CommonPrimitivesProviderRegistryEntry(
      providerName,
      emptyList(),
      "bafkreidgvpkjawlxz6sffxzwgooowe5yt7i6wsyg236mfoks77nywkptdq",
      emptyList()
    )

    aliceProviderClient.createProviderViaGovernanceV2Sudo(keyPair, providerRegistryEntryPayload).await().getOrThrow()

    providerClient.createStake(msaId, BigInteger.valueOf(9_000_000_000_000)).await().getOrThrow()

    FrequencyTestProvider(providerClient, msaId)
  }

  override fun getPropertyValues(): Map<String, String> {
    return mapOf(Pair("unfinished.custodial-wallet.frequency.address", wsAddress))
  }
}
