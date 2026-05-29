package io.amplica.custodial_wallet.health

import io.amplica.frequency.client.FrequencyClient
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.ReactiveHealthIndicator
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.math.BigInteger

class ChainHealthIndicator(private val saasFrequencyClient: FrequencyClient) : ReactiveHealthIndicator {

  override fun health(): Mono<Health> {
    return doHealthCheck().onErrorResume { exception: Throwable? ->
      Mono.just(Health.Builder().down(exception).build())
    }
  }

  private fun doHealthCheck(): Mono<Health> {
      return saasFrequencyClient.getHealth().toMono().map { healthResult ->
        val healthy = ((healthResult.peers > BigInteger.ZERO && healthResult.shouldHavePeers)
            || (healthResult.peers == BigInteger.ZERO && !healthResult.shouldHavePeers))
        val builder = if(healthy) {
          Health.up()
        } else {
          Health.down().withDetail("Peers", "Peers=${healthResult.peers} shouldHavePeers=${healthResult.shouldHavePeers}")
        }
        builder.withDetail("peers", healthResult.peers)
        builder.withDetail("shouldHavePeers", healthResult.shouldHavePeers)
        builder.withDetail("isSyncing", healthResult.isSyncing)
        builder.build()
      }
  }
}