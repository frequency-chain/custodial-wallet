package io.amplica.custodial_wallet.health

import io.amplica.custodial_wallet.email.client.SesClient
import io.amplica.custodial_wallet.email.client.TemplateExistsRequest
import kotlinx.coroutines.reactor.mono
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.ReactiveHealthIndicator
import reactor.core.publisher.Mono

/**
 * Performs a healthcheck by querying SES for a given template's existence.
 *
 * NOTE: `SesClient` may impose draconian rate limits (1 rps)--see `AwsSdkSesAsyncClient` for details.
 */
class SesClientHealthIndicator(private val sesClient: SesClient, private val signupTemplateName: String): ReactiveHealthIndicator {

  override fun health(): Mono<Health> {
    return doHealthCheck().onErrorResume { exception: Throwable? ->
      Mono.just(Health.Builder().withException(exception).status(ExtendedBootHealthStatus.DEGRADED).build())
    }
  }

  private fun doHealthCheck(): Mono<Health> {
    return mono { sesClient.healthCheck(TemplateExistsRequest(signupTemplateName)) }
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