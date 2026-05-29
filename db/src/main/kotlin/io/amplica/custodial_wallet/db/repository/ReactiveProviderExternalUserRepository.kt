package io.amplica.custodial_wallet.db.repository

import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.annotation.Version
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.r2dbc.core.DatabaseClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigInteger
import java.time.Instant


@Table(ProviderExternalUser.TABLE_NAME)
class ProviderExternalUser(
  @Id
  var id: BigInteger? = null,
  @Column(PROVIDER_MSA_ID_COLUMN_NAME)
  val providerMsaId: BigInteger,
  @Column(PROVIDER_EXTERNAL_ID_COLUMN_NAME)
  val providerExternalId: String,
  @Column(USER_KEY_DATA_ID_COLUMN_NAME)
  val userKeyDataId: BigInteger,
  @Column(CREATED_AT_COLUMN_NAME)
  val createdAt: BigInteger,
  @Column(LAST_MODIFIED_COLUMN_NAME)
  var lastModified: BigInteger,
  @Version
  var version: BigInteger?
) {
  @Transient
  var userKeyData: UserKeyData? = null

  constructor(
    providerMsaId: BigInteger,
    providerExternalId: String,
    userKeyDataId: BigInteger,
    createdAt: BigInteger,
    lastModified: BigInteger,
  ) :
      this(
        null,
        providerMsaId,
        providerExternalId,
        userKeyDataId,
        createdAt,
        lastModified,
        null
      )

  companion object {
    const val TABLE_NAME = "provider_external_user"
    const val ALIAS_PREFIX = "peu"
    const val ID_COLUMN_NAME = "id"
    const val PROVIDER_MSA_ID_COLUMN_NAME = "provider_msa_id"
    const val PROVIDER_EXTERNAL_ID_COLUMN_NAME = "provider_external_id"
    const val USER_KEY_DATA_ID_COLUMN_NAME = "user_key_data_id"
    const val CREATED_AT_COLUMN_NAME = "created_at"
    const val LAST_MODIFIED_COLUMN_NAME = "last_modified"
    const val VERSION_COLUMN_NAME = "version"

    const val ID_ALIAS = "$ALIAS_PREFIX.$ID_COLUMN_NAME"
    const val PROVIDER_MSA_ID_ALIAS = "$ALIAS_PREFIX.$PROVIDER_MSA_ID_COLUMN_NAME"
    const val PROVIDER_EXTERNAL_ID_ALIAS = "$ALIAS_PREFIX.$PROVIDER_EXTERNAL_ID_COLUMN_NAME"
    const val USER_KEY_DATA_ID_ALIAS = "$ALIAS_PREFIX.$USER_KEY_DATA_ID_COLUMN_NAME"
    const val CREATED_AT_ALIAS = "$ALIAS_PREFIX.$CREATED_AT_COLUMN_NAME"
    const val LAST_MODIFIED_ALIAS = "$ALIAS_PREFIX.$LAST_MODIFIED_COLUMN_NAME"
    const val VERSION_ALIAS = "$ALIAS_PREFIX.$VERSION_COLUMN_NAME"

    fun create(providerMsaId: BigInteger, providerExternalId: String, userKeyDataId: BigInteger,): ProviderExternalUser {
      val createdAt = Instant.now().toEpochMilli().toBigInteger()
      return ProviderExternalUser(
        providerMsaId,
        providerExternalId,
        userKeyDataId,
        createdAt,
        createdAt
      )
    }

    fun fromRow(row: Map<String, Any>): ProviderExternalUser {
      val userAccount = UserAccount.fromRow(row)
      val userKeyData = UserKeyData.fromRow(row)
      userKeyData.userAccount = userAccount
      val providerExternalUser = ProviderExternalUser(
        (row[ID_ALIAS] as Long).toBigInteger(),
        (row[PROVIDER_MSA_ID_ALIAS] as Long).toBigInteger(),
        row[PROVIDER_EXTERNAL_ID_ALIAS] as String,
        (row[USER_KEY_DATA_ID_ALIAS] as Long).toBigInteger(),
        (row[CREATED_AT_ALIAS] as Long).toBigInteger(),
        (row[LAST_MODIFIED_ALIAS] as Long).toBigInteger(),
        (row[VERSION_ALIAS] as Long).toBigInteger()
      )
      providerExternalUser.userKeyData = userKeyData

      return providerExternalUser
    }
  }
}


interface ReactiveProviderExternalUserRepository : ReactiveCrudRepository<ProviderExternalUser, BigInteger>, ReactiveProviderExternalUserDao {
  fun findOneByProviderMsaIdAndProviderExternalId(providerMsaId: BigInteger, providerExternalId: String): Mono<ProviderExternalUser>

  @Query(value = "SELECT provider_external_user.* " +
      "FROM provider_external_user INNER JOIN user_key_data ON provider_external_user.user_key_data_id=user_key_data.id " +
      "WHERE provider_external_user.provider_msa_id= :providerMsaId " +
      "AND user_key_data.encrypted_private_key_type = :keyPairType " +
      "AND lower(user_key_data.public_key_hex) = lower(:publicKeyHex) " +
      "AND user_key_data.key_usage_type = :keyUsageType")
  fun findOneByProviderMsaIdAndKeyPairTypeAndPublicKeyHexAndKeyUsageType(providerMsaId: BigInteger, keyPairType: KeyPairType, publicKeyHex: String, keyUsageType: KeyUsageType): Mono<ProviderExternalUser?>

