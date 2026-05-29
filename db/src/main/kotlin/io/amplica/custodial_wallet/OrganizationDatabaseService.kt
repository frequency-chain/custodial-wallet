package io.amplica.custodial_wallet

import io.amplica.custodial_wallet.db.repository.organization.*
import io.amplica.custodial_wallet.db.repository.organization.application.ProviderApplication
import io.amplica.custodial_wallet.db.repository.organization.application.ProviderApplicationAsset
import io.amplica.custodial_wallet.db.repository.organization.application.ProviderApplicationWhitelistedOriginDescriptor
import java.math.BigInteger
import java.net.URI


interface OrganizationDatabaseService {

  suspend fun saveOrganization(organization: Organization): BigInteger
  suspend fun updateOrganization(organization: Organization)
  suspend fun findOneOrganizationById(id: BigInteger): Organization?
  suspend fun findOneOrganizationByProviderMsaId(providerMsaId: BigInteger): Organization?

  suspend fun deleteAllProviderFrequencyAccountsByOrganizationId(organizationId: BigInteger)
  suspend fun saveProviderFrequencyAccount(account: ProviderFrequencyAccount): BigInteger

  suspend fun deleteAllAssetsByOrganizationId(organizationId: BigInteger)
  suspend fun saveOrganizationAsset(asset: OrganizationAsset): BigInteger

  suspend fun deleteAllWhitelistedOriginDescriptorsByOrganizationId(organizationId: BigInteger)
  suspend fun saveWhitelistedOriginDescriptor(descriptor: WhitelistedOriginDescriptor): BigInteger

  suspend fun findOneProviderFrequencyAccountByMsaId(providerMsaId: BigInteger): ProviderFrequencyAccount?
  suspend fun updateProviderFrequencyAccount(providerFrequencyAccessAccount: ProviderFrequencyAccount)

  suspend fun findProviderApplicationByUrl(providerMsaId: BigInteger, verifiedCredentialUrl: URI): ProviderApplication?
  suspend fun findProviderApplicationById(providerApplicationId: BigInteger): ProviderApplication?
  suspend fun saveProviderApplication(providerApplication: ProviderApplication): BigInteger

  suspend fun deleteAllAssetsByProviderApplicationId(providerApplicationId: BigInteger)
  suspend fun saveProviderApplicationAsset(asset: ProviderApplicationAsset): BigInteger

  suspend fun deleteAllWhitelistedOriginDescriptorsByProviderApplicationId(providerApplicationId: BigInteger)
  suspend fun saveProviderApplicationWhitelistedOriginDescriptor(descriptor: ProviderApplicationWhitelistedOriginDescriptor): BigInteger

  suspend fun deleteProviderApplicationById(id: BigInteger)
  suspend fun deleteProviderFrequencyAccountByMsaId(msaId: BigInteger)
  suspend fun deleteOrganizationAssetById(id: BigInteger)
  suspend fun deleteWhitelistedOriginDescriptorById(id: BigInteger)
  suspend fun deleteProviderApplicationAssetById(id: BigInteger)
  suspend fun deleteProviderApplicationWhitelistedOriginDescriptorById(id: BigInteger)
  suspend fun deleteAllApplicationsByProviderFrequencyAccountId(id: BigInteger)
}
