package io.amplica.custodial_wallet.dto

import io.amplica.custodial_wallet.service.organization.Asset
import io.amplica.custodial_wallet.service.organization.AssetType
import io.amplica.custodial_wallet.service.organization.OriginDescriptor
import java.math.BigInteger
import java.net.URI


data class OrganizationDataBody(
  val msaIds: List<BigInteger>, // Must not be empty
  val displayName: String,
  val shortcode: String?,
  val whitelistedOrigins: List<OriginDescriptor>, // Must not be empty
  val assets: Map<AssetType, Asset>
)

data class ProviderFrequencyAccountDataBody(
  val organizationId: BigInteger,
  val providerApplications: List<ProviderApplicationDataBody>
)

data class ProviderApplicationDataBody(
  val verifiedCredentialUrl: URI,
  val displayName: String,
  val shortcode: String,
  val whitelistedOrigins: List<OriginDescriptor>,
  val assets: Map<AssetType, Asset>
)