  fun findByUserKeyDataIdIn(userKeyDataIds: List<BigInteger>): Flux<ProviderExternalUser>
}

interface ReactiveProviderExternalUserDao {
  fun findAllAndHydrateByUserAccountIdsIn(userAccountIds: Set<BigInteger>): Flux<ProviderExternalUser>
}

class ReactiveProviderExternalUserDaoImpl(private val databaseClient: DatabaseClient) : ReactiveProviderExternalUserDao {
  companion object {
    private const val USER_ACCOUNT_IDS_PARAMETER_NAME = "userAccountIds"
    private const val FIND_ALL_AND_HYDRATE_BY_USER_ACCOUNT_IDS_IN_QUERY: String = """
      SELECT ${ProviderExternalUser.ID_ALIAS} "${ProviderExternalUser.ID_ALIAS}", 
        ${ProviderExternalUser.PROVIDER_MSA_ID_ALIAS} "${ProviderExternalUser.PROVIDER_MSA_ID_ALIAS}", 
        ${ProviderExternalUser.PROVIDER_EXTERNAL_ID_ALIAS} "${ProviderExternalUser.PROVIDER_EXTERNAL_ID_ALIAS}", 
        ${ProviderExternalUser.USER_KEY_DATA_ID_ALIAS} "${ProviderExternalUser.USER_KEY_DATA_ID_ALIAS}", 
        ${ProviderExternalUser.CREATED_AT_ALIAS} "${ProviderExternalUser.CREATED_AT_ALIAS}", 
        ${ProviderExternalUser.LAST_MODIFIED_ALIAS} "${ProviderExternalUser.LAST_MODIFIED_ALIAS}",
        ${ProviderExternalUser.VERSION_ALIAS} "${ProviderExternalUser.VERSION_ALIAS}", 
        ${UserKeyData.ID_ALIAS} "${UserKeyData.ID_ALIAS}", 
        ${UserKeyData.USER_ACCOUNT_ID_ALIAS} "${UserKeyData.USER_ACCOUNT_ID_ALIAS}", 
        ${UserKeyData.PUBLIC_KEY_HEX_ALIAS} "${UserKeyData.PUBLIC_KEY_HEX_ALIAS}", 
        ${UserKeyData.ENCRYPTED_PRIVATE_KEY_HEX_ALIAS} "${UserKeyData.ENCRYPTED_PRIVATE_KEY_HEX_ALIAS}", 
        ${UserKeyData.ENCRYPTED_PRIVATE_KEY_TYPE_ALIAS} "${UserKeyData.ENCRYPTED_PRIVATE_KEY_TYPE_ALIAS}", 
        ${UserKeyData.KMS_ENCRYPTION_KEY_ID_ALIAS} "${UserKeyData.KMS_ENCRYPTION_KEY_ID_ALIAS}", 
        ${UserKeyData.KMS_ENCRYPTION_KEY_ID_TYPE_ALIAS} "${UserKeyData.KMS_ENCRYPTION_KEY_ID_TYPE_ALIAS}", 
        ${UserKeyData.KEY_USAGE_TYPE_ALIAS} "${UserKeyData.KEY_USAGE_TYPE_ALIAS}", 
        ${UserKeyData.CREATED_AT_ALIAS} "${UserKeyData.CREATED_AT_ALIAS}", 
        ${UserKeyData.LAST_MODIFIED_ALIAS} "${UserKeyData.LAST_MODIFIED_ALIAS}", 
        ${UserKeyData.VERSION_ALIAS} "${UserKeyData.VERSION_ALIAS}",
        ${UserAccount.ID_ALIAS} "${UserAccount.ID_ALIAS}", 
        ${UserAccount.CREATED_AT_ALIAS} "${UserAccount.CREATED_AT_ALIAS}", 
        ${UserAccount.LAST_MODIFIED_ALIAS} "${UserAccount.LAST_MODIFIED_ALIAS}",
        ${UserAccount.VERSION_ALIAS} "${UserAccount.VERSION_ALIAS}"
      FROM ${ProviderExternalUser.TABLE_NAME} ${ProviderExternalUser.ALIAS_PREFIX}
      INNER JOIN ${UserKeyData.TABLE_NAME} ${UserKeyData.ALIAS_PREFIX} ON ${ProviderExternalUser.USER_KEY_DATA_ID_ALIAS}=${UserKeyData.ID_ALIAS}
      INNER JOIN ${UserAccount.TABLE_NAME} ${UserAccount.ALIAS_PREFIX} ON ${UserKeyData.USER_ACCOUNT_ID_ALIAS}=${UserAccount.ID_ALIAS}
      WHERE ${UserAccount.ID_ALIAS} IN(:${USER_ACCOUNT_IDS_PARAMETER_NAME})
    """
  }

  override fun findAllAndHydrateByUserAccountIdsIn(userAccountIds: Set<BigInteger>): Flux<ProviderExternalUser> {
    return databaseClient.sql(FIND_ALL_AND_HYDRATE_BY_USER_ACCOUNT_IDS_IN_QUERY)
      .bind(USER_ACCOUNT_IDS_PARAMETER_NAME, userAccountIds)
      .fetch()
      .all()
      .map {
        ProviderExternalUser.fromRow(it)
      }
  }
}