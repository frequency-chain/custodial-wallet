package io.amplica.custodial_wallet.controller

import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.client.redis.dto.AddIdentifierRequest
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifier
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifierType
import io.amplica.custodial_wallet.conf.BeanNames
import io.amplica.custodial_wallet.controller.util.AuditUtil
import io.amplica.custodial_wallet.controller.util.BooleanHolder
import io.amplica.custodial_wallet.db.conf.DbBeanNames
import io.amplica.custodial_wallet.db.repository.FinalizedState
import io.amplica.custodial_wallet.db.repository.Flow
import io.amplica.custodial_wallet.db.repository.State
import io.amplica.custodial_wallet.dto.ChangePasswordRequest
import io.amplica.custodial_wallet.dto.UserChangeHandleRequest
import io.amplica.custodial_wallet.dto.UserChangeHandleResponse
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.orchestration.CustodialWalletOrchestrationService
import io.amplica.custodial_wallet.orchestration.LookupOrchestrationService
import io.amplica.custodial_wallet.validator.EmailValidator
import io.amplica.custodial_wallet.validator.PhoneNumberValidator
import io.amplica.custodial_wallet.web.ContextLoggerHelper
import io.amplica.custodial_wallet.web.SESSION_ID_COOKIE_NAME
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.*
import java.math.BigInteger
import java.util.*

@RestController
@RequestMapping("api/account")
class AccountController @Autowired constructor(
  @Qualifier(BeanNames.CUSTODIAL_WALLET_ORCHESTRATION_SERVICE) private val custodialWalletOrchestrationService: CustodialWalletOrchestrationService,
  @Value("\${unfinished.enable.stack.trace}") private val enableStackTrace: Boolean,
  @Qualifier(DbBeanNames.CUSTODIAL_WALLET_DATABASE_SERVICE) private val custodialWalletDbService: CustodialWalletDatabaseService,
  @Qualifier(BeanNames.AUDIT_UTIL) private val auditUtil: AuditUtil,
  @Qualifier(BeanNames.LOOKUP_ORCHESTRATION_SERVICE) private val lookupOrchestrationService: LookupOrchestrationService,
  @Qualifier(BeanNames.PHONE_NUMBER_VALIDATOR) private val phoneNumberValidator: PhoneNumberValidator,
  @Value("\${unfinished.custodial-wallet.account.change-handle.enabled}") private val changeHandleEnabled: Boolean
) : AbstractApiController(enableStackTrace) {

  @DeleteMapping("delegations/provider/{providerMsaId}")
  suspend fun revokeDelegation(
    @PathVariable("providerMsaId") providerMsaId: BigInteger,
    request: ServerHttpRequest,
    @CookieValue(SESSION_ID_COOKIE_NAME) sessionId: String
  ): ResponseEntity<Boolean> {
    return auditUtil.aroundUpdate(
      custodialWalletDbService,
      { sessionId },
      State.REQUEST_RECEIVED,
      FinalizedState.COMPLETED
    ) {
      ContextLoggerHelper.logContext(request, sessionId) {
        val session = lookupOrchestrationService.findWebsiteSessionBySessionId(sessionId)
        val userPublicKeyHex = lookupOrchestrationService.getUserDataFromWebsiteSession(session)[0].publicKeyHex
        ResponseEntity.of(
          Optional.of(
            custodialWalletOrchestrationService.revokeDelegation(
              providerMsaId,
              userPublicKeyHex,
              sessionId
            )
          )
        )
      }
    }
  }

  @PutMapping("handle")
  suspend fun changeHandle(
    @RequestBody changeHandleRequest: UserChangeHandleRequest,
    @CookieValue(SESSION_ID_COOKIE_NAME) sessionId: String,
    request: ServerHttpRequest,
  ): ResponseEntity<UserChangeHandleResponse> {
    if(!changeHandleEnabled) {
      throw ApiException(ApiError.CHANGE_HANDLE_FAILED, "Unable to change handle for sessionId: $sessionId")
    }
    lookupOrchestrationService.validateHandle(changeHandleRequest.newHandle)
    return auditUtil.aroundUpdate(
      custodialWalletDbService,
      { sessionId },
      State.REQUEST_RECEIVED,
      FinalizedState.COMPLETED
    ) {
      ContextLoggerHelper.logContext(request, sessionId) {
        val claimedHandle = custodialWalletOrchestrationService.changeHandle(
          sessionId,
          changeHandleRequest.newHandle
        )

        ResponseEntity.of(
          Optional.of(
            UserChangeHandleResponse(claimedHandle)
          )
        )
      }
    }
  }

  @PostMapping("contact/email/verify")
 suspend fun verifyContactEmail(@CookieValue(SESSION_ID_COOKIE_NAME) sessionId: String, @RequestBody addIdentifierRequestBody: AddIdentifierRequest, locale: Locale): ResponseEntity<BooleanHolder> {
    EmailValidator.validate(addIdentifierRequestBody.newIdentifier)
    lookupOrchestrationService.validateIdentifierNotFoundOrThrow(
      UserIdentifier(addIdentifierRequestBody.newIdentifier, UserIdentifierType.EMAIL)
    )
    return auditUtil.aroundUpdate(custodialWalletDbService, { sessionId },
      State.EMAIL_SENT,
      FinalizedState.INCOMPLETE) {
      custodialWalletOrchestrationService.sendNewIdentifierVerificationEmail(addIdentifierRequestBody, sessionId, locale)
      ResponseEntity.accepted().body(BooleanHolder(true))
    }
  }

  @PostMapping("contact/sms/verify")
  suspend fun verifyContactSms(@CookieValue(SESSION_ID_COOKIE_NAME) sessionId: String, @RequestBody addIdentifierRequestBody: AddIdentifierRequest, locale: Locale): ResponseEntity<BooleanHolder> {
    phoneNumberValidator.validate(addIdentifierRequestBody.newIdentifier)
    lookupOrchestrationService.validateIdentifierNotFoundOrThrow(
      UserIdentifier(addIdentifierRequestBody.newIdentifier, UserIdentifierType.PHONE_NUMBER)
    )
    return auditUtil.aroundUpdate(
      custodialWalletDbService, { sessionId },
      State.SMS_SENT,
      FinalizedState.INCOMPLETE
    ) {
      custodialWalletOrchestrationService.sendNewIdentifierVerificationSms(addIdentifierRequestBody, sessionId, locale)
      ResponseEntity.accepted().body(BooleanHolder(true))
    }
  }

  @PutMapping("password")
  suspend fun changePassword(
    @CookieValue(SESSION_ID_COOKIE_NAME) sessionId: String,
    @RequestBody changePasswordRequest: ChangePasswordRequest,
    request: ServerHttpRequest
  ): ResponseEntity<BooleanHolder> {
    return auditUtil.aroundCreate(
      custodialWalletDbService, { sessionId },
      Flow.CHANGE_PASSWORD,
      State.PASSWORD_CHANGED,
      FinalizedState.COMPLETED
    ) {
      ContextLoggerHelper.logContext(request, sessionId) {
        custodialWalletOrchestrationService.changePassword(sessionId, changePasswordRequest)
        ResponseEntity.ok().body(BooleanHolder(true))
      }
    }
  }
}
