package io.amplica.custodial_wallet.health

import kotlinx.coroutines.reactor.mono
import io.amplica.custodial_wallet.client.kms.KmsClient
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.ReactiveHealthIndicator
import reactor.core.publisher.Mono

class KmsClientHealthIndicator(private val kmsClient: KmsClient): ReactiveHealthIndicator {
  override fun health(): Mono<Health> {
    return doHealthCheck().onErrorResume { exception: Throwable? ->
      Mono.just(Health.Builder().down(exception).build())
    }
  }

  private fun doHealthCheck(): Mono<Health> {
    return mono { kmsClient.healthcheck() }
      .map { healthResult ->
        val builder = if (healthResult) {
          Health.up()
        } else {
          Health.down()
        }
        builder.build()
      }
  }
}