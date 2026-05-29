package io.amplica.custodial_wallet.controller

import arrow.core.Either
import com.fasterxml.jackson.databind.ObjectMapper
import io.amplica.custodial_wallet.conf.BeanNames
import io.amplica.custodial_wallet.controller.util.LocalizationUtil
import io.amplica.custodial_wallet.dto.AccountInfo
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.orchestration.CustodialWalletOrchestrationService
import io.amplica.custodial_wallet.service.verifiable_credential.VerifiableCredentialService
import io.amplica.custodial_wallet.web.ContextLoggerHelper
import io.amplica.custodial_wallet.web.CookieHelper
import io.amplica.custodial_wallet.web.Environment
import io.amplica.custodial_wallet.web.SESSION_ID_COOKIE_NAME
import kotlinx.coroutines.future.await
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import java.util.*
import java.util.concurrent.CompletableFuture

class SmsCodeFormCommandObject(val smsCode: String)

@Controller
@RequestMapping("web")
class WebHtmlController @Autowired constructor(
  private val objectMapper: ObjectMapper,
  @Qualifier(BeanNames.CUSTODIAL_WALLET_ORCHESTRATION_SERVICE) private val custodialWalletOrchestrationService: CustodialWalletOrchestrationService,
  @Qualifier(BeanNames.COOKIE_HELPER) private val cookieHelper: CookieHelper,
  @Qualifier(BeanNames.MESSAGES_LOCALIZATION_UTIL) private val messagesLocalizationUtil: LocalizationUtil,
  @Value("\${unfinished.custodial-wallet.display.add_contact.enabled}") private val showAddContact: Boolean,
  @Value("\${unfinished.custodial-wallet.revocation.enabled}") private val revocationEnabled: Boolean,
  @Value("\${unfinished.show.track.trace.front.end}") private val showStackTraceOnFrontEnd: Boolean,
  @Value("\${unfinished.custodial-wallet.postMessage.targetOrigin}") private val targetOrigin: String,
  @Value("\${sentry.environment}") private val sentryEnv: String,
  @Value("\${sentry.release}") private val sentryRelease: String,
  @Value("\${unfinished.custodial-wallet.password.enabled}") private val passwordEnabled: Boolean,
  @Value("\${unfinished.custodial-wallet.environment}") private val environment: Environment,
  @Value("\${unfinished.custodial-wallet.account.passkey-wallet.enabled}") private val passkeyWalletEnabled: Boolean,
  @Value("\${unfinished.custodial-wallet.account.change-handle.enabled}") private val changeHandleEnabled: Boolean,
): AbstractWebController(showStackTraceOnFrontEnd, targetOrigin, objectMapper, messagesLocalizationUtil){

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(WebHtmlController::class.java)
  }

  @GetMapping("login/email")
  suspend fun handleAuthenticationEmailLink(request: ServerHttpRequest, response: ServerHttpResponse, model: Model, locale: Locale): String {
    val sessionId = request.queryParams.getFirst("sessionId") ?: throw ApiException(ApiError.MISSING_SESSION_ID, "Session ID not given")
    val authenticationCode = request.queryParams.getFirst("authenticationCode") ?: throw ApiException(ApiError.MISSING_AUTHENTICATION_CODE, "Authentication code not given")
    return loginToAccountPage(sessionId, authenticationCode, request, response, model, locale)
  }

  @PostMapping("login/sms")
  suspend fun handleAuthenticationSms(@CookieValue(SESSION_ID_COOKIE_NAME) sessionId: String, @ModelAttribute authenticationRequestBody: SmsCodeFormCommandObject, request: ServerHttpRequest, response: ServerHttpResponse, model: Model, locale: Locale): String {
    val authenticationCode = authenticationRequestBody.smsCode
    return loginToAccountPage(sessionId, authenticationCode, request, response, model, locale)
  }

  private suspend fun loginToAccountPage(sessionId: String, authenticationCode: String, request: ServerHttpRequest, response: ServerHttpResponse, model: Model, locale: Locale): String {
    return ContextLoggerHelper.logContext(request, sessionId) {
      when(val accountInfoOrRedirect = loginUser(sessionId, authenticationCode, locale, response)) {
        is Either.Left -> populateAccountPage(sessionId, model, locale, accountInfoOrRedirect.value, false)
        is Either.Right -> accountInfoOrRedirect.value
      }
    }
  }

  @GetMapping("account")
  suspend fun displayAccountPage(@CookieValue(SESSION_ID_COOKIE_NAME) sessionId: String, request: ServerHttpRequest, response: ServerHttpResponse, model: Model, locale: Locale): String {
    return returnToAccountPage(sessionId, request, model, locale)
  }

  @GetMapping("login/resume")
  suspend fun resumeLogin(@CookieValue(SESSION_ID_COOKIE_NAME) sessionId: String, request: ServerHttpRequest, response: ServerHttpResponse, model: Model, locale: Locale): String {
    return returnToAccountPage(sessionId, request, model, locale)
  }

  private suspend fun returnToAccountPage(sessionId: String, request: ServerHttpRequest, model: Model, locale: Locale): String {
    return ContextLoggerHelper.logContext(request, sessionId) {
      val session = custodialWalletOrchestrationService.authenticateLoggedIn(sessionId, locale)

      when(val accountInfoOrRedirect = custodialWalletOrchestrationService.retrieveAccountInfoOrCallback(session)) {
        is Either.Left -> populateAccountPage(sessionId, model, locale, accountInfoOrRedirect.value, false)
        is Either.Right -> accountInfoOrRedirect.value
      }
    }
  }

  //TODO This should probably be moved in the orchestration layer as it's ... orchestrating
  private suspend fun loginUser(sessionId: String, authenticationCode: String, locale: Locale, response: ServerHttpResponse): Either<AccountInfo, String> {
    val websiteSession = custodialWalletOrchestrationService.authenticateLogin(sessionId, authenticationCode, locale)
    val newSession = custodialWalletOrchestrationService.createLoggedInSession(websiteSession)

    val newSessionId = newSession.id ?: throw ApiException(
      ApiError.MISSING_SESSION_ID,
      "The new session doesn't have a sessionId for old sessionId=${sessionId}"
    )

    response.addCookie(cookieHelper.createResponseCookie(newSessionId))
    return custodialWalletOrchestrationService.retrieveAccountInfoOrCallback(newSession)
  }

  suspend fun populateAccountPage(sessionId: String, model: Model, locale: Locale, accountInfo: AccountInfo, isContactAdded: Boolean, verificationCallbackUrl: String? = null): String {
    val messageMapJson = objectMapper.writeValueAsString(messagesLocalizationUtil.getEscapedMessagesForLocale(locale))
    model.addAttribute("accountInfo", accountInfo)
    model.addAttribute("accountInfoJson", objectMapper.writeValueAsString(accountInfo))
    model.addAttribute("confirmRevoke", "")
    model.addAttribute("contactAdded", isContactAdded)
    model.addAttribute("env", environment)
    model.addAttribute("isAccount", true)
    model.addAttribute("isPrivacy", false)
    model.addAttribute("isTerms", false)
    model.addAttribute("messagesJson", messageMapJson)
    model.addAttribute("passwordEnabled", passwordEnabled)
    model.addAttribute("revocationEnabled", revocationEnabled)
    model.addAttribute("sentryEnv", sentryEnv)
    model.addAttribute("sentryRelease", sentryRelease)
    model.addAttribute("showAddContact", showAddContact)
    model.addAttribute("verificationCallbackUrl", verificationCallbackUrl)
    model.addAttribute("passkeyWalletEnabled", passkeyWalletEnabled)
    model.addAttribute("changeHandleEnabled", changeHandleEnabled)
    return "website/account"
  }

  @GetMapping("add/email")
  suspend fun handleVerificationEmailLink(request: ServerHttpRequest, response: ServerHttpResponse, model: Model, locale: Locale): String {
    val sessionId = request.queryParams.getFirst("sessionId") ?: throw ApiException(ApiError.MISSING_SESSION_ID, "Session ID not given")
    val verificationCode = request.queryParams.getFirst("verificationCode") ?: throw ApiException(ApiError.MISSING_AUTHENTICATION_CODE, "Verification code not given")
    return ContextLoggerHelper.logContext(request, sessionId) {
      addUserIdentifier(sessionId, verificationCode, model, locale)
    }
  }

  @PostMapping("add/sms")
  suspend fun handleVerificationSms(@CookieValue(SESSION_ID_COOKIE_NAME) sessionId: String, @ModelAttribute verificationCodeRequestBody: SmsCodeFormCommandObject, request: ServerHttpRequest, response: ServerHttpResponse, model: Model, locale: Locale): String {
    val verificationCode = verificationCodeRequestBody.smsCode
    return ContextLoggerHelper.logContext(request, sessionId) {
      addUserIdentifier(sessionId, verificationCode, model, locale)
    }
  }

  suspend fun addUserIdentifier(sessionId: String, verificationCode: String, model: Model, locale: Locale): String {
    val addIdentifierVerificationResponse = custodialWalletOrchestrationService.handleAddNewIdentifierVerification(sessionId, verificationCode)
    return populateAccountPage(sessionId, model, locale, addIdentifierVerificationResponse.accountInfo, addIdentifierVerificationResponse.isVerified, addIdentifierVerificationResponse.callbackUrl)
  }

  @GetMapping("logout")
  suspend fun logout(@CookieValue(SESSION_ID_COOKIE_NAME) sessionId: String, request: ServerHttpRequest, model: Model, locale: Locale): String {
    return ContextLoggerHelper.logContext(request, sessionId) {
      custodialWalletOrchestrationService.amplicaAccessLogout(sessionId)
      "redirect:/index.html"
    }
  }
}

