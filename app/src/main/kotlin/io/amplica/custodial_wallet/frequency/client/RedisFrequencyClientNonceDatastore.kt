package io.amplica.custodial_wallet.frequency.client

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import io.amplica.custodial_wallet.client.redis.frequency.FrequencyClientRedisClient
import io.amplica.frequency.client.FrequencyClientNonceDatastore
import kotlinx.coroutines.future.await
import java.util.concurrent.CompletableFuture

class RedisFrequencyClientNonceDatastore(private val publicKeyHex: String, private val frequencyClientRedisClient: FrequencyClientRedisClient) : FrequencyClientNonceDatastore {
  @OptIn(DelicateCoroutinesApi::class)
  override fun currentNonce(): CompletableFuture<Long?> = GlobalScope.future {
    frequencyClientRedisClient.getNonce(publicKeyHex)
  }

  @OptIn(DelicateCoroutinesApi::class)
  override fun getAndIncrementNonce(): CompletableFuture<Long> = GlobalScope.future{
    val incrementedNonce = frequencyClientRedisClient.incrementNonce(publicKeyHex)
    incrementedNonce - 1
  }

  @OptIn(DelicateCoroutinesApi::class)
  override fun initializeNonce(initialNonce: Long): CompletableFuture<Unit> = GlobalScope.future{
    frequencyClientRedisClient.setNonce(publicKeyHex, initialNonce)
  }
}