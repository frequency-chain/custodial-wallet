package io.amplica.custodial_wallet.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.net.HttpHeaders
import io.amplica.custodial_wallet.conf.BeanNames
import io.amplica.custodial_wallet.controller.util.LocalizationUtil
import io.amplica.custodial_wallet.controller.util.ThymeleafHelper
import io.amplica.custodial_wallet.dto.*
import io.amplica.custodial_wallet.orchestration.CustodialWalletOrchestrationService
import io.amplica.custodial_wallet.orchestration.LookupOrchestrationService
import io.amplica.custodial_wallet.orchestration.passkey.PasskeyWalletService
import io.amplica.custodial_wallet.web.Environment
import io.amplica.custodial_wallet.web.PASSKEY_WALLET_RECOVERY
import io.amplica.custodial_wallet.web.SESSION_ID_COOKIE_NAME
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import java.util.*

@Controller
@RequestMapping("wallet")
class PasskeyWalletController @Autowired constructor(
  @Qualifier(BeanNames.LOOKUP_ORCHESTRATION_SERVICE) private val lookupOrchestrationService: LookupOrchestrationService,
  @Qualifier(BeanNames.MESSAGES_LOCALIZATION_UTIL) private val messagesLocalizationUtil: LocalizationUtil,
  @Qualifier(BeanNames.CUSTODIAL_WALLET_ORCHESTRATION_SERVICE) private val orchestrationService: CustodialWalletOrchestrationService,
  @Qualifier(BeanNames.PASSKEY_WALLET_SERVICE) private val passkeyWalletService: PasskeyWalletService,
  private val objectMapper: ObjectMapper,
  @Value("\${unfinished.custodial-wallet.frequency.address}") private val frequencyAddress: String,
  @Value("\${unfinished.custodial-wallet.passkey.username}") private val username: String,
  @Value("\${unfinished.custodial-wallet.environment}") private val environment: Environment,
  @Qualifier(BeanNames.MATOMO_PROPERTIES) private val matomoProps: MatomoProps,
  @Value("\${sentry.environment}") private val sentryEnv: String,
  @Value("\${sentry.release}") private val sentryRelease: String,
) {
  companion object {
    const val WALLET_HOME_PAGE = "siwa/passkeyWallet"
    const val RENDER_IFRAME = "passkey_wallet/iframe"
    const val ACCOUNT_PAGE_PATH = "/web/account"
    const val HOME_PAGE_REDIRECT = "redirect:/index.html"
  }

  private fun addToModelWithPasskeyWalletProps(model: Model, messageMapJson: String, matomoData: MatomoData, passkeyWalletProps: PasskeyWalletProps) {
    model.addAllAttributes(mapOf(
      "helper" to ThymeleafHelper,
      "env" to environment,
      "sentryEnv" to sentryEnv,
      "sentryRelease" to sentryRelease,
      "matomo" to matomoProps.withData(matomoData),
      "messagesJson" to messageMapJson,
      "props" to passkeyWalletProps
    ))
  }

  private fun addToModelWithProviderBoostingProps(model: Model, providerBoostingDemoProps: ProviderBoostingDemoProps) {
    model.addAllAttributes(mapOf(
      "helper" to ThymeleafHelper,
      "env" to environment,
      "sentryEnv" to sentryEnv,
      "sentryRelease" to sentryRelease,
      "props" to providerBoostingDemoProps
    ))
  }

  @GetMapping("passkey")
  suspend fun renderPasskeyWalletPage(
    @CookieValue(SESSION_ID_COOKIE_NAME) sessionId: String,
    request: ServerHttpRequest,
    response: ServerHttpResponse,
    model: Model,
    locale: Locale
  ): String {
    if (!passkeyWalletService.passkeyWalletPageIsEnabled) {
      return HOME_PAGE_REDIRECT
    }
    val messageMapJson = objectMapper.writeValueAsString(messagesLocalizationUtil.getEscapedMessagesForLocale(locale))
    val callbackUrl = passkeyWalletService.getCallbackUrl(sessionId)
    val matomoData = MatomoData(WALLET_HOME_PAGE, SiwaMatomoDimensions.create(), MatomoEvent(MatomoEvent.Category.PASSKEY_WALLET, "passkeyWallet"))
    val passkeyWalletProps = PasskeyWalletProps(callbackUrl, frequencyAddress, username, false)
    addToModelWithPasskeyWalletProps(model, messageMapJson, matomoData, passkeyWalletProps)
    return WALLET_HOME_PAGE
  }

  @GetMapping("recovery")
  suspend fun renderPasskeyWalletRecoveryPage(
    @CookieValue(SESSION_ID_COOKIE_NAME) sessionId: String,
    request: ServerHttpRequest,
    response: ServerHttpResponse,
    model: Model,
    locale: Locale
  ): String {
    if (!passkeyWalletService.passkeyWalletPageIsEnabled) {
      return HOME_PAGE_REDIRECT
    }

    val accountPublicKeyHex = passkeyWalletService.getAccountPublicKeyHexOrThrow(sessionId)

    val messageMapJson = objectMapper.writeValueAsString(messagesLocalizationUtil.getEscapedMessagesForLocale(locale))
    val callbackUrl = passkeyWalletService.getCallbackUrl(sessionId)
    val matomoData = MatomoData(WALLET_HOME_PAGE, SiwaMatomoDimensions.create(), MatomoEvent(MatomoEvent.Category.PASSKEY_WALLET, "passkeyWallet"))
    val passkeyWalletProps = PasskeyWalletProps(callbackUrl, frequencyAddress, username, true, accountPublicKeyHex)
    addToModelWithPasskeyWalletProps(model, messageMapJson, matomoData, passkeyWalletProps)

    return WALLET_HOME_PAGE
  }

  @GetMapping("iframe")
  suspend fun renderPasskeyWalletIFrame(
    @RequestParam(PASSKEY_WALLET_RECOVERY, required = false) isPasskeyWalletRecovery: Boolean?,
    response: ServerHttpResponse,
    model: Model
  ): String {
    //Took from HttpHeadders from Google as Spring didn't have it
    response.headers.add(HttpHeaders.CONTENT_SECURITY_POLICY, "sandbox allow-scripts; frame-ancestors 'self'")
    model.addAttribute("props", PasskeyIFrameProps(frequencyAddress, username, isPasskeyWalletRecovery))
    model.addAttribute("env", environment)

    return RENDER_IFRAME
  }

  @GetMapping("boost")
  suspend fun renderPasskeyWalletProviderBoostingPage(
    @CookieValue(SESSION_ID_COOKIE_NAME) sessionId: String,
    model: Model,
  ): String {
    // TODO: This logic should be moved to a proper (orchestration) service
    // (e.g., PasskeyWalletService, ProviderBoostingService)
    val user = lookupOrchestrationService.findWebsiteOrSiwaAuthenticatedUserDataOrThrow(sessionId)
    val userData = lookupOrchestrationService.getUserDataForUserAccountId(user.userAccountId)
    val providerInfoList = orchestrationService.mapUserDataToProviderUserInfo(userData)
    val providerBoostingDemoProps = ProviderBoostingDemoProps(frequencyAddress, providerInfoList)

    addToModelWithProviderBoostingProps(model, providerBoostingDemoProps)

    return "passkey_wallet/providerBoostingDemo.html"
  }

  @GetMapping("copyExampleEnclosingPage.html")
  suspend fun renderPasskeyWalletCopyExampleEnclosingPage(): String {
    return "passkey_wallet/copyExampleEnclosingPage"
  }

  @GetMapping("copyExample.html")
  suspend fun renderPasskeyWalletCopyExample(): String {
    return "passkey_wallet/copyExample"
  }
}