package io.amplica.custodial_wallet.web

import com.google.common.collect.FluentIterable
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.server.Cookie.SameSite
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.ResponseCookie
import java.time.Duration
import java.util.*

enum class Environment(val type: String) {
  MAIN("main"),
  TEST("test"),
  INTEGRATION("integration"),
  DEV("dev");

  companion object {
    @Suppress("UnstableApiUsage") //from is marked @Beta but its been beta for years
    private val TYPE_INDEX: Map<String, Environment> = FluentIterable.from(entries.toTypedArray()).uniqueIndex { it.type.uppercase(
      Locale.US) }

    fun fromType(type: String): Environment {
      return TYPE_INDEX[type.uppercase(Locale.US)]
        ?: throw IllegalArgumentException("Environment Type=${type} is not recognized")
    }
  }
}
@Configuration
open class CookieHelperConf {

  @Bean
  open fun cookieHelper(
    @Value("\${unfinished.custodial-wallet.environment}") env: String,
    @Value("\${unfinished.custodial-wallet.redis.expiration}") redisExpiration: Duration
  ): CookieHelper {
    val environment = Environment.fromType(env)
    return CookieHelper(
      environment,
      redisExpiration
    )
  }
}

class CookieHelper(private val environment: Environment, private val sessionTimeoutDuration: Duration) {

  fun createResponseCookie(sessionId: String): ResponseCookie {
    val cookieBuilder = ResponseCookie.from(SESSION_ID_COOKIE_NAME, sessionId).path("/")
    when(environment) {
      Environment.MAIN, Environment.TEST, Environment.INTEGRATION -> cookieBuilder.sameSite(SameSite.NONE.attributeValue()).secure(true)
      Environment.DEV -> cookieBuilder.secure(false)
    }
    cookieBuilder.maxAge(sessionTimeoutDuration)
    return cookieBuilder.build()
  }
}
