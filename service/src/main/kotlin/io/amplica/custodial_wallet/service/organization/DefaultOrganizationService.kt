package io.amplica.custodial_wallet.service.organization

import io.amplica.custodial_wallet.OrganizationDatabaseService
import io.amplica.custodial_wallet.db.repository.organization.Organization
import io.amplica.custodial_wallet.db.repository.organization.OrganizationAsset
import io.amplica.custodial_wallet.db.repository.organization.ProviderFrequencyAccount
import io.amplica.custodial_wallet.db.repository.organization.WhitelistedOriginDescriptor
import io.amplica.custodial_wallet.db.repository.organization.application.ProviderApplication
import io.amplica.custodial_wallet.db.repository.organization.application.ProviderApplicationAsset
import io.amplica.custodial_wallet.db.repository.organization.application.ProviderApplicationWhitelistedOriginDescriptor
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import java.math.BigInteger
import java.net.URI
import java.time.Instant

/**
 * Default organization service
 * TODO: This call could be tightened up by teasing out reads and writes but it's got a cache over it so not super high
 * priority
 *
 * @property organizationDatabaseService
 * @property transactionalOperator
 * @constructor Create empty Default organization service
 */
class DefaultOrganizationService(
  private val organizationDatabaseService: OrganizationDatabaseService,
  private val transactionalOperator: TransactionalOperator
) : OrganizationService {

  private suspend fun saveAncillaryOrganizationData(organizationId: BigInteger, data: OrganizationData) {
    data.msaIds.map { msaId ->
      val accountEntity = ProviderFrequencyAccount.create(msaId, organizationId)
      organizationDatabaseService.saveProviderFrequencyAccount(accountEntity)
    }

    data.assets.forEach { (assetType, asset) ->
      val assetEntity = OrganizationAsset.create(organizationId, assetType.toOrganizationAssetType(), asset.url)
      organizationDatabaseService.saveOrganizationAsset(assetEntity)
    }

    data.whitelistedOrigins.forEach { origin ->
      val originEntity = WhitelistedOriginDescriptor.create(organizationId, origin.scheme, origin.domain)
      organizationDatabaseService.saveWhitelistedOriginDescriptor(originEntity)
    }
  }

  private suspend fun updateAncillaryOrganizationData(existingOrganization: Organization, data: OrganizationData) {
    val organizationId = existingOrganization.id!!
    val providerFrequencyAccountsToSave = mutableListOf<ProviderFrequencyAccount>()
    val organizationAssetsToSave = mutableListOf<OrganizationAsset>()
    val whitelistedOriginDescriptorsToSave = mutableListOf<WhitelistedOriginDescriptor>()

    data.msaIds.map { msaId ->
      val existingProviderFrequencyAccount = organizationDatabaseService.findOneProviderFrequencyAccountByMsaId(msaId)
      if(existingProviderFrequencyAccount == null){
        val accountEntity = ProviderFrequencyAccount.create(msaId, organizationId)
        providerFrequencyAccountsToSave.add(accountEntity)
      } else {
        providerFrequencyAccountsToSave.add(existingProviderFrequencyAccount)
      }
    }

    data.assets.forEach { (assetType, asset) ->
      val existingOrganizationAsset = existingOrganization.assets.find { it.url == asset.url }
      val assetEntity = existingOrganizationAsset ?: OrganizationAsset.create(organizationId, assetType.toOrganizationAssetType(), asset.url)
      organizationAssetsToSave.add(assetEntity)
    }

    data.whitelistedOrigins.forEach { origin ->
      val existingWhitelistedOriginDescriptor = existingOrganization.whitelistedOriginDescriptors.find { it.scheme == origin.scheme && it.domain == origin.domain }
      if(existingWhitelistedOriginDescriptor == null){
        val originEntity = WhitelistedOriginDescriptor.create(organizationId, origin.scheme, origin.domain)
        whitelistedOriginDescriptorsToSave.add(originEntity)
      } else {
        whitelistedOriginDescriptorsToSave.add(existingWhitelistedOriginDescriptor)
      }
    }

    val providerFrequencyAccountsToDelete = existingOrganization.providerFrequencyAccounts.filter { !data.msaIds.contains(it.msaId) }
    providerFrequencyAccountsToDelete.forEach {
      organizationDatabaseService.deleteAllApplicationsByProviderFrequencyAccountId(it.id!!)
      organizationDatabaseService.deleteProviderFrequencyAccountByMsaId(it.msaId)
    }
    val dataAssetUrls = data.assets.values.map { asset -> asset.url }
    existingOrganization.assets.filter { !dataAssetUrls.contains(it.url) }
      .forEach { organizationDatabaseService.deleteOrganizationAssetById(it.id!!) }
    existingOrganization.whitelistedOriginDescriptors.filter {
      !data.whitelistedOrigins.contains(
        OriginDescriptor(
          it.scheme,
          it.domain
        )
      )
    }.forEach { organizationDatabaseService.deleteWhitelistedOriginDescriptorById(it.id!!) }

    providerFrequencyAccountsToSave.forEach {
      organizationDatabaseService.saveProviderFrequencyAccount(
        it.copy(
          lastModified = Instant.now().toEpochMilli()
        )
      )
    }
    organizationAssetsToSave.forEach {
      organizationDatabaseService.saveOrganizationAsset(
        it.copy(
          lastModified = Instant.now().toEpochMilli()
        )
      )
    }
    whitelistedOriginDescriptorsToSave.forEach {
      organizationDatabaseService.saveWhitelistedOriginDescriptor(
        it.copy(
          lastModified = Instant.now().toEpochMilli()
        )
      )
    }
  }

  private suspend fun saveAncillaryProviderApplicationData(providerApplicationId: BigInteger, data: ProviderApplicationData) {

    data.assets.forEach { (assetType, asset) ->
      val assetEntity = ProviderApplicationAsset.create(providerApplicationId, assetType.toOrganizationAssetType(), asset.url)
      organizationDatabaseService.saveProviderApplicationAsset(assetEntity)
    }

    data.whitelistedOrigins.forEach { origin ->
      val originEntity = ProviderApplicationWhitelistedOriginDescriptor.create(providerApplicationId, origin.scheme, origin.domain)
      organizationDatabaseService.saveProviderApplicationWhitelistedOriginDescriptor(originEntity)
    }
  }

  private suspend fun updateAncillaryProviderApplicationData(existingProviderFrequencyAccount: ProviderFrequencyAccount, data: ProviderFrequencyAccountData) {
    data.providerApplications.forEach { application ->
      var providerApplicationToSave: ProviderApplication? = null
      val applicationId: BigInteger
      val existingApplication = existingProviderFrequencyAccount.providerApplications.find {
        it.verifiedCredentialUrl == application.verifiedCredentialUrl.toASCIIString()
      }
      if(existingApplication == null){
        val applicationEntity = ProviderApplication.create(
          existingProviderFrequencyAccount.id!!,
          application.verifiedCredentialUrl.toASCIIString(),
          application.displayName,
          application.shortcode
        )
        applicationId = organizationDatabaseService.saveProviderApplication(applicationEntity)
      } else {
        providerApplicationToSave = existingApplication.copy(displayName = application.displayName, shortCode = application.shortcode)
        applicationId = existingApplication.id!!
      }

      val providerApplicationAssetsToSave = mutableListOf<ProviderApplicationAsset>()
      val providerApplicationWhitelistedOriginDescriptorsToSave = mutableListOf<ProviderApplicationWhitelistedOriginDescriptor>()
      application.assets.forEach { (assetType, asset) ->
        val existingProviderApplicationAsset = existingApplication?.providerApplicationAssets?.find { it.url == asset.url }
        if(existingProviderApplicationAsset == null){
          val assetEntity = ProviderApplicationAsset.create(applicationId, assetType.toOrganizationAssetType(), asset.url)
          providerApplicationAssetsToSave.add(assetEntity)
        } else {
          providerApplicationAssetsToSave.add(existingProviderApplicationAsset)
        }
      }

      application.whitelistedOrigins.forEach { origin ->
        val existingWhitelistedOriginDescriptor = existingApplication?.providerApplicationWhitelistedOriginDescriptors?.find { it.scheme == origin.scheme && it.domain == origin.domain }
        if(existingWhitelistedOriginDescriptor == null){
          val originEntity = ProviderApplicationWhitelistedOriginDescriptor.create(applicationId, origin.scheme, origin.domain)
          providerApplicationWhitelistedOriginDescriptorsToSave.add(originEntity)
        } else {
          providerApplicationWhitelistedOriginDescriptorsToSave.add(existingWhitelistedOriginDescriptor)
        }
      }
      val applicationAssetUrls = application.assets.values.map { asset -> asset.url }
      existingApplication?.providerApplicationAssets?.filter { !applicationAssetUrls.contains(it.url) }
        ?.forEach { organizationDatabaseService.deleteProviderApplicationAssetById(it.id!!) }
      existingApplication?.providerApplicationWhitelistedOriginDescriptors?.filter {
        !application.whitelistedOrigins.contains(
          OriginDescriptor(it.scheme, it.domain)
        )
      }?.forEach { organizationDatabaseService.deleteProviderApplicationWhitelistedOriginDescriptorById(it.id!!) }

      if (providerApplicationToSave != null) {
        organizationDatabaseService.saveProviderApplication(providerApplicationToSave)
      }

      providerApplicationAssetsToSave.forEach {
        organizationDatabaseService.saveProviderApplicationAsset(
          it.copy(
            lastModified = Instant.now().toEpochMilli()
          )
        )
      }
      providerApplicationWhitelistedOriginDescriptorsToSave.forEach {
        organizationDatabaseService.saveProviderApplicationWhitelistedOriginDescriptor(
          it.copy(lastModified = Instant.now().toEpochMilli())
        )
      }
    }

    val dataProviderApplicationsUrls = data.providerApplications.map { application -> application.verifiedCredentialUrl.toASCIIString() }
    existingProviderFrequencyAccount.providerApplications.filter { !dataProviderApplicationsUrls.contains(it.verifiedCredentialUrl) }
      .forEach { organizationDatabaseService.deleteProviderApplicationById(it.id!!) }
  }

  private suspend fun getOrganizationById(id: BigInteger): Organization {
    return organizationDatabaseService.findOneOrganizationById(id)
      ?: throw ApiException(ApiError.ORGANIZATION_NOT_FOUND, "No organization found for id=$id")
  }

  private fun organizationToOrganizationData(organization: Organization): OrganizationData {
    return OrganizationData(
      organization.providerFrequencyAccounts.map { it.msaId }.toSet(),
      organization.displayName,
      organization.shortCode,
      organization.whitelistedOriginDescriptors.map { it.toOriginDescriptor() },
      organization.assets.associate { Pair(it.assetType.toAssetType(), Asset(it.url)) }
    )
  }

  override suspend fun getOrganization(id: BigInteger): OrganizationData {
    return organizationToOrganizationData(getOrganizationById(id))
  }

  override suspend fun saveOrganization(data: OrganizationData): BigInteger {
    return transactionalOperator.executeAndAwait {
      val organizationEntity = Organization.create(data.displayName, data.shortcode)
      val organizationId = organizationDatabaseService.saveOrganization(organizationEntity)

      saveAncillaryOrganizationData(organizationId, data)

      organizationId
    }
  }

  override suspend fun updateOrganization(organizationId: BigInteger, data: OrganizationData) {
    transactionalOperator.executeAndAwait {
      val existingOrganization = getOrganizationById(organizationId)

      val updatedOrganization = existingOrganization.copy(displayName = data.displayName, shortCode = data.shortcode)
      organizationDatabaseService.updateOrganization(updatedOrganization)

      // Write new rows for related data
      updateAncillaryOrganizationData(existingOrganization, data)
    }
  }

  override suspend fun deleteOrganizationProviderFrequencyAccounts(organizationId: BigInteger) {
    organizationDatabaseService.deleteAllProviderFrequencyAccountsByOrganizationId(organizationId)
  }

  override suspend fun getOrganizationByMsaId(msaId: BigInteger): Pair<BigInteger, OrganizationData>? {
    return organizationDatabaseService.findOneOrganizationByProviderMsaId(msaId)?.let { organization ->
      val data = organizationToOrganizationData(organization)

      Pair(organization.id!!, data)
    }
  }

  override suspend fun getProviderApplicationByUrl(providerMsaId: BigInteger, verifiedCredentialUrl: URI): ProviderApplicationData? {
    val providerApplication = organizationDatabaseService.findProviderApplicationByUrl(
      providerMsaId,
      verifiedCredentialUrl
    )

    return providerApplication?.let {
      providerApplicationToProviderApplicationData(it)
    }
  }

  override suspend fun getProviderFrequencyAccountByProviderMsaId(providerMsaId: BigInteger): ProviderFrequencyAccountData? {
    val providerFrequencyAccount = organizationDatabaseService.findOneProviderFrequencyAccountByMsaId(providerMsaId)
    return if (providerFrequencyAccount == null) {
      null
    } else {
      val providerApplications = providerFrequencyAccount.providerApplications
      val providerApplicationData = providerApplications.map { providerApplication ->
        providerApplicationToProviderApplicationData(providerApplication)
      }
      ProviderFrequencyAccountData(
        providerMsaId,
        providerFrequencyAccount.organizationId,
        providerApplicationData
      )
    }
  }

  override suspend fun updateProviderFrequencyAccountByProviderMsaId(providerMsaId: BigInteger, data: ProviderFrequencyAccountData) {
    transactionalOperator.executeAndAwait {
      val existingProviderFrequencyAccount = organizationDatabaseService.findOneProviderFrequencyAccountByMsaId(providerMsaId)
        ?: throw ApiException(
          ApiError.NO_PROVIDER_FREQUENCY_ACCOUNT_FOUND,
          "No provider frequency account found for providerMsaId=$providerMsaId"
        )

      organizationDatabaseService.updateProviderFrequencyAccount(
        ProviderFrequencyAccount(
          existingProviderFrequencyAccount.id,
          providerMsaId,
          data.organizationId,
          existingProviderFrequencyAccount.createdAt,
          Instant.now().toEpochMilli(),
          existingProviderFrequencyAccount.version
        )
      )
      updateAncillaryProviderApplicationData(existingProviderFrequencyAccount, data)
    }
  }

  override suspend fun deleteProviderApplicationsByProviderMsaId(providerMsaId: BigInteger) {
    val providerFrequencyAccount = organizationDatabaseService.findOneProviderFrequencyAccountByMsaId(providerMsaId)
      ?: throw ApiException(
        ApiError.NO_PROVIDER_FREQUENCY_ACCOUNT_FOUND,
        "No provider frequency account found for providerMsaId=$providerMsaId"
      )
    for (application in providerFrequencyAccount.providerApplications){
      // Wipe out all existing related rows
      application.id?.let { organizationDatabaseService.deleteProviderApplicationById(it) }
    }
  }

  private fun providerApplicationToProviderApplicationData(providerApplication: ProviderApplication): ProviderApplicationData{
    val assets = mutableMapOf<AssetType,Asset>()
    for (asset in providerApplication.providerApplicationAssets){
      assets[asset.assetType.toAssetType()] = Asset(asset.url)
    }
    return ProviderApplicationData(
      URI(providerApplication.verifiedCredentialUrl),
      providerApplication.displayName,
      providerApplication.shortCode,
      providerApplication.providerApplicationWhitelistedOriginDescriptors.map { d ->
        OriginDescriptor(
          d.scheme,
          d.domain
        )
      },
      assets
    )
  }

  override suspend fun getProviderApplication(providerMsaId: BigInteger, providerApplicationId: BigInteger): ProviderApplicationData {
    val providerApplication = checkMsaMatchesProviderApplication(providerMsaId, providerApplicationId)
    return ProviderApplicationData(
        URI(providerApplication.verifiedCredentialUrl),
        providerApplication.displayName,
        providerApplication.shortCode,
        providerApplication.providerApplicationWhitelistedOriginDescriptors.map { it.toProviderApplicationOriginDescriptor() },
        providerApplication.providerApplicationAssets.associate { Pair(it.assetType.toAssetType(), Asset(it.url)) }
      )

  }
  override suspend fun saveProviderApplication(providerMsaId: BigInteger, data: ProviderApplicationData): BigInteger {
    val providerFrequencyAccountId = organizationDatabaseService.findOneProviderFrequencyAccountByMsaId(providerMsaId)?.id
      ?: throw ApiException(
        ApiError.NO_PROVIDER_FREQUENCY_ACCOUNT_FOUND,
        "No provider frequency account found for providerMsaId=$providerMsaId"
      )
    val providerApplication = ProviderApplication.create(providerFrequencyAccountId, data.verifiedCredentialUrl.toASCIIString(), data.displayName, data.shortcode)
    val providerApplicationId = organizationDatabaseService.saveProviderApplication(providerApplication)
    saveAncillaryProviderApplicationData(providerApplicationId, data)
    return providerApplicationId
  }

  override suspend fun deleteProviderApplication(providerMsaId: BigInteger, providerApplicationId: BigInteger) {
    checkMsaMatchesProviderApplication(providerMsaId, providerApplicationId)
    organizationDatabaseService.deleteProviderApplicationById(providerApplicationId)
  }

  private suspend fun checkMsaMatchesProviderApplication(providerMsaId: BigInteger, providerApplicationId: BigInteger): ProviderApplication {
    val providerFrequencyAccount = organizationDatabaseService.findOneProviderFrequencyAccountByMsaId(providerMsaId)
      ?: throw ApiException(
        ApiError.NO_PROVIDER_FREQUENCY_ACCOUNT_FOUND,
        "No provider frequency account found for providerMsaId=$providerMsaId"
      )
    val providerApplication = organizationDatabaseService.findProviderApplicationById(providerApplicationId)
      ?: throw ApiException(
        ApiError.NO_PROVIDER_APPLICATION_FOUND,
        "No provider application found for providerApplicationId=$providerApplicationId"
      )
    if(providerFrequencyAccount.id != providerApplication.providerFrequencyAccountId) {
      throw ApiException(
        ApiError.MSA_DOES_NOT_MATCH_PROVIDER_APPLICATION,
        "ProviderMsaId=$providerMsaId does not match providerApplication=$providerApplicationId"
      )
    }
    return providerApplication
  }
}
