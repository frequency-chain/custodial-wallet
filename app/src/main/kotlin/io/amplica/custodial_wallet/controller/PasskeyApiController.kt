package io.amplica.custodial_wallet.controller

import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.conf.BeanNames
import io.amplica.custodial_wallet.controller.util.AuditUtil
import io.amplica.custodial_wallet.controller.util.BooleanHolder
import io.amplica.custodial_wallet.db.conf.DbBeanNames
import io.amplica.custodial_wallet.db.repository.FinalizedState
import io.amplica.custodial_wallet.db.repository.Flow
import io.amplica.custodial_wallet.db.repository.State
import io.amplica.custodial_wallet.orchestration.passkey.PasskeyWalletService
import io.amplica.custodial_wallet.web.ContextLoggerHelper
import io.amplica.custodial_wallet.web.SESSION_ID_COOKIE_NAME
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("api/passkey")
class PasskeyApiController @Autowired constructor(
  @Qualifier(BeanNames.PASSKEY_WALLET_SERVICE) private val passkeyWalletService: PasskeyWalletService,
  @Value("\${unfinished.enable.stack.trace}") private val enableStackTrace: Boolean,
  @Qualifier(DbBeanNames.CUSTODIAL_WALLET_DATABASE_SERVICE) private val custodialWalletDbService: CustodialWalletDatabaseService,
  @Qualifier(BeanNames.AUDIT_UTIL) private val auditUtil: AuditUtil
) : AbstractApiController(enableStackTrace) {
  @PostMapping("registration/accept")
  suspend fun parseRegistrationRequest(
    @RequestBody acceptRegistrationRequest: AcceptRegistrationRequest,
    request: ServerHttpRequest,
    @CookieValue(SESSION_ID_COOKIE_NAME) sessionId: String
  ): ResponseEntity<BooleanHolder> {
    return auditUtil.aroundUpsert(custodialWalletDbService, { sessionId }, Flow.PASSKEY_REGISTRATION, State.REGISTRATION_ACCEPTED, FinalizedState.COMPLETED) {
      ContextLoggerHelper.logContext(request, sessionId) {
        passkeyWalletService.acceptRegistrationRequest(sessionId, acceptRegistrationRequest)
        ResponseEntity.ok().body(BooleanHolder(true))
      }
    }
  }

  @GetMapping("credential/{credentialId}")
  suspend fun retrieveCredentialResponse(
    request: ServerHttpRequest,
    @CookieValue(SESSION_ID_COOKIE_NAME) sessionId: String,
    @PathVariable("credentialId") credentialId: String
  ): ResponseEntity<CredentialResponseDto> {
    return auditUtil.aroundUpdate(custodialWalletDbService, { sessionId }, State.CREDENTIAL_RETRIEVED, FinalizedState.INCOMPLETE) {
      ContextLoggerHelper.logContext(request, sessionId) {
        ResponseEntity.of(
          Optional.of(
            passkeyWalletService.retrieveCredentialAccount(sessionId, credentialId)
          )
        )
      }
    }
  }

  @GetMapping("credentials")
  suspend fun retrieveCredentialsResponse(
    request: ServerHttpRequest,
    @CookieValue(SESSION_ID_COOKIE_NAME) sessionId: String,
  ): ResponseEntity<CredentialResponsesDto> {
    return auditUtil.aroundUpdate(custodialWalletDbService, { sessionId }, State.CREDENTIAL_RETRIEVED, FinalizedState.INCOMPLETE) {
      ContextLoggerHelper.logContext(request, sessionId) {
        ResponseEntity.of(
          Optional.of(
            passkeyWalletService.retrieveCredentials(sessionId)
          )
        )
      }
    }
  }
}