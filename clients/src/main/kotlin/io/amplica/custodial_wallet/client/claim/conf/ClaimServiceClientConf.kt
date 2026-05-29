package io.amplica.custodial_wallet.client.claim.conf

import io.amplica.custodial_wallet.client.claim.ClaimServiceClient
import io.amplica.custodial_wallet.client.claim.RestClaimServiceClient
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import java.time.Duration

@Configuration
class ClaimServiceClientConf {
  @Bean
  fun restClaimServiceClient(
    @Value("\${unfinished.custodial-wallet.claim-service.service_endpoint}") claimServiceEndpoint: String,
    @Value("\${unfinished.custodial-wallet.claim-service.maxConnections}") maxConnections: Int,
    @Value("\${unfinished.custodial-wallet.claim-service.connectionPoolTimeout}") connectionPoolTimeout: Duration,
    @Value("\${unfinished.custodial-wallet.claim-service.connectionTimeout}") connectionTimeout: Duration,
    @Value("\${unfinished.custodial-wallet.claim-service.readTimeout}") readTimeout: Duration,
    @Value("\${unfinished.custodial-wallet.claim-service.writeTimeout}") writeTimeout: Duration,

    ): ClaimServiceClient {
    val httpClient = HttpClient.create(
      ConnectionProvider.builder("connectionProvider").maxConnections(maxConnections).pendingAcquireTimeout(connectionPoolTimeout).build()
    )
      .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeout.toMillisPart())
      .doOnConnected{ conn -> conn
        .addHandlerLast(ReadTimeoutHandler(readTimeout.toSecondsPart()))
        .addHandlerLast(WriteTimeoutHandler(writeTimeout.toSecondsPart()))
      }

    val webClient = WebClient.builder()
      .baseUrl(claimServiceEndpoint)
      .clientConnector(ReactorClientHttpConnector(httpClient))
      .build()
    return RestClaimServiceClient(
      webClient
    )
  }
}