package io.amplica.custodial_wallet.client.redis

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import io.amplica.custodial_wallet.client.redis.conf.RedisClientConfig
import io.amplica.custodial_wallet.client.redis.conf.RedisConfigurationProperties
import io.amplica.custodial_wallet.client.redis.frequency.ReactiveFrequencyClientRedisClient
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.redis.core.ReactiveRedisOperations
import org.springframework.data.redis.core.RedisOperations
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringJUnitConfig(classes = [CustodialWalletRedisClientTest::class, RedisClientConfig::class, MoreConfig::class])
@EnableConfigurationProperties(RedisConfigurationProperties::class)
@EnableAutoConfiguration
class FrequencyClientRedisClientTest {
  companion object {
    private const val PUBLIC_KEY_HEX = "0xCAFEBABE"
    private const val JENNYS_NUMBER = 8675309L

    @Container
    val redis = GenericContainer<Nothing>("valkey/valkey:7.2.6").apply {
      withExposedPorts(6379)
    }

    @DynamicPropertySource
    @JvmStatic
    fun registerDynamicProperties(registry: DynamicPropertyRegistry) {
      registry.add("spring.data.redis.host", redis::getHost)
      registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
      registry.add("unfinished.custodial-wallet.redis.expiration"){"10s"}
    }
  }

  @Autowired
  lateinit var stringRedisTemplate: RedisOperations<String,String>

  @Autowired
  lateinit var reactiveStringRedisTemplate: ReactiveRedisOperations<String, String>

  @Nested
  @DisplayName("Reactive Redis Tests")
  inner class ReactiveTests {
    private val frequencyClientRedisClient = ReactiveFrequencyClientRedisClient(reactiveStringRedisTemplate)

    @Test
    fun incrementNonce() {
      runBlocking {
        //GIVEN
        frequencyClientRedisClient.setNonce(PUBLIC_KEY_HEX, 0)
        val one = frequencyClientRedisClient.incrementNonce(PUBLIC_KEY_HEX)
        Assertions.assertThat(one).isEqualTo(1L)

        //WHEN
        val two = frequencyClientRedisClient.incrementNonce(PUBLIC_KEY_HEX)

        //THEN
        Assertions.assertThat(two).isEqualTo(2L)
      }
    }

    @Test
    fun getNonce() {
      runBlocking {
        //GIVEN
        frequencyClientRedisClient.setNonce(PUBLIC_KEY_HEX, JENNYS_NUMBER)

        //WHEN
        val jennysNumber = frequencyClientRedisClient.getNonce(PUBLIC_KEY_HEX)

        Assertions.assertThat(jennysNumber).isEqualTo(JENNYS_NUMBER)
      }
    }
  }
}
