package io.amplica.custodial_wallet.client.notification.conf

import io.amplica.custodial_wallet.client.notification.NotificationServiceClient
import io.amplica.custodial_wallet.client.notification.RestNotificationServiceClient
import io.amplica.custodial_wallet.web.SHARED_SECRET
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
class NotificationServiceClientConf {
  @Bean
  fun restNotificationServiceClient(
    //NOTE: Should this be changed to have the "custodial-wallet" removed?
    @Value("\${unfinished.custodial-wallet.notification-service.service_endpoint}") notificationServiceEndpoint: String,
    @Value("\${unfinished.notificationservice.api.shared.secret}") sharedSecret: String,
    @Value("\${unfinished.custodial-wallet.notification-service.maxConnections}") maxConnections: Int,
    @Value("\${unfinished.custodial-wallet.notification-service.connectionPoolTimeout}") connectionPoolTimeout: Duration,
    @Value("\${unfinished.custodial-wallet.notification-service.connectionTimeout}") connectionTimeout: Duration,
    @Value("\${unfinished.custodial-wallet.notification-service.readTimeout}") readTimeout: Duration,
    @Value("\${unfinished.custodial-wallet.notification-service.writeTimeout}") writeTimeout: Duration,

    ): NotificationServiceClient {
    val httpClient = HttpClient.create(
      ConnectionProvider.builder("connectionProvider").maxConnections(maxConnections).pendingAcquireTimeout(connectionPoolTimeout).build()
    )
      .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeout.toMillisPart())
      .doOnConnected{ conn -> conn
        .addHandlerLast(ReadTimeoutHandler(readTimeout.toSecondsPart()))
        .addHandlerLast(WriteTimeoutHandler(writeTimeout.toSecondsPart()))
      }

    val webClient = WebClient.builder()
      .baseUrl(notificationServiceEndpoint)
      .defaultHeader(SHARED_SECRET, sharedSecret)
      .clientConnector(ReactorClientHttpConnector(httpClient))
      .build()
    return RestNotificationServiceClient(
      webClient
    )
  }
}