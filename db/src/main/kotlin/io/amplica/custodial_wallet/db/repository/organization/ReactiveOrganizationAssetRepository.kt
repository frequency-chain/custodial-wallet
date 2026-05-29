package io.amplica.custodial_wallet.db.repository.organization

import com.fasterxml.jackson.annotation.JsonValue
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigInteger
import java.time.Instant


enum class OrganizationAssetType(@JsonValue val value: String) {
  BRAND_LOGO("BRAND_LOGO");
}

@Table(OrganizationAsset.TABLE_NAME)
data class OrganizationAsset(
  @Id
  val id: BigInteger? = null,

  @Column(ORGANIZATION_ID_COLUMN_NAME)
  val organizationId: BigInteger,

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
    const val TABLE_NAME = "organization_asset"

    const val ORGANIZATION_ID_COLUMN_NAME = "organization_id"
    const val ASSET_TYPE_COLUMN_NAME = "asset_type"
    const val URL_COLUMN_NAME = "url"
    const val CREATED_AT_COLUMN_NAME = "created_at"
    const val LAST_MODIFIED_COLUMN_NAME = "last_modified"

    fun create(
      organizationId: BigInteger,
      assetType: OrganizationAssetType,
      url: String,
    ): OrganizationAsset {
      val now = Instant.now().toEpochMilli()
      return OrganizationAsset(
        null,
        organizationId,
        assetType,
        url,
        now,
        now,
        null
      )
    }
  }

}

interface ReactiveOrganizationAssetRepository : ReactiveCrudRepository<OrganizationAsset, BigInteger> {
  fun findAllByOrganizationId(organizationId: BigInteger): Flux<OrganizationAsset>
  fun deleteAllByOrganizationId(organizationId: BigInteger): Mono<Void>
}
