package io.amplica.custodial_wallet.db.repository.organization.application

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigInteger
import java.time.Instant


@Table(ProviderApplicationWhitelistedOriginDescriptor.TABLE_NAME)
data class ProviderApplicationWhitelistedOriginDescriptor(
  @Id
  val id: BigInteger? = null,

  @Column(PROVIDER_APPLICATION_ID_COLUMN_NAME)
  val providerApplicationId: BigInteger,

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
    const val TABLE_NAME = "provider_application_whitelisted_origin_descriptor"

    const val PROVIDER_APPLICATION_ID_COLUMN_NAME = "provider_application_id"
    const val SCHEME_COLUMN_NAME = "scheme"
    const val DOMAIN_COLUMN_NAME = "domain"
    const val CREATED_AT_COLUMN_NAME = "created_at"
    const val LAST_MODIFIED_COLUMN_NAME = "last_modified"

    fun create(
      providerApplicationId: BigInteger,
      scheme: String,
      domain: String,
    ): ProviderApplicationWhitelistedOriginDescriptor {
      val now = Instant.now().toEpochMilli()
      return ProviderApplicationWhitelistedOriginDescriptor(
        null,
        providerApplicationId,
        scheme,
        domain,
        now,
        now,
        null
      )
    }
  }
}

interface ReactiveProviderApplicationWhitelistedOriginDescriptorRepository : ReactiveCrudRepository<ProviderApplicationWhitelistedOriginDescriptor, BigInteger> {
  fun findAllByProviderApplicationId(providerApplicationId: BigInteger): Flux<ProviderApplicationWhitelistedOriginDescriptor>
  fun deleteAllByProviderApplicationId(providerApplicationId: BigInteger): Mono<Void>
}
