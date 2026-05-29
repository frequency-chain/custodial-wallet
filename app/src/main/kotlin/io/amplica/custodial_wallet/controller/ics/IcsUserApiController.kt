package io.amplica.custodial_wallet.controller.ics

import io.amplica.custodial_wallet.client.redis.dto.IcsRetrievePayloadsResponse
import io.amplica.custodial_wallet.client.redis.dto.IcsRetrievePayloadsSignedRequest
import io.amplica.custodial_wallet.conf.BeanNames
import io.amplica.custodial_wallet.controller.AbstractApiController
import io.amplica.custodial_wallet.orchestration.ics.IcsUserOrchestrationService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The intent here is user related APIs, this may only be headless creation but maybe my.frequency related APIs as well
 * as those are done on behalf of users?
 */
@RestController
@RequestMapping("hcp/api/user")
class IcsUserApiController(
  @param:Qualifier(BeanNames.ICS_USER_ORCHESTRATION_SERVICE) private val icsUserOrchestrationService: IcsUserOrchestrationService
) : AbstractApiController(true) {

  @PostMapping("payloads")
  suspend fun retrieveHcpUserPayloads(@RequestBody request: IcsRetrievePayloadsSignedRequest): IcsRetrievePayloadsResponse {
    return icsUserOrchestrationService.retrieveUserPayloads(request)
  }

}