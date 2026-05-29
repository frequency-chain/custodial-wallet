package io.amplica.custodial_wallet.health

import io.amplica.custodial_wallet.client.notification.NotificationServiceClient
import kotlinx.coroutines.reactor.mono
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.ReactiveHealthIndicator
import reactor.core.publisher.Mono

class NotificationServiceHealthIndicator(private val client: NotificationServiceClient) : ReactiveHealthIndicator {

  override fun health(): Mono<Health> {
    return doHealthCheck().onErrorResume { exception: Throwable? ->
      Mono.just(Health.Builder().down(exception).build())
    }
  }

  private fun doHealthCheck(): Mono<Health> {
    return mono {
      client.healthcheck()
    }.map { isHealthy ->
      when {
        isHealthy -> Health.up().build()
        else -> Health.down().build()
      }
    }
  }

}