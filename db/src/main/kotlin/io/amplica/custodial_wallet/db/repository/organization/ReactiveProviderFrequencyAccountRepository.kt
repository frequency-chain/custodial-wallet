package io.amplica.custodial_wallet.db.repository.organization

import io.amplica.custodial_wallet.db.repository.organization.application.ProviderApplication
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigInteger
import java.time.Instant

@Table(ProviderFrequencyAccount.TABLE_NAME)
data class ProviderFrequencyAccount(
  @Id
  val id: BigInteger? = null,

  @Column(MSA_ID_COLUMN_NAME)
  val msaId: BigInteger,

  @Column(ORGANIZATION_ID_COLUMN_NAME)
  val organizationId: BigInteger,

  @Column(CREATED_AT_COLUMN_NAME)
  val createdAt: Long,

  @Column(LAST_MODIFIED_COLUMN_NAME)
  val lastModified: Long,

  @Version
  var version: Long?,
) {
  @Transient
  var providerApplications: List<ProviderApplication> = emptyList()

  companion object {
    const val TABLE_NAME = "provider_frequency_account"
    const val ALIAS_PREFIX = "pfa"

    const val MSA_ID_COLUMN_NAME = "msa_id"
    const val ORGANIZATION_ID_COLUMN_NAME = "organization_id"
    const val CREATED_AT_COLUMN_NAME = "created_at"
    const val LAST_MODIFIED_COLUMN_NAME = "last_modified"

    fun create(
      msaId: BigInteger,
      organizationId: BigInteger,
    ): ProviderFrequencyAccount {
      val now = Instant.now().toEpochMilli()
      return ProviderFrequencyAccount(
        null,
        msaId,
        organizationId,
        now,
        now,
        null
      )
    }
  }

}

interface ReactiveProviderFrequencyAccountRepository : ReactiveCrudRepository<ProviderFrequencyAccount, BigInteger> {
  fun findAllByOrganizationId(organizationId: BigInteger): Flux<ProviderFrequencyAccount>
  fun deleteAllByOrganizationId(organizationId: BigInteger): Mono<Void>

  // Guaranteed by UNIQUE constraint
  fun findByMsaId(msaId: BigInteger): Mono<ProviderFrequencyAccount>

  // Guaranteed by UNIQUE constraint
  fun deleteByMsaId(msaId: BigInteger): Mono<Void>
}
