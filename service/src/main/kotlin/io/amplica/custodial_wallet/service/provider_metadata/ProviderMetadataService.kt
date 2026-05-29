package io.amplica.custodial_wallet.service.provider_metadata

import io.amplica.custodial_wallet.service.organization.Asset
import io.amplica.custodial_wallet.service.organization.AssetType
import io.amplica.custodial_wallet.service.organization.OriginDescriptor
import java.math.BigInteger
import java.net.URI

interface ProviderMetadataService {
  /** If the result is `null` then the provider is unknown to us */
  @Deprecated("Use ", ReplaceWith("resolveProviderMetadataForApplication(msaId, verifiedCredentialUrl)"))
  suspend fun resolveProviderMetadata(msaId: BigInteger): ProviderMetadata?

  /** If the result is `null` then the provider and/or application are not registered in our database */
  suspend fun resolveProviderMetadataForApplication(msaId: BigInteger, verifiedCredentialUrl: URI): ProviderMetadata?
}

data class ProviderMetadata(
  val displayName: String,
  val shortcode: String?,
  val whitelistedOrigins: List<OriginDescriptor>,
  val assets: Map<AssetType, Asset>
)
