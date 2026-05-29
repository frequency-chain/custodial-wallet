package io.amplica.custodial_wallet.orchestration.organization

import io.amplica.custodial_wallet.dto.OrganizationDataBody
import io.amplica.custodial_wallet.dto.ProviderApplicationDataBody
import io.amplica.custodial_wallet.dto.ProviderFrequencyAccountDataBody
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.orchestration.LookupOrchestrationService
import io.amplica.custodial_wallet.service.organization.*
import io.amplica.custodial_wallet.template.TemplateConstants
import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto
import java.math.BigInteger


open class DefaultOrganizationOrchestrationService(
  hostName: String,
  private val organizationService: OrganizationService,
  private val lookupOrchestrationService: LookupOrchestrationService,
) : OrganizationOrchestrationService {

  private val defaultProviderBrandLogo = "$hostName/img/providers/swif_example_app_brand_logo.png"

  override suspend fun getOrganization(organizationId: BigInteger): OrganizationData {
    return organizationService.getOrganization(organizationId)
  }

  private fun bodyToOrganizationData(body: OrganizationDataBody): OrganizationData {
    // NOTE(Julian, 2024-10-21): A shortcode is required in the database, so we fall back on the default
    val shortcode = when {
      body.shortcode.isNullOrEmpty() -> TemplateConstants.DEFAULT_PROVIDER_NAME
      else -> body.shortcode
    }
    return OrganizationData(
      body.msaIds.toSet(),
      body.displayName,
      shortcode,
      body.whitelistedOrigins,
      body.assets
    )
  }

  private fun assertOrganizationBodyIsValid(body: OrganizationDataBody) {
    if (body.msaIds.isEmpty()) {
      throw ApiException(ApiError.EMPTY_REQUIRED_LIST_ERROR, "'msaIds' must contain at least one value")
    }

    if (body.whitelistedOrigins.isEmpty()) {
      throw ApiException(ApiError.EMPTY_REQUIRED_LIST_ERROR, "'whitelistedOrigins' must contain at least one value")
    }
  }

  override suspend fun saveOrganization(body: OrganizationDataBody): BigInteger {
    assertOrganizationBodyIsValid(body)
    val organizationData = bodyToOrganizationData(body)

    val id = organizationService.saveOrganization(organizationData)
    return id
  }

  override suspend fun updateOrganization(organizationId: BigInteger, body: OrganizationDataBody) {
    assertOrganizationBodyIsValid(body)
    val organizationData = bodyToOrganizationData(body)

    organizationService.updateOrganization(organizationId, organizationData)
  }

  override suspend fun deleteOrganizationProviderFrequencyAccounts(organizationId: BigInteger) {
    organizationService.deleteOrganizationProviderFrequencyAccounts(organizationId)
  }

  // NOTE(Julian, 2025-06-02): This method only supports SS58 keys encoded in base58 at the moment
  override suspend fun saveOrUpdateOrganizationFromPublicKey(publicKey: PublicKeyDto): OrganizationData {
    val msaId = lookupOrchestrationService.retrieveMsaId(publicKey)
    val displayName = lookupOrchestrationService.getProviderName(msaId)

    when (val existingRow = organizationService.getOrganizationByMsaId(msaId)) {
      null -> {
        val data = OrganizationData(
          setOf(msaId),
          displayName,
          TemplateConstants.DEFAULT_PROVIDER_NAME,
          emptyList(),
          mapOf(
            AssetType.BRAND_LOGO to Asset(defaultProviderBrandLogo)
          )
        )
        organizationService.saveOrganization(data)

        return data
      }

      else -> {
        val (existingId, existingData) = existingRow
        // NOTE(Julian, 2025-06-04): Only updates the display name in case we have manually set any of the other values
        val updatedData = existingData.copy(displayName = displayName)
        organizationService.updateOrganization(existingId, updatedData)

        return updatedData
      }
    }
  }

  private fun bodyToProviderFrequencyAccountData(providerMsaId: BigInteger, providerFrequencyAccountDataBody: ProviderFrequencyAccountDataBody): ProviderFrequencyAccountData {
    // NOTE(Teddy, 2025-02-03): A shortcode is required in the database, so we fall back on the default
    val providerApplications = providerFrequencyAccountDataBody.providerApplications.map{ providerApplication ->
      val shortcode = when {
        providerApplication.shortcode.isEmpty() -> TemplateConstants.DEFAULT_PROVIDER_APPLICATION_NAME
        else -> providerApplication.shortcode
      }
      ProviderApplicationData(
        providerApplication.verifiedCredentialUrl,
        providerApplication.displayName,
        shortcode,
        providerApplication.whitelistedOrigins,
        providerApplication.assets
      )
    }

    return ProviderFrequencyAccountData(
      providerMsaId,
      providerFrequencyAccountDataBody.organizationId,
      providerApplications
    )
  }

  override suspend fun getProviderFrequencyAccountByProviderMsaId(providerMsaId: BigInteger): ProviderFrequencyAccountData {
    return organizationService.getProviderFrequencyAccountByProviderMsaId(providerMsaId)
      ?: throw ApiException(ApiError.NO_PROVIDER_FREQUENCY_ACCOUNT_FOUND, "No provider frequency account found with msaId=$providerMsaId")
  }

  override suspend fun updateProviderFrequencyAccountByProviderMsaId(
    providerMsaId: BigInteger,
    body: ProviderFrequencyAccountDataBody
  ) {
    val providerFrequencyAccountData = bodyToProviderFrequencyAccountData(providerMsaId, body)

    organizationService.updateProviderFrequencyAccountByProviderMsaId(providerMsaId, providerFrequencyAccountData)
  }

  override suspend fun deleteProviderFrequencyAccountApplicationsByProviderMsaId(providerMsaId: BigInteger) {
    organizationService.deleteProviderApplicationsByProviderMsaId(providerMsaId)
  }

  override suspend fun getProviderApplication(
    providerMsaId: BigInteger,
    providerApplicationId: BigInteger
  ): ProviderApplicationDataBody {
    val applicationData = organizationService.getProviderApplication(providerMsaId, providerApplicationId)
    return ProviderApplicationDataBody(
      applicationData.verifiedCredentialUrl,
      applicationData.displayName,
      applicationData.shortcode,
      applicationData.whitelistedOrigins,
      applicationData.assets
    )

  }

  override suspend fun saveProviderApplication(
    providerMsaId: BigInteger,
    body: ProviderApplicationDataBody
  ): BigInteger {
    val providerApplicationData = ProviderApplicationData(
      body.verifiedCredentialUrl,
      body.displayName,
      body.shortcode,
      body.whitelistedOrigins,
      body.assets,
    )
    return organizationService.saveProviderApplication(providerMsaId, providerApplicationData)
  }

  override suspend fun deleteProviderApplication(providerMsaId: BigInteger, providerApplicationId: BigInteger) {
    organizationService.deleteProviderApplication(providerMsaId, providerApplicationId)
  }
}
