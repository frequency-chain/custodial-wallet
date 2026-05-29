package io.amplica.custodial_wallet.service.organization

import java.math.BigInteger
import java.net.URI


data class OrganizationData(
  val msaIds: Set<BigInteger>,
  val displayName: String,
  val shortcode: String,
  val whitelistedOrigins: List<OriginDescriptor>,
  val assets: Map<AssetType, Asset>
)

data class ProviderFrequencyAccountData(
  val msaId: BigInteger,
  val organizationId: BigInteger,
  val providerApplications: List<ProviderApplicationData>
)

data class ProviderApplicationData(
  val verifiedCredentialUrl: URI,
  val displayName: String,
  val shortcode: String,
  val whitelistedOrigins: List<OriginDescriptor>,
  val assets: Map<AssetType, Asset>
)

data class Asset(
  val url: String,
)

// NOTE: Assets are not fully defined yet...
enum class AssetType {
  BRAND_LOGO
}
