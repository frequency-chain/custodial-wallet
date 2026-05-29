package io.amplica.custodial_wallet.container

import org.springframework.test.context.DynamicPropertyRegistry

interface CustodialWalletTestContainer {
  fun getPropertyValues(): Map<String, String>

  fun registerDynamicProperties(registry: DynamicPropertyRegistry) {
    registerDynamicProperties(registry, getPropertyValues())
  }

  private fun registerDynamicProperties(registry: DynamicPropertyRegistry, properties: Map<String, String>) {
    properties.forEach { entry ->
      registry.add(entry.key) { entry.value }
    }
  }
}
