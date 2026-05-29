package io.amplica.custodial_wallet.container

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.time.Duration

class AccountServiceWorkerTestContainer(
  frequencyAddress: String,
  redisUrl: String,
) : GenericContainer<AccountServiceApiTestContainer?>(IMAGE_NAME) {

  companion object {
    const val IMAGE_NAME = "${FrequencyGatewayContainerProperties.AccountService.IMAGE}:${FrequencyGatewayContainerProperties.AccountService.VERSION}"
  }

  init {
    setCommand("account-worker")

    withEnv(FrequencyGatewayContainerProperties.Common.getEnv(frequencyAddress, redisUrl))
    withEnv(FrequencyGatewayContainerProperties.AccountService.ENV)

    waitingFor(
      Wait.forHttp("/healthz").forStatusCode(200).withStartupTimeout(Duration.ofSeconds(90))
    )
  }
}