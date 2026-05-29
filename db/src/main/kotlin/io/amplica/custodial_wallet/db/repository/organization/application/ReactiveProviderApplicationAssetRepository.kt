package io.amplica.custodial_wallet.db.repository.organization.application

import io.amplica.custodial_wallet.db.repository.organization.OrganizationAssetType
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigInteger
import java.time.Instant
@Table(ProviderApplicationAsset.TABLE_NAME)
data class ProviderApplicationAsset(
  @Id
  val id: BigInteger? = null,

  @Column(PROVIDER_APPLICATION_ID_COLUMN_NAME)
  val providerApplicationId: BigInteger,

  @Column(ASSET_TYPE_COLUMN_NAME)
  val assetType: OrganizationAssetType,

  @Column(URL_COLUMN_NAME)
  val url: String,

  @Column(CREATED_AT_COLUMN_NAME)
  val createdAt: Long,

  @Column(LAST_MODIFIED_COLUMN_NAME)
  val lastModified: Long,

  @Version
  var version: Long?,
) {
    companion object {
    const val TABLE_NAME = "provider_application_asset"

    const val PROVIDER_APPLICATION_ID_COLUMN_NAME = "provider_application_id"
    const val ASSET_TYPE_COLUMN_NAME = "asset_type"
    const val URL_COLUMN_NAME = "url"
    const val CREATED_AT_COLUMN_NAME = "created_at"
    const val LAST_MODIFIED_COLUMN_NAME = "last_modified"

    fun create(
      providerApplicationId: BigInteger,
      assetType: OrganizationAssetType,
      url: String,
    ): ProviderApplicationAsset {
      val now = Instant.now().toEpochMilli()
      return ProviderApplicationAsset(
        null,
        providerApplicationId,
        assetType,
        url,
        now,
        now,
        null
      )
    }
  }
}

interface ReactiveProviderApplicationAssetRepository : ReactiveCrudRepository<ProviderApplicationAsset, BigInteger> {
  fun findAllByProviderApplicationId(providerApplicationId: BigInteger): Flux<ProviderApplicationAsset>
  fun deleteAllByProviderApplicationId(providerApplicationId: BigInteger): Mono<Void>
}
