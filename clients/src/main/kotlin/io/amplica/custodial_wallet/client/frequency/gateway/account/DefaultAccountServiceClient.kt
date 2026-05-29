package io.amplica.custodial_wallet.client.frequency.gateway.account

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

class DefaultAccountServiceClient(private val webClient: WebClient) : AccountServiceClient {
  override suspend fun healthcheck(): Boolean {
    return webClient.get()
      .uri("/healthz")
      .exchangeToMono { response ->
        Mono.just(response.statusCode().is2xxSuccessful)
      }
      .awaitSingleOrNull() ?: false
  }
}