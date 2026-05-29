package io.amplica.custodial_wallet.controller

import io.amplica.custodial_wallet.client.redis.dto.UserIdentifier
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifierType
import io.amplica.custodial_wallet.conf.BeanNames
import io.amplica.custodial_wallet.controller.util.BooleanHolder
import io.amplica.custodial_wallet.orchestration.deliverability.DeliverabilityOrchestrationService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("api/deliverability")
class DeliverabilityController(
  @Value("\${unfinished.enable.stack.trace}") private val enableStackTrace: Boolean,
  @Qualifier(BeanNames.DELIVERABILITY_ORCHESTRATION_SERVICE) private val deliverabilityOrchestrationService: DeliverabilityOrchestrationService,
) : AbstractApiController(enableStackTrace) {

  @GetMapping("{type}/{value}")
  suspend fun getDeliverability(
    @PathVariable("type") type: UserIdentifierType,
    @PathVariable("value") value: String,
  ): ResponseEntity<BooleanHolder> {
    val userIdentifier = UserIdentifier(value, type)
    val isDeliverable = deliverabilityOrchestrationService.getDeliverability(userIdentifier)

    return ResponseEntity.ok(BooleanHolder(isDeliverable))
  }
}