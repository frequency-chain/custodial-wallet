package io.amplica.custodial_wallet.service.provider_metadata

import io.amplica.custodial_wallet.OrganizationDatabaseService
import io.amplica.custodial_wallet.service.organization.Asset
import io.amplica.custodial_wallet.service.organization.toAssetType
import io.amplica.custodial_wallet.service.organization.toOriginDescriptor
import io.amplica.custodial_wallet.service.organization.toProviderApplicationOriginDescriptor
import java.math.BigInteger
import java.net.URI

class DatabaseSourcedProviderMetadataService(
  private val organizationDatabaseService: OrganizationDatabaseService
) : ProviderMetadataService {

  override suspend fun resolveProviderMetadata(msaId: BigInteger): ProviderMetadata? {
    return organizationDatabaseService.findOneOrganizationByProviderMsaId(msaId)?.let { organization ->
      ProviderMetadata(
        organization.displayName,
        organization.shortCode,
        organization.whitelistedOriginDescriptors.map { it.toOriginDescriptor() },
        organization.assets.associate { Pair(it.assetType.toAssetType(), Asset(it.url)) }
      )
    }
  }

  override suspend fun resolveProviderMetadataForApplication(
    msaId: BigInteger,
    verifiedCredentialUrl: URI
  ): ProviderMetadata? {
    return organizationDatabaseService.findProviderApplicationByUrl(msaId, verifiedCredentialUrl)?.let { application ->
      ProviderMetadata(
        application.displayName,
        application.shortCode,
        application.providerApplicationWhitelistedOriginDescriptors.map { it.toProviderApplicationOriginDescriptor() },
        application.providerApplicationAssets.associate { Pair(it.assetType.toAssetType(), Asset(it.url)) }
      )
    }
  }

}