@Profile("dev")
@RequestMapping("web/exception")
@Controller
class WebExceptionController(
  objectMapper: ObjectMapper,
  @Qualifier(BeanNames.MESSAGES_LOCALIZATION_UTIL) private val messagesLocalizationUtil: LocalizationUtil,
  @Value("\${unfinished.show.track.trace.front.end}") private val showStackTraceOnFrontEnd: Boolean,
  @Value("\${unfinished.custodial-wallet.postMessage.targetOrigin}") private val targetOrigin: String,

  ): AbstractWebController(showStackTraceOnFrontEnd, targetOrigin, objectMapper, messagesLocalizationUtil) {
  companion object{
    private val LOG: Logger = LoggerFactory.getLogger(WebExceptionController::class.java)
  }
  @GetMapping("{apiErrorId}")
  fun throwApiException(@PathVariable("apiErrorId") apiErrorId: Int) {
    //Find the ApiError by id
    //throw an ApiException
    val apiError = ApiError.fromId(apiErrorId)
    throw ApiException(apiError, apiError.description)
  }

  @GetMapping("catchAll")
  suspend fun throwException(request: ServerHttpRequest) {
    return ContextLoggerHelper.logContext(request, null) {
      LOG.info("Outside the CompletableFuture context")
      CompletableFuture.supplyAsync(withMdc {
        LOG.info("Inside the CompletableFuture context")
      }).thenCompose { fakeClientCall() }.await()
    }
  }
}

