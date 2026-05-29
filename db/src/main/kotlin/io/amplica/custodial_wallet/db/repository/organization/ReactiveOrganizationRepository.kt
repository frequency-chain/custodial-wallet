package io.amplica.custodial_wallet.db.repository.organization

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.annotation.Version
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono
import java.math.BigInteger
import java.time.Instant

@Table(Organization.TABLE_NAME)
data class Organization(
  @Id
  val id: BigInteger? = null,

  @Column(DISPLAY_NAME_COLUMN_NAME)
  val displayName: String,

  @Column(SHORT_CODE_COLUMN_NAME)
  val shortCode: String,

  @Column(CREATED_AT_COLUMN_NAME)
  val createdAt: Long,

  @Column(LAST_MODIFIED_COLUMN_NAME)
  val lastModified: Long,

  @Version
  var version: Long?,
) {

  @Transient
  var providerFrequencyAccounts: List<ProviderFrequencyAccount> = emptyList()

  @Transient
  var assets: List<OrganizationAsset> = emptyList()

  @Transient
  var whitelistedOriginDescriptors: List<WhitelistedOriginDescriptor> = emptyList()

  companion object {
    const val TABLE_NAME = "organization"
    const val ALIAS_PREFIX = "org"

    const val DISPLAY_NAME_COLUMN_NAME = "display_name"
    const val SHORT_CODE_COLUMN_NAME = "short_code"
    const val CREATED_AT_COLUMN_NAME = "created_at"
    const val LAST_MODIFIED_COLUMN_NAME = "last_modified"

    fun create(
      displayName: String,
      shortCode: String,
    ): Organization {
      val now = Instant.now().toEpochMilli()
      return Organization(
        null,
        displayName,
        shortCode,
        now,
        now,
        null
      )
    }
  }

}

interface ReactiveOrganizationRepository : ReactiveCrudRepository<Organization, BigInteger> {

  @Query(
    value = """
      SELECT ${Organization.ALIAS_PREFIX}.*
      FROM ${ProviderFrequencyAccount.TABLE_NAME} ${ProviderFrequencyAccount.ALIAS_PREFIX}
      INNER JOIN ${Organization.TABLE_NAME} ${Organization.ALIAS_PREFIX} 
        ON ${Organization.ALIAS_PREFIX}.id = ${ProviderFrequencyAccount.ALIAS_PREFIX}.${ProviderFrequencyAccount.ORGANIZATION_ID_COLUMN_NAME}
      WHERE ${ProviderFrequencyAccount.ALIAS_PREFIX}.${ProviderFrequencyAccount.MSA_ID_COLUMN_NAME} = :providerMsaId
    """
  )
  fun findOneOrganizationByMsaId(providerMsaId: BigInteger): Mono<Organization?>

}
