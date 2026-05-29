package io.amplica.custodial_wallet.client.redis.frequency

import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.redis.core.ReactiveRedisOperations

class ReactiveFrequencyClientRedisClient(
  private val reactiveStringRedisTemplate: ReactiveRedisOperations<String, String>
) : FrequencyClientRedisClient {

  companion object {
    private const val NONCE_PREFIX = "nonce"
    const val NONCE_KEY_TEMPLATE = "$NONCE_PREFIX.%s"
  }

  private fun createNonceKey(publicKeyHex: String): String {
    return String.format(NONCE_KEY_TEMPLATE, publicKeyHex)
  }

  override suspend fun incrementNonce(publicKeyHex: String): Long {
    return reactiveStringRedisTemplate.opsForValue().increment(createNonceKey(publicKeyHex)).awaitSingle()
  }

  override suspend fun getNonce(publicKeyHex: String): Long? {
    val nonce = reactiveStringRedisTemplate.opsForValue().get(createNonceKey(publicKeyHex)).awaitSingleOrNull() ?: return null
    return nonce.toLong()
  }

  override suspend fun setNonce(publicKeyHex: String, nonce: Long) {
    reactiveStringRedisTemplate.opsForValue().set(createNonceKey(publicKeyHex), nonce.toString()).awaitSingle()
  }
}