@Controller
class StaticWebController(
  val objectMapper: ObjectMapper,
  @Qualifier(BeanNames.VERIFIABLE_CREDENTIAL_SERVICE) private val verifiableCredentialService: VerifiableCredentialService,
  @Qualifier(BeanNames.MESSAGES_LOCALIZATION_UTIL) private val messagesLocalizationUtil: LocalizationUtil,
  @Value("\${unfinished.show.track.trace.front.end}") private val showStackTraceOnFrontEnd: Boolean,
  @Value("\${unfinished.custodial-wallet.postMessage.targetOrigin}") private val targetOrigin: String,
) : AbstractWebController(showStackTraceOnFrontEnd, targetOrigin, objectMapper, messagesLocalizationUtil) {

  @GetMapping("provider-signup")
  fun providerSignupForm(): String {
    return "redirect:https://forms.gle/3WL9EYzPsYBZY13i6"
  }

  @GetMapping(".well-known/did.json", produces = [MediaType.APPLICATION_JSON_VALUE])
  fun wellKnownDidJson(): ResponseEntity<String> {
    val didJson = verifiableCredentialService.getIssuerDidJson()
    return ResponseEntity.ok(didJson)
  }

  @GetMapping("learn-more")
  fun getLearnMore(): String {
    return "redirect:/#faq"
  }
}

@RestController
@RequestMapping("api/web")
class WebApiController(
  @Qualifier(BeanNames.MESSAGES_LOCALIZATION_UTIL) private val messagesLocalizationUtil: LocalizationUtil,
  @Value("\${unfinished.show.track.trace.front.end}") private val showStackTraceOnFrontEnd: Boolean,
) : AbstractApiController(showStackTraceOnFrontEnd) {
  @GetMapping("messages")
  fun getLocalizedMessages(locale: Locale, @RequestParam(name = "localeOverride", required = false) localeOverride: Locale? = null): ResponseEntity<Map<String, String>> {
    val localeToUse: Locale = localeOverride ?: locale

    return ResponseEntity.ok(messagesLocalizationUtil.getUnescapedMessagesForLocale(localeToUse))
  }
}
