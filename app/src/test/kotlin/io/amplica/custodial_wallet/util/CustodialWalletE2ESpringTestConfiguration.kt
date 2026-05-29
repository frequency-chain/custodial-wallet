package io.amplica.custodial_wallet.util

import io.amplica.custodial_wallet.addListeners
import org.springframework.boot.SpringApplication
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootContextLoader
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.testcontainers.junit.jupiter.Testcontainers

class TestSpringBootContextLoader : SpringBootContextLoader() {
  override fun getSpringApplication(): SpringApplication {
    val app: SpringApplication = super.getSpringApplication()
    addListeners(app)
    return app
  }
}

/**
 * Shorthand decorator for configuring a `Tests` class to run integration tests.
 */
@Testcontainers
@AutoConfigureObservability // Apparently metrics endpoints are disabled for tests by default. This fixes it
@ActiveProfiles("test", "enableTesting")
@ContextConfiguration(loader = TestSpringBootContextLoader::class)
annotation class CustodialWalletE2ESpringTestConfiguration