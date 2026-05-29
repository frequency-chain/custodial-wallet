package io.amplica.custodial_wallet.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.amplica.custodial_wallet.client.redis.dto.SiwaIdentifierAndCaptchaToken
import io.amplica.custodial_wallet.client.redis.dto.SiwaRequest
import io.amplica.custodial_wallet.client.redis.dto.SiwaSmsCode
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifierType
import io.amplica.custodial_wallet.conf.BeanNames
import io.amplica.custodial_wallet.controller.util.LocalizationUtil
import io.amplica.custodial_wallet.controller.util.NormalizationUtil
import io.amplica.custodial_wallet.controller.util.ThymeleafHelper
import io.amplica.custodial_wallet.controller.util.getClientIpAddress
import io.amplica.custodial_wallet.dto.*
import io.amplica.custodial_wallet.orchestration.LookupOrchestrationService
import io.amplica.custodial_wallet.orchestration.siwa.*
import io.amplica.custodial_wallet.util.base64UrlEncode
import io.amplica.custodial_wallet.util.fromBase64Url
import io.amplica.custodial_wallet.validator.MEWE_TEST_PHONE_PREFIX
import io.amplica.custodial_wallet.web.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.util.*


@Controller
@RequestMapping("siwa")
class SiwaController @Autowired constructor(
  @Qualifier(BeanNames.SIWA_ORCHESTRATION_SERVICE) private val siwaOrchestrationService: SiwaOrchestrationService,
  @Qualifier(BeanNames.LOOKUP_ORCHESTRATION_SERVICE) private val lookupOrchestrationService: LookupOrchestrationService,
  @Qualifier(BeanNames.COOKIE_HELPER) private val cookieHelper: CookieHelper,
  val objectMapper: ObjectMapper,
  @Value("\${unfinished.show.track.trace.front.end}") private val showStackTraceOnFrontEnd: Boolean,
  @Value("\${unfinished.custodial-wallet.postMessage.targetOrigin}") private val targetOrigin: String,
  @Value("\${unfinished.custodial-wallet.environment}") private val environment: Environment,
  @Qualifier(BeanNames.NORMALIZATION_UTIL) private val normalizationUtil: NormalizationUtil,
  @Qualifier(BeanNames.MATOMO_PROPERTIES) private val matomoProps: MatomoProps,
  @Qualifier(BeanNames.MESSAGES_LOCALIZATION_UTIL) private val messagesLocalizationUtil: LocalizationUtil,
  @Value("\${sentry.environment}") private val sentryEnv: String,
  @Value("\${sentry.release}") private val sentryRelease: String,
) : AbstractSiwaController(
  showStackTraceOnFrontEnd,
  targetOrigin,
  objectMapper,
  environment,
  siwaOrchestrationService,
  lookupOrchestrationService,
  matomoProps,
  messagesLocalizationUtil,
  sentryEnv,
  sentryRelease,
) {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(WebHtmlController::class.java)
  }

  private fun addGlobalModelAttributes(model: Model) {
    val messageMapJson = objectMapper.writeValueAsString(messagesLocalizationUtil.getEscapedMessagesForLocale(Locale.US))
    model.addAttribute("messagesJson", messageMapJson)

    model.addAttribute("helper", ThymeleafHelper)
    model.addAttribute("env", environment)
    model.addAttribute("sentryEnv", sentryEnv)
    model.addAttribute("sentryRelease", sentryRelease)
  }

  private fun handleOrchestrationResponse(
    response: SiwaResponse<Any>,
    httpResponse: ServerHttpResponse,
    model: Model,
  ): String {
    return when (response) {
      is CallbackResponse -> {
        // Before callbacks we need to remove the X-Captcha header if present
        // since it is not a CORS-safelisted request header. This can cause issues with redirects
        // https://developer.mozilla.org/en-US/docs/Glossary/CORS-safelisted_request_header
        httpResponse.headers.remove(X_CAPTCHA_NAME)
        val callbackWithAuthorizationAppended = UriComponentsBuilder.fromUriString(response.callbackUrl)
          .queryParam(AUTHORIZATION_CODE_PARAMETER_NAME, response.authorizationCode).encode().build().toUriString()
        LOG.info("Redirecting to callbackUrl={}", callbackWithAuthorizationAppended)
        return "redirect:$callbackWithAuthorizationAppended"
      }

      is ViewResponse -> {
        if(response.sessionId != null) {
          httpResponse.addCookie(cookieHelper.createResponseCookie(response.sessionId))
        }

        addGlobalModelAttributes(model)
        model.addAttribute("props", response.model)
        model.addAttribute("matomo", matomoProps.withData(response.matomo))

        response.template
      }

      is RedirectResponse -> {
        return "redirect:${
          UriComponentsBuilder.fromUri(response.location).queryParam("sessionId", response.sessionId).build()
            .toUriString()
        }"
      }
    }
  }

  private suspend fun handleStartViewResponse(
    responseSupplier: suspend () -> ViewResponse<SiwaProps>,
    response: ServerHttpResponse,
    model: Model,
  ): String {
    val orchestrationResponse = responseSupplier()
    return handleOrchestrationResponse(orchestrationResponse, response, model)
  }

  @GetMapping("start/{sessionId}")
  suspend fun getStart(
    @PathVariable("sessionId") sessionId: String,
    request: ServerHttpRequest,
    response: ServerHttpResponse,
    model: Model,
  ): String {
    return ContextLoggerHelper.logContext(request, sessionId) {
      handleStartViewResponse(
        { siwaOrchestrationService.acceptSavedSiwaRequestBySessionId(sessionId) },
        response,
        model
      )
    }
  }

  @GetMapping("start")
  suspend fun getStartWithSignedRequest(
    @CookieValue(SESSION_ID_COOKIE_NAME, required = false) sessionId: String?,
    @RequestParam(SIGNED_REQUEST_PARAMETER_NAME, required = true) signedRequest: String,
    @RequestParam(USER_KEY_TYPE, required = false) userKeyType: String?,
    request: ServerHttpRequest,
    response: ServerHttpResponse,
    model: Model,
  ): String {

    return ContextLoggerHelper.logContext(request, null) {
      val siwaRequestBytes = fromBase64Url(signedRequest)
      val siwaRequest = objectMapper.readValue(siwaRequestBytes, SiwaRequest::class.java)
      val additionalParams = request.queryParams
      handleStartViewResponse({ siwaOrchestrationService.acceptSiwaRequest(siwaRequest, additionalParams, sessionId) }, response, model)
    }
  }

  /**
   * Provider submits a request on behalf of the user to kick off the SiWA process, and this method renders the page
   * for entering user identifier (email/sms).
   */
  @PostMapping("start")
  suspend fun providerRequest(
    @CookieValue(SESSION_ID_COOKIE_NAME, required = false) sessionId: String?,
    @RequestBody siwaRequest: SiwaRequest,
    request: ServerHttpRequest,
    response: ServerHttpResponse,
    model: Model,
    locale: Locale,
    @RequestParam(USER_KEY_TYPE, required = false) userKeyType: String?, //Here for contract purposes
  ): String {
    return ContextLoggerHelper.logContext(request, null) {
      handleStartViewResponse({ siwaOrchestrationService.acceptSiwaRequest(siwaRequest, request.queryParams, sessionId) }, response, model)
    }
  }

  /**
   * This will accept a user identifier (e.g., sms, email) and render either a page to enter SMS code or informing
   * the user to check their email.
   */
  @PostMapping("verify")
  suspend fun sendVerification(
    @CookieValue(SESSION_ID_COOKIE_NAME) sessionId: String,
    @ModelAttribute siwaIdentifierAndCaptchaToken: SiwaIdentifierAndCaptchaToken,
    @RequestHeader(OVERRIDE_BLOCKING_SECRET_NAME, required = false) overrideBlockingSecret: String?,
    request: ServerHttpRequest,
    response: ServerHttpResponse,
    model: Model,
    locale: Locale
  ): String {
     return ContextLoggerHelper.logContext(request, sessionId) {
       val userIdentifierValue = siwaIdentifierAndCaptchaToken.value
       val userIdentifierType = siwaIdentifierAndCaptchaToken.type
       val normalizedValue = if(userIdentifierType == UserIdentifierType.PHONE_NUMBER && userIdentifierValue.startsWith(MEWE_TEST_PHONE_PREFIX)){
         userIdentifierValue
       }else{
         normalizationUtil.normalizeContactMethod(siwaIdentifierAndCaptchaToken.value, siwaIdentifierAndCaptchaToken.type,)
       }

       val requestHeaders = request.headers
       val userIp = getClientIpAddress(requestHeaders)

       val normalizedSiwaIdentifierAndCaptcha = siwaIdentifierAndCaptchaToken.withValue(normalizedValue)
       val orchestrationResponse = siwaOrchestrationService.acceptUserIdentifier(
         sessionId,
         normalizedSiwaIdentifierAndCaptcha,
         userIp,
         overrideBlockingSecret,
         locale,
         requestHeaders.getFirst(X_CAPTCHA_NAME)
       )
       handleOrchestrationResponse(orchestrationResponse, response, model)
    }
  }

  /**
   * Invoked when the user clicks on a link in their email and renders the page showing handle input and
   * payloads acceptance (if needed).
   */
  @GetMapping("payloads")
  suspend fun handleMagicLinkVerificationAndDisplayPayloads(
    @RequestParam("sessionId") sessionId: String,
    @RequestParam("token", required = false) authenticationCode: String?,
    request: ServerHttpRequest,
    response: ServerHttpResponse,
    model: Model,
    locale: Locale
  ): String {
    return ContextLoggerHelper.logContext(request, sessionId) {
      val orchestrationResponse = siwaOrchestrationService.acceptAuthenticationCode(authenticationCode, sessionId)
      handleOrchestrationResponse(orchestrationResponse, response, model)
    }
  }

  /**
   * Invoked when the user enters a verification code and renders the page showing handle input and payloads
   * acceptance (if needed).
   */
  @PostMapping("payloads")
  suspend fun handleOTPVerificationAndDisplayPayloads(
    @CookieValue(SESSION_ID_COOKIE_NAME) sessionId: String,
    @ModelAttribute siwaSmsCode: SiwaSmsCode,
    request: ServerHttpRequest,
    response: ServerHttpResponse,
    model: Model,
    locale: Locale
  ): String {
    return ContextLoggerHelper.logContext(request, sessionId) {
      val orchestrationResponse = siwaOrchestrationService.acceptAuthenticationCode(siwaSmsCode.smsCode, sessionId)
      handleOrchestrationResponse(orchestrationResponse, response, model)
    }
  }

  @PostMapping("accepted")
  suspend fun handlePayloadsAcceptedRedirectToProvider(
    @CookieValue(SESSION_ID_COOKIE_NAME) sessionId: String,
    @ModelAttribute body: UserPayloadsAcceptanceAndDataCommand,
    request: ServerHttpRequest,
    response: ServerHttpResponse,
    model: Model,
    locale: Locale
  ): String {
    return ContextLoggerHelper.logContext(request, sessionId) {
      val orchestrationResponse = siwaOrchestrationService.acceptAcceptanceAndData(sessionId, body)
      handleOrchestrationResponse(orchestrationResponse, response, model)
    }
  }

  @GetMapping("rewards")
  suspend fun getRewardsPage(
    @CookieValue(SESSION_ID_COOKIE_NAME) sessionId: String,
    @ModelAttribute body: UserPayloadsAcceptanceAndDataCommand,
    request: ServerHttpRequest,
    response: ServerHttpResponse,
    model: Model,
    locale: Locale
  ): String {
    addGlobalModelAttributes(model)
    return "siwa/communityRewardsDemo"
  }
}

