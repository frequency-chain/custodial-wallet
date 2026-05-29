package io.amplica.custodial_wallet.service.whitelistChecker

import io.amplica.custodial_wallet.service.whitelist_checker.ConfigWhitelistChecker
import io.amplica.custodial_wallet.service.whitelist_checker.WhitelistChecker
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ConfigWhitelistCheckerTest {
  private lateinit var whitelistChecker: WhitelistChecker
  
  private val whitelistedProviders = setOf("unfinished.com", "projectliberty.io", "test.com")
  private val whitelistedEmailDomains = setOf("test.com")
  @BeforeEach
  fun setUp() {
    whitelistChecker = ConfigWhitelistChecker(true, whitelistedProviders, true, whitelistedEmailDomains)
  }
  
  @ParameterizedTest
  @ValueSource(strings = ["devs@projectliberty.io", "devs@unfinished.com",])
  fun isWhitelistedForPlusAddressing(email: String) {

    val isWhitelisted = whitelistChecker.plusAddressingPermitted(email)
    Assertions.assertTrue(isWhitelisted)

  }

  @ParameterizedTest
  @ValueSource(strings = ["devs@dsrfgdfrgdf.org", "devs@mewe.com",])
  fun isNotWhitelistedForPlusAddressing(email: String) {

    val isWhitelisted = whitelistChecker.plusAddressingPermitted(email)
    Assertions.assertFalse(isWhitelisted)

  }

  @ParameterizedTest
  @ValueSource(strings = ["john.doe@test.com", "example@test.com"])
  fun isDoNotSendToEmailAddress(email: String) {
    val isWhitelisted = whitelistChecker.isNoSendEmailAddress(email)
    Assertions.assertTrue(isWhitelisted)
  }

  @ParameterizedTest
  @ValueSource(strings = ["test@", "devs@unfinished.com"])
  fun isSendToEmailAddress(email: String) {
    val isThisWhiteListed = whitelistChecker.isNoSendEmailAddress(email)
    Assertions.assertFalse(isThisWhiteListed)
  }
}