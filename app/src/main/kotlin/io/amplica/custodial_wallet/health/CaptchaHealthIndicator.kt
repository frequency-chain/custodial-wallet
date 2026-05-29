package io.amplica.custodial_wallet.health

import io.amplica.custodial_wallet.client.captcha.CaptchaClient
import kotlinx.coroutines.reactor.mono
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.ReactiveHealthIndicator
import reactor.core.publisher.Mono

class CaptchaHealthIndicator(private val captchaClient: CaptchaClient): ReactiveHealthIndicator {

  override fun health(): Mono<Health> {
    return doHealthCheck().onErrorResume { exception: Throwable? ->
      Mono.just(Health.Builder().withException(exception).status(ExtendedBootHealthStatus.DEGRADED).build())
    }
  }

  private fun doHealthCheck(): Mono<Health> {
    return mono { captchaClient.healthcheck() }
      .map { healthResult ->
        val builder = if (healthResult) {
          Health.up()
        } else {
          Health.status(ExtendedBootHealthStatus.DEGRADED)
        }
        builder.build()
      }
  }
}