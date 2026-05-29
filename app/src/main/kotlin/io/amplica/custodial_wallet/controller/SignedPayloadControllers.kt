package io.amplica.custodial_wallet.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.conf.BeanNames
import io.amplica.custodial_wallet.controller.util.LocalizationUtil
import io.amplica.custodial_wallet.orchestration.SignedPayloadOrchestrationService
import io.amplica.custodial_wallet.web.AUTHORIZATION_CODE_PARAMETER_NAME
import io.amplica.custodial_wallet.web.CookieHelper
import io.amplica.custodial_wallet.web.SESSION_ID_COOKIE_NAME
import io.amplica.frequency.signing_service.AddProviderPayload
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.time.Duration
import java.util.Locale

/**
 * Signed payload rest controller is the controller that will wrap the endpoints that are pure API calls and don't result
 * in an HTML view. In the SignedPayloadOrchestrationService flow we rely on the endpoints to provide the "type" information
 * but after that everything is generic
 *
 * @constructor
 *
 * @param signedPayloadOrchestrationService
 */
@RestController
@RequestMapping("api/sign")
class SignedPayloadRestController @Autowired constructor(
  @Qualifier(BeanNames.SIGNED_PAYLOAD_ORCHESTRATION_SERVICE) private val signedPayloadOrchestrationService: SignedPayloadOrchestrationService,
  @Value("\${unfinished.custodial-wallet.hostname}") private val hostName: String,
) {

  @PostMapping("batch")
  suspend fun postBatchSignature(@RequestBody batchPayloadToSignRequest: BatchPayloadToSignRequest): ResponseEntity<Unit> {

    val sessionId = signedPayloadOrchestrationService.persistBatchPayloadToSign(batchPayloadToSignRequest)
    val batchLocationUri = URI.create("$hostName/web/sign/permissions/$sessionId")
    return ResponseEntity.created(batchLocationUri).build()
  }

  @GetMapping("authorize")
  suspend fun getBatchSignedPayload(@RequestParam("sessionId") sessionId: String, @RequestParam(
    AUTHORIZATION_CODE_PARAMETER_NAME) authorizationCode: String): ResponseEntity<BatchSignedPayloadResponse> {
    val signedPayloadResponse = signedPayloadOrchestrationService.retrieveBatchSignedPayload(sessionId, authorizationCode)
    return ResponseEntity.ok(signedPayloadResponse)
  }
}

/**
 * Signed payload web controller is the controller that will wrap things that ultimately result in an HTML resource being
 * served. In the SignedPayloadOrchestrationService flow we rely on the endpoints to provide the "type" information
 *  * but after that everything is generic
 *
 * @constructor
 *
 * @param signedPayloadOrchestrationService
 */
@Controller
@RequestMapping("web/sign")
class SignedPayloadWebController @Autowired constructor(
  private val objectMapper: ObjectMapper,
  @Qualifier(BeanNames.SIGNED_PAYLOAD_ORCHESTRATION_SERVICE) private val signedPayloadOrchestrationService: SignedPayloadOrchestrationService,
  @Qualifier(BeanNames.COOKIE_HELPER) private val cookieHelper: CookieHelper,
  @Qualifier(BeanNames.MESSAGES_LOCALIZATION_UTIL) private val messagesLocalizationUtil: LocalizationUtil,
  @Value("\${unfinished.custodial-wallet.postMessage.targetOrigin}") private val targetOrigin: String,
  @Value("\${unfinished.custodial-wallet.timer.expiration}") private val retryTimeout: Duration,
  @Value("\${unfinished.custodial-wallet.redis.expiration}") private val otpTimeout: Duration,
  @Value("\${sentry.environment}") private val sentryEnv: String,
  @Value("\${sentry.release}") private val sentryRelease: String,
  @Value("\${unfinished.custodial-wallet.signup.resend_limit}") private val resendLimit: String,
) {
  @GetMapping("permissions/{sessionId}")
  suspend fun getPermissionsScreen(@PathVariable("sessionId") sessionId: String, response: ServerHttpResponse, model: Model, locale: Locale): String {
    val componentsWithContextList = signedPayloadOrchestrationService.getPermissionsContextForBatchPayloadToSign(sessionId, locale)

    val componentList = componentsWithContextList.map { componentWithContext ->
      model.addAllAttributes(componentWithContext.context)
      componentWithContext.component
    }
    model.addAttribute("componentList", componentList)
    response.addCookie(cookieHelper.createResponseCookie(sessionId))
    return "oauth/batchPayloadToSignPermissions"
  }

  @PostMapping("accept")
  suspend fun postAcceptPayloadPermissions(@CookieValue(SESSION_ID_COOKIE_NAME) sessionId: String, model: Model, locale: Locale): String {
    // Should this also be sending the code to the user via email/sms?
    val userIdentifier = signedPayloadOrchestrationService.sendAuthenticationCode(sessionId, locale)
    model.addAttribute("targetOrigin", targetOrigin)
    model.addAttribute("otpTimeout", otpTimeout.toMinutes())
    model.addAttribute("retryTimeout", retryTimeout.toMillis())
    model.addAttribute("sessionId", sessionId)
    model.addAttribute("resendLimit", resendLimit)
    model.addAttribute("sentryEnv", sentryEnv)
    model.addAttribute("sentryRelease", sentryRelease)

    val messageMapJson = objectMapper.writeValueAsString(messagesLocalizationUtil.getEscapedMessagesForLocale(locale))
    model.addAttribute("messagesJson", messageMapJson)

    return if(userIdentifier.type == UserIdentifierType.EMAIL){
      "oauth/payloadToSignEmail"
    }else{
      "oauth/payloadToSignSms"
    }
  }

  @GetMapping("verify")
  suspend fun getSignedPayloadRedirectFromEmail(@RequestParam("sessionId") sessionId: String, @RequestParam("authenticationCode") authenticationCode: String): String {
    return getSignedPayloadRedirect(sessionId, authenticationCode)
  }

  @PostMapping("verify")
  suspend fun getSignedPayloadRedirectFromSmsFormSubmission(@RequestParam("sessionId") sessionId: String, @RequestParam("authenticationCode") authenticationCode: String): String {
    return getSignedPayloadRedirect(sessionId, authenticationCode)
  }

  private suspend fun getSignedPayloadRedirect(sessionId: String, authenticationCode: String): String {
    val authorizationCodeAndCallback = signedPayloadOrchestrationService.generateAuthorizationCode(sessionId, authenticationCode)
    val redirectUriComponents = UriComponentsBuilder.fromUriString(authorizationCodeAndCallback.callback).queryParam("authorizationCode", authorizationCodeAndCallback.authorizationCode).build()
    return "redirect:${redirectUriComponents.toUriString()}"
  }
}
