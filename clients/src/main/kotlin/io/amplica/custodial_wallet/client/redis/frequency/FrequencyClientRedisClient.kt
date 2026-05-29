package io.amplica.custodial_wallet.client.redis.frequency

interface FrequencyClientRedisClient {
  suspend fun incrementNonce(publicKeyHex: String): Long
  suspend fun getNonce(publicKeyHex: String): Long?
  suspend fun setNonce(publicKeyHex: String, nonce: Long)
}
