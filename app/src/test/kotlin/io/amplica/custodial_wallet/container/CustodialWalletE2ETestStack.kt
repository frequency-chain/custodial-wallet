package io.amplica.custodial_wallet.container

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.lifecycle.Startable

class CustodialWalletE2ETestStack : Startable, CustodialWalletTestContainer {

  companion object {
    const val GATEWAY_REDIS_ALIAS = "gateway-redis"
    const val FREQUENCY_ALIAS = "frequency"
    const val REDIS_PORT = 6379
    const val FREQUENCY_PORT = 9944
  }

  // Simple containers used by the application
  private val redis = RedisTestContainer()
  private val postgres = PostgresTestContainer()
  private val localStack = LocalStackTestContainer()

  // Network to facilitate gateway inter-container communication (e.g., with frequency, redis)
  private val gatewayNetwork = Network.newNetwork()

  val frequency = FrequencyTestContainer(FrequencyVersion.CURRENT).apply {
    this.withNetwork(gatewayNetwork)
  }

  // Gateway dependencies
  private val gatewayRedis = GenericContainer<Nothing>("valkey/valkey:7.2.6").apply {
    this.withNetwork(gatewayNetwork)
    this.withNetworkAliases(GATEWAY_REDIS_ALIAS)
    this.withExposedPorts(REDIS_PORT)
  }

  private var accountServiceWorker: AccountServiceWorkerTestContainer? = null
  private var accountServiceApi: AccountServiceApiTestContainer? = null

  private val allContainers = listOf(
    redis,
    postgres,
    localStack,
    frequency,
    gatewayRedis,
    accountServiceWorker,
    accountServiceApi
  )

  override fun start() {
    redis.start()
    postgres.start()
    localStack.start()
    frequency.start()
    gatewayRedis.start()

    val frequencyAddress = "ws://$FREQUENCY_ALIAS:$FREQUENCY_PORT"
    val redisUrl = "redis://$GATEWAY_REDIS_ALIAS:$REDIS_PORT"

    accountServiceWorker = AccountServiceWorkerTestContainer(frequencyAddress, redisUrl).apply {
      this.withNetwork(gatewayNetwork)
      this.start()
    }
    accountServiceApi = AccountServiceApiTestContainer(frequencyAddress, redisUrl).apply {
      this.withNetwork(gatewayNetwork)
      this.start()
    }
  }

  override fun stop() {
    allContainers.forEach { it?.stop() }
  }

  override fun getPropertyValues(): Map<String, String> {
    return listOf(
      redis.getPropertyValues(),
      postgres.getPropertyValues(),
      localStack.getPropertyValues(),
      frequency.getPropertyValues(),
      accountServiceApi!!.getPropertyValues(),
    ).fold(mapOf()) { a, b -> a + b }
  }
}