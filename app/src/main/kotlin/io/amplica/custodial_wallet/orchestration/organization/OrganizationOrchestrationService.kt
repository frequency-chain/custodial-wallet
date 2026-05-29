package io.amplica.custodial_wallet.orchestration.organization

import io.amplica.custodial_wallet.dto.OrganizationDataBody
import io.amplica.custodial_wallet.dto.ProviderApplicationDataBody
import io.amplica.custodial_wallet.dto.ProviderFrequencyAccountDataBody
import io.amplica.custodial_wallet.service.organization.OrganizationData
import io.amplica.custodial_wallet.service.organization.ProviderFrequencyAccountData
import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto
import java.math.BigInteger

interface OrganizationOrchestrationService {

  suspend fun getOrganization(organizationId: BigInteger): OrganizationData
  suspend fun saveOrganization(body: OrganizationDataBody): BigInteger
  suspend fun updateOrganization(organizationId: BigInteger, body: OrganizationDataBody)
  suspend fun deleteOrganizationProviderFrequencyAccounts(organizationId: BigInteger)
  suspend fun saveOrUpdateOrganizationFromPublicKey(publicKey: PublicKeyDto): OrganizationData
  suspend fun getProviderFrequencyAccountByProviderMsaId(providerMsaId: BigInteger): ProviderFrequencyAccountData
  suspend fun updateProviderFrequencyAccountByProviderMsaId(providerMsaId: BigInteger, body: ProviderFrequencyAccountDataBody)
  suspend fun deleteProviderFrequencyAccountApplicationsByProviderMsaId(providerMsaId: BigInteger)
  suspend fun getProviderApplication(providerMsaId: BigInteger, providerApplicationId: BigInteger): ProviderApplicationDataBody?
  suspend fun saveProviderApplication(providerMsaId: BigInteger, body: ProviderApplicationDataBody): BigInteger
  suspend fun deleteProviderApplication(providerMsaId: BigInteger, providerApplicationId: BigInteger)

}

