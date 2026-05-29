package io.amplica.custodial_wallet.controller

import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.client.redis.generateUUID
import io.amplica.custodial_wallet.conf.BeanNames
import io.amplica.custodial_wallet.controller.util.AuditUtil
import io.amplica.custodial_wallet.db.conf.DbBeanNames
import io.amplica.custodial_wallet.db.repository.FinalizedState
import io.amplica.custodial_wallet.db.repository.Flow
import io.amplica.custodial_wallet.db.repository.State
import io.amplica.custodial_wallet.dto.FinalizedHeadNumberResponse
import io.amplica.custodial_wallet.dto.LatestBlockNumberResponse
import io.amplica.custodial_wallet.orchestration.LookupOrchestrationService
import io.amplica.custodial_wallet.web.ContextLoggerHelper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("api/chain")
class ChainApiController @Autowired constructor(
  @Qualifier(BeanNames.LOOKUP_ORCHESTRATION_SERVICE) private val lookupOrchestrationService: LookupOrchestrationService,
  @Value("\${unfinished.enable.stack.trace}") private val enableStackTrace: Boolean,
  @Qualifier(DbBeanNames.CUSTODIAL_WALLET_DATABASE_SERVICE) private val custodialWalletDbService: CustodialWalletDatabaseService,
  @Qualifier(BeanNames.AUDIT_UTIL) private val auditUtil: AuditUtil
  ) : AbstractApiController(enableStackTrace) {

  @GetMapping("finalizedHead/blockNumber")
  suspend fun getFinalizedHeadBlockNumber(request: ServerHttpRequest): ResponseEntity<FinalizedHeadNumberResponse> {
    val sessionId = generateUUID()
    return auditUtil.aroundCreate(custodialWalletDbService, { sessionId }, Flow.GET_FINALIZED_HEAD_NUMBER, State.REQUEST_RECEIVED, FinalizedState.COMPLETED) {
      ContextLoggerHelper.logContext(request, sessionId) {
        ResponseEntity.of(
          Optional.of(
            lookupOrchestrationService.getFinalizedHeadNumber()
          )
        )
      }
    }
  }

  @GetMapping("latest/blockNumber")
  suspend fun getLatestBlockNumber(request: ServerHttpRequest): ResponseEntity<LatestBlockNumberResponse> {
    val sessionId = generateUUID()
    return auditUtil.aroundCreate(custodialWalletDbService, { sessionId }, Flow.LATEST_BLOCK_NUMBER, State.REQUEST_RECEIVED, FinalizedState.COMPLETED) {
      ContextLoggerHelper.logContext(request, sessionId) {
        ResponseEntity.of(
          Optional.of(
            lookupOrchestrationService.getLatestBlockNumber()
          )
        )
      }
    }
  }
}