package io.amplica.custodial_wallet.exception

fun redisClientErrorToApiExceptionMapper(redisClientError: RedisClientError, message: String): ApiException {
  return when(redisClientError) {
    RedisClientError.NO_SIGNUP_TOKEN_FOUND -> {
      ApiException(ApiError.NO_SIGNUP_REQUEST_FOR_TOKEN_ERROR, message)
    }
    RedisClientError.NO_LOGIN_TOKEN_FOUND -> {
      ApiException(ApiError.NO_LOGIN_REQUEST_FOR_TOKEN_ERROR, message)
    }
    RedisClientError.NO_SIGNUP_REQUEST_FOUND -> {
      ApiException(ApiError.NO_SIGNUP_REQUEST_FOR_SESSION_ERROR, message)
    }
    RedisClientError.NO_LOGIN_REQUEST_FOUND -> {
      ApiException(ApiError.NO_LOGIN_REQUEST_FOR_SESSION_ERROR, message)
    }
    RedisClientError.NULL_SIGNUP_TOKEN -> {
      ApiException(ApiError.NO_SIGNUP_REQUEST_FOR_TOKEN_ERROR, message)
    }
    RedisClientError.NULL_LOGIN_TOKEN -> {
      ApiException(ApiError.NO_LOGIN_REQUEST_FOR_TOKEN_ERROR, message)
    }
    else -> {
      ApiException(ApiError.UNKNOWN_ERROR, message)
    }
  }
}