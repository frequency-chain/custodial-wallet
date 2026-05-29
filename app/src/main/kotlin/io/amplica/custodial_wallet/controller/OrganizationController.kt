package io.amplica.custodial_wallet.controller

import io.amplica.custodial_wallet.conf.BeanNames
import io.amplica.custodial_wallet.dto.OrganizationDataBody
import io.amplica.custodial_wallet.dto.ProviderApplicationDataBody
import io.amplica.custodial_wallet.dto.ProviderFrequencyAccountDataBody
import io.amplica.custodial_wallet.orchestration.organization.OrganizationOrchestrationService
import io.amplica.custodial_wallet.service.organization.OrganizationData
import io.amplica.custodial_wallet.service.organization.ProviderFrequencyAccountData
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigInteger
import java.net.URI

@RestController
@RequestMapping("api/admin")
class OrganizationController @Autowired constructor(
  @Value("\${unfinished.custodial-wallet.hostname}") private val hostName: String,
  @Value("\${unfinished.custodial-wallet.admin.shared.secret}") private val adminSharedSecret: String,
  @Qualifier(BeanNames.ORGANIZATION_ORCHESTRATION_SERVICE) private val organizationOrchestrationService: OrganizationOrchestrationService,
): AbstractApiController(true) {

  /**
   * Performs the given `restrictedAction` and returns a `ResponseEntity` containing the result when the
   * provided token matches the configured admin shared secret, otherwise returns a FORBIDDEN response.
   */
  private suspend fun <A> executeAdminRestrictedAction(
    accessToken: String,
    restrictedAction: suspend () -> ResponseEntity<A>
  ): ResponseEntity<A> {
    return when (accessToken) {
      adminSharedSecret -> restrictedAction.invoke()
      else -> ResponseEntity.status(HttpStatus.FORBIDDEN).build()
    }
  }

  @GetMapping("organization/{organizationId}")
  suspend fun getOrganization(
    @PathVariable("organizationId") organizationId: BigInteger,
    @RequestParam("shared_secret") accessToken: String,
  ): ResponseEntity<OrganizationData> {
    return executeAdminRestrictedAction(accessToken) {
      val response = organizationOrchestrationService.getOrganization(organizationId)
      ResponseEntity.ok(response)
    }
  }

  @PostMapping("organization")
  suspend fun addOrganization(
    @RequestBody body: OrganizationDataBody,
    @RequestParam("shared_secret") accessToken: String,
  ): ResponseEntity<Unit> {
    return executeAdminRestrictedAction(accessToken) {
      val organizationId = organizationOrchestrationService.saveOrganization(body)
      ResponseEntity.created(URI.create("$hostName/api/admin/organization/$organizationId")).build()
    }
  }

  /**
   * Updates an existing organization. Completely overwrites all existing organization data with the new values.
   */
  @PutMapping("organization/{organizationId}")
  suspend fun updateOrganization(
    @PathVariable("organizationId") organizationId: BigInteger,
    @RequestBody body: OrganizationDataBody,
    @RequestParam("shared_secret") accessToken: String,
  ): ResponseEntity<Unit> {
    return executeAdminRestrictedAction(accessToken) {
      organizationOrchestrationService.updateOrganization(organizationId, body)
      ResponseEntity.noContent().build()
    }
  }

  /**
   * Removes any provider MSA ID rows associated with an organization--effectively soft-deleting the organization.
   */
  @DeleteMapping("organization/{organizationId}/providerFrequencyAccounts")
  suspend fun deleteOrganizationProviderFrequencyAccounts(
    @PathVariable("organizationId") organizationId: BigInteger,
    @RequestParam("shared_secret") accessToken: String,
  ): ResponseEntity<Unit> {
    return executeAdminRestrictedAction(accessToken) {
      organizationOrchestrationService.deleteOrganizationProviderFrequencyAccounts(organizationId)
      ResponseEntity.noContent().build()
    }
  }

  @GetMapping("provider/msa/{providerMsaId}")
  suspend fun getProviderFrequencyAccount(
    @PathVariable("providerMsaId") providerMsaId: BigInteger,
    @RequestParam("shared_secret") accessToken: String,
  ): ResponseEntity<ProviderFrequencyAccountData> {
    return executeAdminRestrictedAction(accessToken) {
      val response = organizationOrchestrationService.getProviderFrequencyAccountByProviderMsaId(providerMsaId)
      ResponseEntity.ok(response)
    }
  }

  /**
   * Updates an existing providerFrequencyAccount. Completely overwrites all existing provider frequency account data with the new values.
   */
  @PutMapping("provider/msa/{providerMsaId}")
  suspend fun updateProviderFrequencyAccount(
    @PathVariable("providerMsaId") providerMsaId: BigInteger,
    @RequestBody body: ProviderFrequencyAccountDataBody,
    @RequestParam("shared_secret") accessToken: String,
  ): ResponseEntity<Unit> {
    return executeAdminRestrictedAction(accessToken) {
      organizationOrchestrationService.updateProviderFrequencyAccountByProviderMsaId(providerMsaId, body)
      ResponseEntity.noContent().build()
    }
  }

  /**
   * Removes any application rows associated with a provider frequency account
   */
  @DeleteMapping("provider/msa/{providerMsaId}/applications")
  suspend fun deleteProviderFrequencyAccountApplications(
    @PathVariable("providerMsaId") providerMsaId: BigInteger,
    @RequestParam("shared_secret") accessToken: String,
  ): ResponseEntity<Unit> {
    return executeAdminRestrictedAction(accessToken) {
      organizationOrchestrationService.deleteProviderFrequencyAccountApplicationsByProviderMsaId(providerMsaId)
      ResponseEntity.noContent().build()
    }
  }

  @PostMapping("provider/msa/{providerMsaId}/applications")
  suspend fun addProviderApplication(
    @PathVariable("providerMsaId") providerMsaId: BigInteger,
    @RequestParam("shared_secret") accessToken: String,
    @RequestBody body: ProviderApplicationDataBody,
  ): ResponseEntity<Unit> {
    return executeAdminRestrictedAction(accessToken) {
      val providerApplicationId = organizationOrchestrationService.saveProviderApplication(providerMsaId, body)
      ResponseEntity.created(URI.create("$hostName/api/admin/provider/msa/$providerMsaId/providerApplications/$providerApplicationId")).build()
    }
  }

  @GetMapping("provider/msa/{providerMsaId}/applications/{providerApplicationId}")
  suspend fun getProviderApplication(
    @PathVariable("providerMsaId") providerMsaId: BigInteger,
    @PathVariable("providerApplicationId") providerApplicationId: BigInteger,
    @RequestParam("shared_secret") accessToken: String,
  ): ResponseEntity<ProviderApplicationDataBody> {
    return executeAdminRestrictedAction(accessToken) {
      val response = organizationOrchestrationService.getProviderApplication(providerMsaId, providerApplicationId)
      ResponseEntity.ok(response)
    }
  }

  @DeleteMapping("provider/msa/{providerMsaId}/applications/{providerApplicationId}")
  suspend fun deleteProviderApplication(
    @PathVariable("providerMsaId") providerMsaId: BigInteger,
    @PathVariable("providerApplicationId") providerApplicationId: BigInteger,
    @RequestParam("shared_secret") accessToken: String,
  ): ResponseEntity<Unit> {
    return executeAdminRestrictedAction(accessToken) {
      organizationOrchestrationService.deleteProviderApplication(providerMsaId, providerApplicationId)
      ResponseEntity.noContent().build()
    }
  }

}
