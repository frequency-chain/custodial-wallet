package io.amplica.custodial_wallet.db.repository.organization.application

import io.amplica.custodial_wallet.db.repository.organization.ProviderFrequencyAccount
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.annotation.Version
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigInteger
import java.time.Instant
@Table(ProviderApplication.TABLE_NAME)
data class ProviderApplication(
  @Id
  val id: BigInteger? = null,

  @Column(PROVIDER_FREQUENCY_ACCOUNT_ID_COLUMN_NAME)
  val providerFrequencyAccountId: BigInteger,

  @Column(VERIFIED_CREDENTIAL_URL_COLUMN_NAME)
  val verifiedCredentialUrl: String,

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
){
  @Transient
  var providerApplicationAssets: List<ProviderApplicationAsset> = emptyList()

  @Transient
  var providerApplicationWhitelistedOriginDescriptors: List<ProviderApplicationWhitelistedOriginDescriptor> = emptyList()

  companion object {
    const val TABLE_NAME = "provider_application"
    const val ALIAS_PREFIX = "app"

    const val VERIFIED_CREDENTIAL_URL_COLUMN_NAME = "verified_credential_url"
    const val PROVIDER_FREQUENCY_ACCOUNT_ID_COLUMN_NAME = "provider_frequency_account_id"
    const val DISPLAY_NAME_COLUMN_NAME = "display_name"
    const val SHORT_CODE_COLUMN_NAME = "short_code"
    const val CREATED_AT_COLUMN_NAME = "created_at"
    const val LAST_MODIFIED_COLUMN_NAME = "last_modified"

    fun create(
      providerFrequencyAccountId: BigInteger,
      verifiedCredentialUrl: String,
      displayName: String,
      shortCode: String,
    ): ProviderApplication {
      val now = Instant.now().toEpochMilli()
      return ProviderApplication(
        null,
        providerFrequencyAccountId,
        verifiedCredentialUrl,
        displayName,
        shortCode,
        now,
        now,
        null
      )
    }
  }
}

interface ReactiveProviderApplicationRepository : ReactiveCrudRepository<ProviderApplication, BigInteger> {
  @Query(
    value = """
      SELECT ${ProviderApplication.ALIAS_PREFIX}.*
      FROM ${ProviderFrequencyAccount.TABLE_NAME} ${ProviderFrequencyAccount.ALIAS_PREFIX}
      INNER JOIN ${ProviderApplication.TABLE_NAME} ${ProviderApplication.ALIAS_PREFIX} 
        ON ${ProviderFrequencyAccount.ALIAS_PREFIX}.id = ${ProviderApplication.ALIAS_PREFIX}.${ProviderApplication.PROVIDER_FREQUENCY_ACCOUNT_ID_COLUMN_NAME}
      WHERE ${ProviderFrequencyAccount.ALIAS_PREFIX}.${ProviderFrequencyAccount.MSA_ID_COLUMN_NAME} = :providerMsaId
      AND ${ProviderApplication.ALIAS_PREFIX}.${ProviderApplication.VERIFIED_CREDENTIAL_URL_COLUMN_NAME} = :verifiedCredentialUrl
    """
  )
  fun findOneOrganizationByMsaIdAndVerifiedCredentialUrl(providerMsaId: BigInteger, verifiedCredentialUrl: String): Mono<ProviderApplication?>
  fun findAllByProviderFrequencyAccountId(providerFrequencyAccountId: BigInteger): Flux<ProviderApplication>
  fun deleteAllByProviderFrequencyAccountId(id: BigInteger): Mono<Void>
}