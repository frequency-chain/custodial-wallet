package io.amplica.custodial_wallet.controller

import io.amplica.custodial_wallet.conf.BeanNames
import io.amplica.custodial_wallet.orchestration.organization.OrganizationOrchestrationService
import io.amplica.custodial_wallet.orchestration.siwa.SiwaOrchestrationService
import io.amplica.custodial_wallet.orchestration.siwa.TokenResponse
import io.amplica.custodial_wallet.service.organization.OrganizationData
import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*


@Profile("dev")
@Controller
@RequestMapping("/")
class TestController {
  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(TestController::class.java)
  }

  @GetMapping("/test/redirector")
  fun getRedirector(@RequestParam("url") url: String, model: Model): String {
    LOG.debug("URL=${url}")
    model.addAttribute("url", url)
    return "test/redirector"
  }
}

@Profile("enableTesting")
@RestController
@RequestMapping("/")
class EnableTestingController(
  @Qualifier(BeanNames.SIWA_ORCHESTRATION_SERVICE) private val siwaOrchestrationService: SiwaOrchestrationService,
) {
  @GetMapping("testing/siwa/token/{siwaSessionId}")
  suspend fun getTokenForSiwaSessionId(@PathVariable("siwaSessionId") siwaSessionId: String): TokenResponse {
    return siwaOrchestrationService.getTokenForSiwaSessionId(siwaSessionId)
  }
}

@Profile("enableTesting")
@RestController
@RequestMapping("/")
class EnableTestingApiController(
  @Value("\${unfinished.enable.stack.trace}") private val enableStackTrace: Boolean,
  @Qualifier(BeanNames.ORGANIZATION_ORCHESTRATION_SERVICE) private val organizationOrchestrationService: OrganizationOrchestrationService,
) : AbstractApiController(enableStackTrace) {
  // Helper for lower environments that allows creating an organization from a public key to facilitate self-service
  // onboarding for providers.
  @PostMapping("api/admin/organization/publicKey")
  suspend fun upsertOrganizationFromPublicKey(
    @RequestBody body: PublicKeyDto
  ): ResponseEntity<OrganizationData> {
    val organizationData = organizationOrchestrationService.saveOrUpdateOrganizationFromPublicKey(body)
    return ResponseEntity.ok(organizationData)
  }
}
