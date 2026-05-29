package io.amplica.custodial_wallet.db.repository

import org.springframework.data.annotation.Id
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

data class UserAccountWithCount(
  val id: BigInteger?,
  val createdAt: BigInteger,
  val lastModified: BigInteger,
  var version: BigInteger?,
  val count: Int
)

data class UserAccountsWithOffsetAndLimit(
  val userAccountList: List<UserAccountWithCount>,
  val offset: Int,
  val limit: Int
)

@Table(UserAccount.TABLE_NAME)
class UserAccount(
  @Id
  var id: BigInteger? = null,
  @Column(CREATED_AT_COLUMN_NAME)
  val createdAt: BigInteger,
  @Column(LAST_MODIFIED_COLUMN_NAME)
  val lastModified: BigInteger,
  @Version
  var version: BigInteger?
) {
  constructor(
    createdAt: BigInteger,
    lastModified: BigInteger,
  ) :
      this(
        null,
        createdAt,
        lastModified,
        null
      )

  companion object {
    const val TABLE_NAME = "user_account"
    const val ALIAS_PREFIX = "ua"
    const val ID_COLUMN_NAME = "id"
    const val CREATED_AT_COLUMN_NAME = "created_at"
    const val LAST_MODIFIED_COLUMN_NAME = "last_modified"
    const val VERSION_COLUMN_NAME = "version"

    const val ID_ALIAS = "$ALIAS_PREFIX.$ID_COLUMN_NAME"
    const val CREATED_AT_ALIAS = "$ALIAS_PREFIX.$CREATED_AT_COLUMN_NAME"
    const val LAST_MODIFIED_ALIAS = "$ALIAS_PREFIX.${LAST_MODIFIED_COLUMN_NAME}"
    const val VERSION_ALIAS = "$ALIAS_PREFIX.${VERSION_COLUMN_NAME}"

    fun create(): UserAccount {
      val createdAt = Instant.now().toEpochMilli().toBigInteger()
      return UserAccount(
        createdAt,
        createdAt
      )
    }

    fun fromRow(row: Map<String, Any>): UserAccount {
      return UserAccount((row[ID_ALIAS] as Long).toBigInteger(),
        (row[CREATED_AT_ALIAS] as Long).toBigInteger(),
        (row[LAST_MODIFIED_ALIAS] as Long).toBigInteger(),
        (row[VERSION_ALIAS] as Long).toBigInteger())
    }
  }
}

interface ReactiveUserAccountRepository : ReactiveCrudRepository<UserAccount, BigInteger>, ReactiveUserAccountRepositoryDao {
  @Query(value = "SELECT user_account.*, count(*) OVER() " +
          "FROM user_account " +
          "JOIN provider_external_user_detail " +
          "ON user_account.id = provider_external_user_detail.user_account_id " +
          "WHERE provider_external_user_detail.user_detail_value = :userDetailValue " +
          "and provider_external_user_detail.user_detail_type = :userDetailType " +
          "ORDER BY user_account.id " +
          "OFFSET :offset " +
          "LIMIT :limit")
  fun findByUserDetail(userDetailValue: String, userDetailType: UserDetailType, offset: Int, limit: Int): Flux<UserAccountWithCount>

  @Query("""
    SELECT ${UserAccount.ALIAS_PREFIX}.*
    FROM ${UserIdentifier.TABLE_NAME} ${UserIdentifier.ALIAS}
    INNER JOIN ${UserAccountUserIdentifier.TABLE_NAME} ${UserAccountUserIdentifier.ALIAS}
      ON ${UserIdentifier.ID_ALIAS} = ${UserAccountUserIdentifier.USER_IDENTIFIER_ID_ALIAS}
    INNER JOIN ${UserAccount.TABLE_NAME} ${UserAccount.ALIAS_PREFIX}
      ON ${UserAccountUserIdentifier.USER_ACCOUNT_ID_ALIAS} = ${UserAccount.ID_ALIAS}
    WHERE ${UserIdentifier.VALUE_ALIAS} = :value
      AND ${UserIdentifier.TYPE_ALIAS} = :type
    """)
  fun findByUserIdentifier(value: String, type: UserDetailType): Mono<UserAccount>

  @Query("""
    SELECT ${UserAccount.ALIAS_PREFIX}.*
    FROM ${ProviderExternalUser.TABLE_NAME} ${ProviderExternalUser.ALIAS_PREFIX}
    INNER JOIN ${UserKeyData.TABLE_NAME} ${UserKeyData.ALIAS_PREFIX} ON ${ProviderExternalUser.USER_KEY_DATA_ID_ALIAS} = ${UserKeyData.ID_ALIAS}
    INNER JOIN ${UserAccount.TABLE_NAME} ${UserAccount.ALIAS_PREFIX} ON ${UserKeyData.USER_ACCOUNT_ID_ALIAS} = ${UserAccount.ID_ALIAS}
    WHERE ${ProviderExternalUser.PROVIDER_EXTERNAL_ID_ALIAS} = :providerExternalId
      AND ${ProviderExternalUser.PROVIDER_MSA_ID_ALIAS} = :providerMsaId
    """)
  fun findAllByProviderExternalUserProviderExternalId(providerMsaId: BigInteger, providerExternalId: String): Flux<UserAccount>
}

interface ReactiveUserAccountRepositoryDao {
  fun findByUserIdentifiers(userIdentifiers: List<UserDetail>): Flux<UserAccount>
}

class ReactiveUserAccountRepositoryDaoImpl(private val databaseClient: DatabaseClient) : ReactiveUserAccountRepositoryDao {

  override fun findByUserIdentifiers(userIdentifiers: List<UserDetail>): Flux<UserAccount> {
    val conditions = userIdentifiers.map { userDetail ->
      "(${UserIdentifier.VALUE_ALIAS} = '${userDetail.value}' AND ${UserIdentifier.TYPE_ALIAS} = '${userDetail.type}')"
    }

    val query = """
        SELECT 
          ${UserAccount.ID_ALIAS} "${UserAccount.ID_ALIAS}", 
          ${UserAccount.CREATED_AT_ALIAS} "${UserAccount.CREATED_AT_ALIAS}", 
          ${UserAccount.LAST_MODIFIED_ALIAS} "${UserAccount.LAST_MODIFIED_ALIAS}",
          ${UserAccount.VERSION_ALIAS} "${UserAccount.VERSION_ALIAS}"
        FROM ${UserIdentifier.TABLE_NAME} ${UserIdentifier.ALIAS}
        INNER JOIN ${UserAccountUserIdentifier.TABLE_NAME} ${UserAccountUserIdentifier.ALIAS}
          ON ${UserIdentifier.ID_ALIAS} = ${UserAccountUserIdentifier.USER_IDENTIFIER_ID_ALIAS}
        INNER JOIN ${UserAccount.TABLE_NAME} ${UserAccount.ALIAS_PREFIX}
          ON ${UserAccountUserIdentifier.USER_ACCOUNT_ID_ALIAS} = ${UserAccount.ID_ALIAS}
        WHERE
          ${conditions.joinToString(" OR ")}
    """.trimIndent()

    return databaseClient.sql(query)
      .fetch()
      .all()
      .map { row ->
        UserAccount.fromRow(row)
      }
  }
}