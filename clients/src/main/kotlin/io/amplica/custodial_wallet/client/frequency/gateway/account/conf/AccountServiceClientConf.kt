package io.amplica.custodial_wallet.client.frequency.gateway.account.conf

import io.amplica.custodial_wallet.client.frequency.gateway.account.AccountServiceClient
import io.amplica.custodial_wallet.client.frequency.gateway.account.DefaultAccountServiceClient
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
class AccountServiceClientConf {

  object BeanNames {
    const val ACCOUNT_SERVICE_CLIENT = "accountServiceClient"
  }

  @Bean
  fun accountServiceClient(
    @Value("\${unfinished.custodial-wallet.client.frequency.gateway.account.service_endpoint}") accountServiceApiEndpoint: String,
    @Value("\${unfinished.custodial-wallet.client.frequency.gateway.account.maxConnections}") maxConnections: Int,
    @Value("\${unfinished.custodial-wallet.client.frequency.gateway.account.connectionPoolTimeout}") connectionPoolTimeout: Duration,
    @Value("\${unfinished.custodial-wallet.client.frequency.gateway.account.connectionTimeout}") connectionTimeout: Duration,
    @Value("\${unfinished.custodial-wallet.client.frequency.gateway.account.readTimeout}") readTimeout: Duration,
    @Value("\${unfinished.custodial-wallet.client.frequency.gateway.account.writeTimeout}") writeTimeout: Duration,
  ): AccountServiceClient {
    val httpClient = HttpClient.create(
      ConnectionProvider.builder("connectionProvider")
        .maxConnections(maxConnections)
        .pendingAcquireTimeout(connectionPoolTimeout)
        .build()
    )
      .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeout.toMillisPart())
      .doOnConnected { conn ->
        conn.addHandlerLast(ReadTimeoutHandler(readTimeout.toSecondsPart()))
          .addHandlerLast(WriteTimeoutHandler(writeTimeout.toSecondsPart()))
      }

    val webClient = WebClient.builder()
      .baseUrl(accountServiceApiEndpoint)
      .clientConnector(ReactorClientHttpConnector(httpClient))
      .build()
    return DefaultAccountServiceClient(webClient)
  }
}