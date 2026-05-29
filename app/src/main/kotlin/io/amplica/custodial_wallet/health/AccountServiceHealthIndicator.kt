package io.amplica.custodial_wallet.health

import io.amplica.custodial_wallet.client.frequency.gateway.account.AccountServiceClient
import kotlinx.coroutines.reactor.mono
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.ReactiveHealthIndicator
import reactor.core.publisher.Mono

class AccountServiceHealthIndicator(private val accountServiceClient: AccountServiceClient) : ReactiveHealthIndicator {

  override fun health(): Mono<Health> {
    return doHealthCheck().onErrorResume { exception: Throwable? ->
      Mono.just(Health.Builder().down(exception).build())
    }
  }

  private fun doHealthCheck(): Mono<Health> {
    return mono { accountServiceClient.healthcheck() }
      .map { healthResult ->
        val builder = if (healthResult) {
          Health.up()
        } else {
          // TODO: Change to `DOWN` ounce gateway services are live in all envs
          Health.status(ExtendedBootHealthStatus.DEGRADED)
        }
        builder.build()
      }
  }
}