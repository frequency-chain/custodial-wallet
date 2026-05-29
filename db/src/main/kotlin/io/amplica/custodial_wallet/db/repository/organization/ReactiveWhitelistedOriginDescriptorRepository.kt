package io.amplica.custodial_wallet.db.repository.organization

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigInteger
import java.time.Instant

@Table(WhitelistedOriginDescriptor.TABLE_NAME)
data class WhitelistedOriginDescriptor(
  @Id
  val id: BigInteger? = null,

  @Column(ORGANIZATION_ID_COLUMN_NAME)
  val organizationId: BigInteger,

  @Column(SCHEME_COLUMN_NAME)
  val scheme: String,

  @Column(DOMAIN_COLUMN_NAME)
  val domain: String,

  @Column(CREATED_AT_COLUMN_NAME)
  val createdAt: Long,

  @Column(LAST_MODIFIED_COLUMN_NAME)
  val lastModified: Long,

  @Version
  var version: Long?,
) {

  companion object {
    const val TABLE_NAME = "organization_whitelisted_origin_descriptor"

    const val ORGANIZATION_ID_COLUMN_NAME = "organization_id"
    const val SCHEME_COLUMN_NAME = "scheme"
    const val DOMAIN_COLUMN_NAME = "domain"
    const val CREATED_AT_COLUMN_NAME = "created_at"
    const val LAST_MODIFIED_COLUMN_NAME = "last_modified"

    fun create(
      organizationId: BigInteger,
      scheme: String,
      domain: String,
    ): WhitelistedOriginDescriptor {
      val now = Instant.now().toEpochMilli()
      return WhitelistedOriginDescriptor(
        null,
        organizationId,
        scheme,
        domain,
        now,
        now,
        null
      )
    }
  }

}

interface ReactiveWhitelistedOriginDescriptorRepository : ReactiveCrudRepository<WhitelistedOriginDescriptor, BigInteger> {
  fun findAllByOrganizationId(organizationId: BigInteger): Flux<WhitelistedOriginDescriptor>
  fun deleteAllByOrganizationId(organizationId: BigInteger): Mono<Void>
}
