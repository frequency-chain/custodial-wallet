package io.amplica.custodial_wallet.client.captcha.conf

import io.amplica.custodial_wallet.client.captcha.CaptchaClient
import io.amplica.custodial_wallet.client.captcha.HCaptchaClient
import io.amplica.custodial_wallet.web.Environment
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
class HCaptchaClientConf {
  /**
   * Returns false for the integration environment as we are using a NAT Gateway that effectively times out the connection
   * to the outside world which means the application fails with a connectionReset; the theory is this will fix that.
   * This is a function of not having a lot of traffic in that environment and the NAT Gateway solution we went with.
   * Ideally you always want keepAlive and in our AWS Production account we do not see this issue (it uses a different
   * NAT Gateway solution)
   */
  private fun determineSoKeepAlive(environment: Environment): Boolean {
    return when(environment) {
      Environment.INTEGRATION -> false
      else -> true
    }
  }

  @Bean
  fun hCaptchaClient(
    @Value("\${unfinished.custodial-wallet.hcaptcha.enabled}") enabled: Boolean,
    @Value("\${unfinished.custodial-wallet.hcaptcha.service_endpoint}") serviceEndpoint: String,
    @Value("\${unfinished.custodial-wallet.hcaptcha.status_endpoint}") statusEndpoint: String,
    @Value("\${unfinished.custodial-wallet.hcaptcha.secret_key}") secretKey: String,
    @Value("\${unfinished.custodial-wallet.hcaptcha.site_key}") siteKey: String,
    @Value("\${unfinished.custodial-wallet.hcaptcha.include_user_ip}") includeUserIP: Boolean,
    @Value("\${unfinished.custodial-wallet.hcaptcha.max_connections}") maxConnections: Int,
    @Value("\${unfinished.custodial-wallet.hcaptcha.connection_pool_timeout}") connectionPoolTimeout: Duration,
    @Value("\${unfinished.custodial-wallet.hcaptcha.connection_timeout}") connectionTimeout: Duration,
    @Value("\${unfinished.custodial-wallet.hcaptcha.read_timeout}") readTimeout: Duration,
    @Value("\${unfinished.custodial-wallet.hcaptcha.write_timeout}") writeTimeout: Duration,
    @Value("\${unfinished.custodial-wallet.environment}") environment: Environment,
    @Value("\${unfinished.custodial-wallet.hcaptcha.x.captcha.header.value}") xCaptchaHeaderValue: String,
  ): CaptchaClient {
    val httpClient = HttpClient.create(
      ConnectionProvider.builder("connectionProvider").maxConnections(maxConnections)
        .pendingAcquireTimeout(connectionPoolTimeout).build()
    )
      .option(ChannelOption.SO_KEEPALIVE, determineSoKeepAlive(environment))
      .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeout.toMillisPart())
      .doOnConnected { conn ->
        conn
          .addHandlerLast(ReadTimeoutHandler(readTimeout.toSecondsPart()))
          .addHandlerLast(WriteTimeoutHandler(writeTimeout.toSecondsPart()))
      }

    val verifyClient = WebClient.builder()
      .baseUrl(serviceEndpoint)
      .clientConnector(ReactorClientHttpConnector(httpClient))
      .build()

    val statusClient = WebClient.builder()
      .baseUrl(statusEndpoint)
      .clientConnector(ReactorClientHttpConnector(httpClient))
      .build()
    return HCaptchaClient(enabled, siteKey, verifyClient, statusClient, secretKey, includeUserIP, xCaptchaHeaderValue)
  }
}
