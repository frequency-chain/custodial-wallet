package io.amplica.custodial_wallet.blocking

import io.amplica.custodial_wallet.client.redis.CustodialWalletRedisClient
import io.amplica.custodial_wallet.client.redis.ProviderCount
import io.amplica.custodial_wallet.exception.ApiException
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.math.BigInteger
import java.time.Duration

class ProviderRateLimitBlockingStrategyTest {
  private lateinit var mockRedisClient: CustodialWalletRedisClient
  private lateinit var providerRateLimitBlockingStrategy: ProviderRateLimitBlockingStrategy

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    mockRedisClient = mock()

    providerRateLimitBlockingStrategy = ProviderRateLimitBlockingStrategy(
      mockRedisClient,
      2,
      Duration.ofSeconds(10)
    ) {49_000L}
  }
  @Nested
  @DisplayName("Provider Rate Limit Tests")
  inner class ProviderRateLimitTests{
    @Test
    fun incrementsCounterCorrectly(): Unit = runBlocking {
      providerRateLimitBlockingStrategy.checkOrThrow(BigInteger.ONE, null)

      Mockito.verify(mockRedisClient, Mockito.times(1)).findProviderCount("40:1")
      Mockito.verify(mockRedisClient, Mockito.times(1)).saveProviderCount(ProviderCount("40:1", 1, providerRateLimitBlockingStrategy.period.seconds*2))
    }

    @Test
    fun blocksWhenCountExceeded(): Unit = runBlocking {
      Mockito.`when`(mockRedisClient.findProviderCount(eq("40:1"))).thenReturn(ProviderCount("40:1", 2, providerRateLimitBlockingStrategy.period.seconds*2))

      Assertions.assertThatThrownBy {
        runBlocking {
          providerRateLimitBlockingStrategy.checkOrThrow(BigInteger.ONE, null)
        }
      }.isInstanceOf(ApiException::class.java)

      Mockito.verify(mockRedisClient, Mockito.times(1)).findProviderCount("40:1")
    }
  }
}