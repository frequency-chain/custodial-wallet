package io.amplica.custodial_wallet.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.amplica.custodial_wallet.client.captcha.CaptchaClient
import io.amplica.custodial_wallet.conf.BeanNames
import io.amplica.custodial_wallet.controller.util.LocalizationUtil
import io.amplica.custodial_wallet.orchestration.CustodialWalletOrchestrationService
import io.amplica.custodial_wallet.orchestration.LookupOrchestrationService
import io.amplica.custodial_wallet.web.Environment
import io.amplica.custodial_wallet.web.SESSION_ID_COOKIE_NAME
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import java.util.*


@Controller
@RequestMapping("/")
class WebSiteController @Autowired constructor(
  private val objectMapper: ObjectMapper,
  @Qualifier(BeanNames.CUSTODIAL_WALLET_ORCHESTRATION_SERVICE) private val custodialWalletOrchestrationService: CustodialWalletOrchestrationService,
  @Qualifier(BeanNames.LOOKUP_ORCHESTRATION_SERVICE) private val lookupService: LookupOrchestrationService,
  @Qualifier(BeanNames.MESSAGES_LOCALIZATION_UTIL) private val messagesLocalizationUtil: LocalizationUtil,
  @Value("\${sentry.environment}") private val sentryEnv: String,
  @Value("\${sentry.release}") private val sentryRelease: String,
  @Value("\${unfinished.custodial-wallet.password.enabled}") private val passwordEnabled: Boolean,
  @Value("\${unfinished.custodial-wallet.logged-in-callback.enabled}") private val loggedInCallbackEnabled: Boolean,
  @Value("\${unfinished.custodial-wallet.environment}") private val environment: Environment,
  @Qualifier(BeanNames.CAPTCHA_CLIENT) private val captchaClient: CaptchaClient,
  @Value("\${unfinished.custodial-wallet.hcaptcha.site_key}") private val siteKey: String,
) {

  @GetMapping("/", "/index.html")
  suspend fun getIndexPage(
    @CookieValue(SESSION_ID_COOKIE_NAME) sessionId: String?,
    @RequestParam(name = "callbackUrl", required = false) callbackUrl: String?,
    model: Model,
    locale: Locale
  ): String {
    val isLoggedIn = custodialWalletOrchestrationService.checkLoggedInState(sessionId)

    // NOTE(2024-06-07, Julian): This feature was added for an internal community rewards POC
    if (loggedInCallbackEnabled && isLoggedIn && sessionId != null && callbackUrl != null) {
      val websiteSession = lookupService.findWebsiteSessionBySessionId(sessionId)
      return custodialWalletOrchestrationService.createRedirectForCallback(websiteSession, callbackUrl)
    }

    val messageMapJson = objectMapper.writeValueAsString(messagesLocalizationUtil.getEscapedMessagesForLocale(locale))
    model.addAllAttributes(mutableMapOf(
      "env" to environment,
      "isAccount" to false,
      "isLoggedIn" to isLoggedIn,
      "isPrivacy" to false,
      "isTerms" to false,
      "messagesJson" to messageMapJson,
      "passwordEnabled" to passwordEnabled,
      "sentryEnv" to sentryEnv,
      "sentryRelease" to sentryRelease,
      "captchaEnabled" to captchaClient.enabled,
      "siteKey" to siteKey
    ))

    return "website/index"
  }

  @GetMapping("/privacy", "/privacy.html")
  fun getPrivacyPage(model: Model): String {
    model.addAttribute("isTerms", false)
    model.addAttribute("isPrivacy", true)
    model.addAttribute("isAccount", false)
    return "website/privacy"
  }
  @GetMapping("/terms", "/terms.html")
  suspend fun getTermsPage(
    model: Model,
    locale: Locale,
    @CookieValue(SESSION_ID_COOKIE_NAME) sessionId: String?
  ): String {
    val isLoggedIn = custodialWalletOrchestrationService.checkLoggedInState(sessionId)

    val messageMapJson = objectMapper.writeValueAsString(messagesLocalizationUtil.getEscapedMessagesForLocale(locale))
    model.addAttribute("messagesJson", messageMapJson)
    model.addAttribute("isTerms", true)
    model.addAttribute("isDeveloperTerms", false)
    model.addAttribute("isPrivacy", false)
    model.addAttribute("isAccount", false)
    model.addAttribute("isLoggedIn", isLoggedIn)
    model.addAttribute("captchaEnabled", captchaClient.enabled)
    model.addAttribute("siteKey", siteKey)
    return "website/terms"
  }

  @GetMapping("/developer_terms", "/developer_terms.html")
  suspend fun getDeveloperTosPage(
    model: Model,
    locale: Locale,
    @CookieValue(SESSION_ID_COOKIE_NAME) sessionId: String?
  ): String {
    val isLoggedIn = custodialWalletOrchestrationService.checkLoggedInState(sessionId)

    val messageMapJson = objectMapper.writeValueAsString(messagesLocalizationUtil.getEscapedMessagesForLocale(locale))
    model.addAttribute("messagesJson", messageMapJson)
    model.addAttribute("isAccount", false)
    model.addAttribute("isPrivacy", false)
    model.addAttribute("isTerms", true)
    model.addAttribute("isDeveloperTerms", true)
    model.addAttribute("isLoggedIn", isLoggedIn)
    model.addAttribute("captchaEnabled", captchaClient.enabled)
    model.addAttribute("siteKey", siteKey)
    return "website/terms"
  }
}

@Profile("dev")
@Controller
@RequestMapping("/")
class DevUniversalLinkController {
  @GetMapping("/.well-known/assetlinks.json", produces = [MediaType.APPLICATION_JSON_VALUE])
  fun getAssetLinks(): String {
    return ".well-known/assetlinks-dev.json"
  }
}
