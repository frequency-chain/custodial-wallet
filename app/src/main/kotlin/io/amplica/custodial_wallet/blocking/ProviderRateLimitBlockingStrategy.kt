package io.amplica.custodial_wallet.blocking
import io.amplica.custodial_wallet.client.redis.CustodialWalletRedisClient
import io.amplica.custodial_wallet.client.redis.ProviderCount
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.time.Duration

class ProviderRateLimitBlockingStrategy(
  private val redisClient: CustodialWalletRedisClient,
  val maxCount: Int,
  val period: Duration,
  // For testing purposes
  private val timeMillisSupplier: () -> Long = System::currentTimeMillis
) : BlockingStrategy {

  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(ProviderRateLimitBlockingStrategy::class.java)
  }

  private val bucketSizeSeconds = period.toSeconds().coerceAtLeast(1)

  override suspend fun checkOrThrow(providerMsaId: BigInteger, sessionId: String?) {
    val currentTimeSeconds = timeMillisSupplier.invoke() / 1000L
    val bucket = currentTimeSeconds - (currentTimeSeconds % bucketSizeSeconds)

    val key = "$bucket:$providerMsaId"

    val count = redisClient.findProviderCount(key)?.count ?: 0

    if (count >= maxCount) {
      throw ApiException(
        ApiError.TEMPORARILY_BANNED_PROVIDER,
        "Provider with msaId $providerMsaId is temporarily banned",
        if(sessionId != null) {
          mapOf(
            "sessionId" to sessionId
          )
        } else mapOf()
      )
    }

    // NOTE: `timeToLive` is double bucket size to decrease chance of timing issues
    val timeToLive = Duration.ofSeconds(bucketSizeSeconds * 2)
    redisClient.saveProviderCount(ProviderCount(key, count + 1, timeToLive.seconds))
  }
}