@RestController
@RequestMapping("siwa/api")
class SiwaApiController @Autowired constructor(
  @Qualifier(BeanNames.SIWA_ORCHESTRATION_SERVICE) private val siwaOrchestrationService: SiwaOrchestrationService,
  private val objectMapper: ObjectMapper,
  @Value("\${unfinished.enable.stack.trace}") private val enableStackTrace: Boolean,
  @Value("\${unfinished.custodial-wallet.hostname}") private val hostName: String,
  @Value("\${unfinished.custodial-wallet.siwa.use.get.ingress}") private val useGetIngress: Boolean,
) : AbstractApiController(enableStackTrace) {

  /**
   * Provider server side submits a request behalf of the user to kick off the SiWA process, it returns a "claim check"
   * for another Client UserAgent to use to kick off the siwa process
   */
  @PostMapping("request")
  suspend fun saveSiwaRequest(
    @CookieValue(SESSION_ID_COOKIE_NAME, required = false) sessionId: String?,
    @RequestBody siwaRequest: SiwaRequest,
    request: ServerHttpRequest
  ): ResponseEntity<Unit> {
    return ContextLoggerHelper.logContext(request, null) {
      val location: URI
      if (useGetIngress) {
        val siwaRequestString = objectMapper.writeValueAsString(siwaRequest)
        location = URI.create(
          UriComponentsBuilder.fromUriString(
            "${hostName}/siwa/start?$SIGNED_REQUEST_PARAMETER_NAME=${
              base64UrlEncode(siwaRequestString.toByteArray(Charsets.UTF_8))
            }"
          ).queryParams(request.queryParams).toUriString()
        )
      } else {
        val newSessionId = ContextLoggerHelper.logContext(request, null) {
          siwaOrchestrationService.saveSiwaRequest(siwaRequest, sessionId)
        }
        location = URI.create("${hostName}/siwa/start/${newSessionId}")
      }
      ResponseEntity.created(location).build()
    }
  }

  @GetMapping("payload")
  suspend fun retrievePayload(
    @RequestParam(AUTHORIZATION_CODE_PARAMETER_NAME) authorizationCode: String,
    request: ServerHttpRequest
  ): ResponseEntity<SiwaPayloadResponse> {
    return ContextLoggerHelper.logContext(request, null) {
      val response = siwaOrchestrationService.retrieveSiwaPayload(authorizationCode)

      ResponseEntity.ok(response)
    }
  }

  @GetMapping("submission/{submissionId}")
  suspend fun getAsyncSubmission(
    @PathVariable submissionId: String,
    request: ServerHttpRequest
  ): ResponseEntity<AsyncSubmissionResponse> {
    return ContextLoggerHelper.logContext(request, null) {
      val response = siwaOrchestrationService.getAsyncSubmission(submissionId)

      ResponseEntity.ok(response)
    }
  }
}
