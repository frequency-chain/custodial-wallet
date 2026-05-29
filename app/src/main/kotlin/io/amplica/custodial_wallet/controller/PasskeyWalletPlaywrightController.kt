package io.amplica.custodial_wallet.controller

import io.amplica.custodial_wallet.conf.BeanNames
import io.amplica.custodial_wallet.dto.PasskeyIFrameProps
import io.amplica.custodial_wallet.orchestration.LookupOrchestrationService
import io.amplica.custodial_wallet.web.SESSION_ID_COOKIE_NAME
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import java.util.*

@Controller
@RequestMapping("wallet")
class PasskeyWalletPlaywrightController(
  @Qualifier(BeanNames.LOOKUP_ORCHESTRATION_SERVICE) private val lookupOrchestrationService: LookupOrchestrationService,
  @Value("\${unfinished.custodial-wallet.siwa.passkey-wallet.enabled}") private val passkeyEnabledForSiwa: Boolean,
  @Value("\${unfinished.custodial-wallet.frequency.address}") private val frequencyAddress: String,
  @Value("\${unfinished.custodial-wallet.passkey.username}") private val username: String,
  @Value("\${unfinished.custodial-wallet.environment}") private val environment: String,
) {
  companion object {
    const val WALLET_HOME_PAGE = "passkey_wallet_playwright/passkey"
    const val WALLET_TRANSACTION_PAGE = "passkey_wallet_playwright/transaction"
    const val HOME_PAGE_REDIRECT = "redirect:/index.html"
  }

  @GetMapping("passkey-playwright")
  suspend fun renderPasskeyWalletPage(
    @CookieValue(SESSION_ID_COOKIE_NAME) sessionId: String,
    request: ServerHttpRequest,
    response: ServerHttpResponse,
    model: Model,
    locale: Locale
  ): String {
    if ((environment == "dev" || environment == "test") && passkeyEnabledForSiwa) {
      //Assert that there's a valid SIWA session, otherwise it will throw an API Exception
      lookupOrchestrationService.findAuthenticatedSiwaSessionOrThrow(sessionId)
      return WALLET_HOME_PAGE
    } else {
      return HOME_PAGE_REDIRECT
    }
  }

  @PostMapping("passkey-playwright/transaction")
  suspend fun renderPasskeyTransactionPage(
    @CookieValue(SESSION_ID_COOKIE_NAME) sessionId: String,
    request: ServerHttpRequest,
    response: ServerHttpResponse,
    model: Model,
    locale: Locale
  ): String {
    if ((environment == "dev" || environment == "test") && passkeyEnabledForSiwa) {
      //Assert that there's a valid SIWA session, otherwise it will throw an API Exception
      lookupOrchestrationService.findAuthenticatedSiwaSessionOrThrow(sessionId)
      response.headers.add("Permissions-Policy", "publickey-credentials-get=*, publickey-credentials-create=*")
      model.addAttribute("props", PasskeyIFrameProps(frequencyAddress, username, false))
      return WALLET_TRANSACTION_PAGE
    } else {
      return HOME_PAGE_REDIRECT
    }
  }
}
