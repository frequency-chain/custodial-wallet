package io.amplica.custodial_wallet.container

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.time.Duration


class AccountServiceApiTestContainer(frequencyAddress: String, redisUrl: String) :
  GenericContainer<AccountServiceApiTestContainer?>(IMAGE_NAME),
  CustodialWalletTestContainer
{

  companion object {
    const val IMAGE_NAME = "${FrequencyGatewayContainerProperties.AccountService.IMAGE}:${FrequencyGatewayContainerProperties.AccountService.VERSION}"
  }

  init {
    setCommand("account-api")

    withEnv(FrequencyGatewayContainerProperties.Common.getEnv(frequencyAddress, redisUrl))
    withEnv(FrequencyGatewayContainerProperties.AccountService.ENV)

    addExposedPorts(3000)

    waitingFor(
      Wait.forHttp("/healthz").forStatusCode(200).withStartupTimeout(Duration.ofSeconds(90))
    )
  }

  override fun getPropertyValues(): Map<String, String> {
    return mapOf(
      "unfinished.custodial-wallet.client.frequency.gateway.account.service_endpoint" to "http://localhost:${getMappedPort(3000)}"
    )
  }
}
