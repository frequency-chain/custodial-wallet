package io.amplica.custodial_wallet.controller

import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.conf.BeanNames
import io.amplica.custodial_wallet.controller.util.AuditUtil
import io.amplica.custodial_wallet.controller.util.DeferredSupplier
import io.amplica.custodial_wallet.controller.util.NormalizationUtil
import io.amplica.custodial_wallet.controller.util.SessionIdHolder
import io.amplica.custodial_wallet.controller.util.getClientIpAddress
import io.amplica.custodial_wallet.db.conf.DbBeanNames
import io.amplica.custodial_wallet.db.repository.FinalizedState
import io.amplica.custodial_wallet.db.repository.Flow
import io.amplica.custodial_wallet.db.repository.State
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.orchestration.CustodialWalletOrchestrationService
import io.amplica.custodial_wallet.validator.EmailValidator
import io.amplica.custodial_wallet.web.ContextLoggerHelper
import io.amplica.custodial_wallet.web.CookieHelper
import io.amplica.custodial_wallet.web.X_CAPTCHA_NAME
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

class DirectLoginRequestCleaner(private val normalizationUtil: NormalizationUtil) {
  fun clean(directLoginRequest: DirectLoginRequest): DirectLoginRequest {
    val normalizedContactMethod =
      normalizationUtil.normalizeContactMethod(directLoginRequest.contactMethod, directLoginRequest.contactMethodType)
    return DirectLoginRequest(
      normalizedContactMethod,
      directLoginRequest.contactMethodType,
      directLoginRequest.callbackUrl,
      directLoginRequest.captchaToken,
    )
  }

  fun clean(passwordDirectLoginRequest: PasswordDirectLoginRequest): PasswordDirectLoginRequest {
    val contactMethodType = checkContactMethodType(passwordDirectLoginRequest.username)
    val normalizedContactMethod =
      normalizationUtil.normalizeContactMethod(passwordDirectLoginRequest.username, contactMethodType)
    return PasswordDirectLoginRequest(
      normalizedContactMethod,
      passwordDirectLoginRequest.password,
      passwordDirectLoginRequest.callbackUrl
    )
  }

  fun checkContactMethodType(username: String): UserIdentifierType {
    return if(EmailValidator.isValid(username)) {
      UserIdentifierType.EMAIL
    } else {
      UserIdentifierType.PHONE_NUMBER
    }
  }
}

data class AuthenticationRequest(
  val authenticationCode: String,
  val sessionId: String
)

