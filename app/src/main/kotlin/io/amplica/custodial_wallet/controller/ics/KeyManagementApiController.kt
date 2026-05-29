package io.amplica.custodial_wallet.controller.ics

import io.amplica.custodial_wallet.client.redis.dto.IcsContextGroupKeyResponse
import io.amplica.custodial_wallet.client.redis.dto.IcsContextGroupKeySignedRequest
import io.amplica.custodial_wallet.client.redis.dto.IcsContextItemKeyResponse
import io.amplica.custodial_wallet.client.redis.dto.IcsContextItemKeySignedRequest
import io.amplica.custodial_wallet.conf.BeanNames
import io.amplica.custodial_wallet.controller.AbstractApiController
import io.amplica.custodial_wallet.orchestration.ics.IcsUserOrchestrationService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The intent here is anything for retrieving keys, ContextGroup or ContextItem
 */
@RestController
@RequestMapping("hcp/api/key")
class KeyManagementApiController(@param:Qualifier(BeanNames.ICS_USER_ORCHESTRATION_SERVICE) private val icsUserOrchestrationService: IcsUserOrchestrationService): AbstractApiController(true) {
  @PostMapping("/contextGroup")
  suspend fun retrieveContextGroupKey(@RequestBody icsContextGroupKeySignedRequest: IcsContextGroupKeySignedRequest): IcsContextGroupKeyResponse {
    return icsUserOrchestrationService.retrieveContextGroupKey(icsContextGroupKeySignedRequest)
  }

  @PostMapping("/contextItem")
  suspend fun retrieveContextItemKey(@RequestBody icsContextItemKeySignedRequest: IcsContextItemKeySignedRequest): IcsContextItemKeyResponse {
    return icsUserOrchestrationService.retrieveContextItemKey(icsContextItemKeySignedRequest)
  }
}
