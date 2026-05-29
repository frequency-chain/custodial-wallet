package io.amplica.custodial_wallet.service.organization

import java.math.BigInteger
import java.net.URI

interface OrganizationService {
  suspend fun getOrganization(id: BigInteger): OrganizationData
  suspend fun saveOrganization(data: OrganizationData): BigInteger
  suspend fun updateOrganization(organizationId: BigInteger, data: OrganizationData)
  suspend fun deleteOrganizationProviderFrequencyAccounts(organizationId: BigInteger)
  suspend fun getOrganizationByMsaId(msaId: BigInteger): Pair<BigInteger, OrganizationData>?

  suspend fun getProviderApplicationByUrl(providerMsaId: BigInteger, verifiedCredentialUrl: URI): ProviderApplicationData?
  suspend fun getProviderFrequencyAccountByProviderMsaId(providerMsaId: BigInteger): ProviderFrequencyAccountData?
  suspend fun updateProviderFrequencyAccountByProviderMsaId(providerMsaId: BigInteger, data: ProviderFrequencyAccountData)
  suspend fun deleteProviderApplicationsByProviderMsaId(providerMsaId: BigInteger)

  suspend fun getProviderApplication(providerMsaId: BigInteger, providerApplicationId: BigInteger): ProviderApplicationData
  suspend fun saveProviderApplication(providerMsaId: BigInteger, data: ProviderApplicationData): BigInteger
  suspend fun deleteProviderApplication(providerMsaId: BigInteger, providerApplicationId: BigInteger)
}