@RestController
@RequestMapping("api/login")
class LoginApiController @Autowired constructor(
  @Qualifier(BeanNames.CUSTODIAL_WALLET_ORCHESTRATION_SERVICE) private val custodialWalletOrchestrationService: CustodialWalletOrchestrationService,
  @Value("\${unfinished.enable.stack.trace}") private val enableStackTrace: Boolean,
  @Qualifier(DbBeanNames.CUSTODIAL_WALLET_DATABASE_SERVICE) private val custodialWalletDbService: CustodialWalletDatabaseService,
  @Qualifier(BeanNames.AUDIT_UTIL) private val auditUtil: AuditUtil,
  @Qualifier(BeanNames.DIRECT_LOGIN_REQUEST_CLEANER) private val directLoginRequestCleaner: DirectLoginRequestCleaner,
  @Qualifier(BeanNames.COOKIE_HELPER) private val cookieHelper: CookieHelper
) : AbstractApiController(enableStackTrace) {

  @PostMapping("direct")
  suspend fun directWalletLogin(
    @RequestBody directLoginRequest: DirectLoginRequest,
    response: ServerHttpResponse,
    request: ServerHttpRequest,
    locale: Locale
  ): ResponseEntity<Boolean> {
    val normalizedDirectLoginRequest = directLoginRequestCleaner.clean(directLoginRequest)
    val isEmail = normalizedDirectLoginRequest.contactMethodType == UserIdentifierType.EMAIL
    val deferredSessionId = DeferredSupplier<String>()
    val requestHeaders = request.headers
    val userIp = getClientIpAddress(requestHeaders)
    val xCaptchaHeaderValue = requestHeaders.getFirst(X_CAPTCHA_NAME)

    return auditUtil.aroundCreate(
      custodialWalletDbService,
      deferredSessionId,
      Flow.DIRECT_LOGIN,
      State.URL_SENT,
      FinalizedState.INCOMPLETE
    ) {
      deferredSessionId.value = custodialWalletOrchestrationService.sendLoginUrl(
        normalizedDirectLoginRequest,
        locale,
        userIp,
        xCaptchaHeaderValue,
      )
      response.addCookie(cookieHelper.createResponseCookie(deferredSessionId.value!!))
      ResponseEntity.ok(isEmail)
    }
  }

  @PostMapping("authorizationCode")
  suspend fun validateAuthorizationCode(@RequestBody authorizationCodeRequest: AuthorizationCodeRequest, request: ServerHttpRequest): ResponseEntity<AuthorizationWebsiteSessionResponse> {
    val sessionId = authorizationCodeRequest.sessionId
    return auditUtil.aroundUpdate(custodialWalletDbService, { sessionId }, State.PAYLOAD_DELIVERED, FinalizedState.COMPLETED) {
      ContextLoggerHelper.logContext(request, sessionId) {
        val authorizationWebsiteSessionResponse = custodialWalletOrchestrationService.loginValidateAuthorizationCode(authorizationCodeRequest)
        ResponseEntity.ok(authorizationWebsiteSessionResponse)
      }
    }
  }

  /**
   * Authenticates the provided session ID and authentication code, and returns the new logged-in session ID
   */
  @PostMapping("authenticate")
  suspend fun webviewAuthenticate(
    @RequestBody body: AuthenticationRequest, request: ServerHttpRequest, locale: Locale, response: ServerHttpResponse
  ): ResponseEntity<SessionIdHolder> {
    val (authenticationCode, sessionId) = body
    return auditUtil.aroundUpdate(
      custodialWalletDbService,
      { sessionId },
      State.TOKEN_VALIDATED,
      FinalizedState.INCOMPLETE
    ) {
      ContextLoggerHelper.logContext(request, sessionId) {
        val websiteSession =
          custodialWalletOrchestrationService.authenticateLogin(sessionId, authenticationCode, locale)
        val newSession = custodialWalletOrchestrationService.createLoggedInSession(websiteSession)

        val newSessionId = newSession.id ?: throw ApiException(
          ApiError.MISSING_SESSION_ID,
          "The new session doesn't have a sessionId for old sessionId=${sessionId}"
        )

        val newSessionIdCookie = cookieHelper.createResponseCookie(newSessionId)
        response.addCookie(newSessionIdCookie)
        ResponseEntity.of(Optional.of(SessionIdHolder(newSessionId)))
      }
    }
  }

  @PostMapping("password")
  suspend fun authenticatePassword(
    @RequestBody passwordDirectLoginRequest: PasswordDirectLoginRequest, request: ServerHttpRequest, response: ServerHttpResponse): ResponseEntity<SessionIdHolder> {
    val normalizedPasswordDirectLoginRequest = directLoginRequestCleaner.clean(passwordDirectLoginRequest)
    val username = normalizedPasswordDirectLoginRequest.username
    val contactMethodType = directLoginRequestCleaner.checkContactMethodType(username)
    val deferredSessionId = DeferredSupplier<String>()
    return auditUtil.aroundUpdate(
      custodialWalletDbService,
      deferredSessionId,
      State.TOKEN_VALIDATED,
      FinalizedState.INCOMPLETE
    ) {
      ContextLoggerHelper.logContext(request, deferredSessionId.value) {
        val sessionId = custodialWalletOrchestrationService.authenticateUserWithPassword(normalizedPasswordDirectLoginRequest, contactMethodType)
        deferredSessionId.value = sessionId
        response.addCookie(cookieHelper.createResponseCookie(deferredSessionId.value!!))
        ResponseEntity.of(Optional.of(SessionIdHolder(sessionId)))
      }
    }
  }
}
