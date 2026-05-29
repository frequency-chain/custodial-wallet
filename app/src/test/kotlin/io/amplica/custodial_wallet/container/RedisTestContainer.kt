package io.amplica.custodial_wallet.container

import org.testcontainers.containers.GenericContainer

class RedisTestContainer : GenericContainer<Nothing>("valkey/valkey:8.1.5"), CustodialWalletTestContainer {
  companion object {
    // Exposed by the application but internal to the container
    const val PORT = 6379
  }

  init {
    withExposedPorts(PORT)
  }

  val exposedPort: Int get() = getMappedPort(PORT)

  override fun getPropertyValues(): Map<String, String> {
    return mapOf(
      Pair("spring.data.redis.host", host),
      Pair("spring.data.redis.port", getMappedPort(PORT).toString())
    )
  }
}
