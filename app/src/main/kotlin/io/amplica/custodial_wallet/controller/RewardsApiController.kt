package io.amplica.custodial_wallet.controller

import io.amplica.custodial_wallet.conf.BeanNames
import io.amplica.custodial_wallet.orchestration.LookupOrchestrationService
import io.amplica.custodial_wallet.orchestration.community_rewards.CommunityRewardsOrchestrationService
import io.amplica.custodial_wallet.web.ContextLoggerHelper
import io.amplica.custodial_wallet.web.SESSION_ID_COOKIE_NAME
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("api/rewards")
class RewardsApiController @Autowired constructor(
  @Qualifier(BeanNames.LOOKUP_ORCHESTRATION_SERVICE) private val lookupOrchestrationService: LookupOrchestrationService,
  @Qualifier(BeanNames.COMMUNITY_REWARDS_ORCHESTRATION_SERVICE) private val communityRewardsOrchestrationService: CommunityRewardsOrchestrationService,
  @Value("\${unfinished.enable.stack.trace}") private val enableStackTrace: Boolean,
) : AbstractApiController(enableStackTrace) {
  @PostMapping("optIn")
  suspend fun optInToCommunityRewards(
    @CookieValue(SESSION_ID_COOKIE_NAME, required = true) sessionId: String,
    request: ServerHttpRequest
  ): ResponseEntity<Void> {
    return ContextLoggerHelper.logContext(request, sessionId) {
      communityRewardsOrchestrationService.optInAuthenticatedSiwaOrWebsiteSession(sessionId)

      ResponseEntity.noContent().build()
    }
  }
}
