package io.amplica.custodial_wallet.service.organization

import io.amplica.custodial_wallet.db.repository.organization.OrganizationAssetType
import io.amplica.custodial_wallet.db.repository.organization.WhitelistedOriginDescriptor
import io.amplica.custodial_wallet.db.repository.organization.application.ProviderApplicationWhitelistedOriginDescriptor

fun AssetType.toOrganizationAssetType(): OrganizationAssetType {
  return when (this) {
    AssetType.BRAND_LOGO -> OrganizationAssetType.BRAND_LOGO
  }
}

fun OrganizationAssetType.toAssetType(): AssetType {
  return when (this) {
    OrganizationAssetType.BRAND_LOGO -> AssetType.BRAND_LOGO
  }
}

fun WhitelistedOriginDescriptor.toOriginDescriptor(): OriginDescriptor {
  return OriginDescriptor(this.scheme, this.domain)
}

fun ProviderApplicationWhitelistedOriginDescriptor.toProviderApplicationOriginDescriptor(): OriginDescriptor {
  return OriginDescriptor(this.scheme, this.domain)
}
