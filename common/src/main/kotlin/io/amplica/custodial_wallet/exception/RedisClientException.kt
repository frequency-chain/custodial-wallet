package io.amplica.custodial_wallet.exception

import com.google.common.collect.FluentIterable

data class RedisClientErrorDto(
  val id: Int,
  val description: String,
  val stackTrace: String?
)

enum class RedisClientError(val id: Int, val description: String) {
  UNKNOWN_ERROR(1, "An unknown error has occurred"),
  NO_SIGNUP_TOKEN_FOUND(2, "No signup token found"),
  NO_LOGIN_TOKEN_FOUND(3, "No login token found"),
  NO_SIGNUP_REQUEST_FOUND(4, "No signup request found"),
  NO_LOGIN_REQUEST_FOUND(5, "No login request found"),
  NULL_SIGNUP_TOKEN(6, "Signup token cannot be null"),
  NULL_LOGIN_TOKEN(7, "Login token cannot be null");
  ;

  companion object {
    @Suppress("UnstableApiUsage") //from is marked @Beta but its been beta for years
    private val REDIS_CLIENT_ERROR_INDEX: Map<Int, RedisClientError> = FluentIterable.from(RedisClientError.values()).uniqueIndex { it.id }

    fun fromId(id: Int): RedisClientError {
      return REDIS_CLIENT_ERROR_INDEX[id]
        ?: throw IllegalArgumentException("Error ID=${id} is not recognized")
    }
  }
}


class RedisClientException(val redisClientError: RedisClientError, message: String?, cause: Throwable?, enableSuppression: Boolean, writableStackTrace: Boolean) : RuntimeException(message, cause, enableSuppression, writableStackTrace) {
  constructor(redisClientError: RedisClientError, message: String?) : this(redisClientError, message, null)
  constructor(redisClientError: RedisClientError, message: String?, cause: Throwable?) : this(redisClientError, message, cause, false, true)
  constructor(redisClientError: RedisClientError, cause: Throwable?) : this(redisClientError, null, cause, false, true)
}