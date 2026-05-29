package io.amplica.custodial_wallet.db.repository

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import com.google.common.collect.FluentIterable
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono
import java.math.BigInteger
import java.time.Instant
import java.util.*


enum class KeyDerivationAlgorithmType(@JsonValue val type: String) {
  BCRYPT("BCRYPT");

  companion object {
    @Suppress("UnstableApiUsage") //from is marked @Beta but its been beta for years
    private val TYPE_INDEX: Map<String, KeyDerivationAlgorithmType> =
      FluentIterable.from(entries.toTypedArray()).uniqueIndex { it.type.uppercase(Locale.US) }

    @JvmStatic
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    fun fromType(type: String): KeyDerivationAlgorithmType {
      return TYPE_INDEX[type.uppercase(Locale.US)]
        ?: throw IllegalArgumentException("Type=${type} is not recognized")
    }
  }
}

@Table(UserPassword.TABLE_NAME)
data class UserPassword(
  @Id
  var id: BigInteger? = null,
  @Column(USER_ACCOUNT_ID_COLUMN_NAME)
  val userAccountId: BigInteger,
  @Column(KEY_DERIVATION_ALGORITHM_COLUMN_NAME)
  val keyDerivationAlgorithmType: KeyDerivationAlgorithmType,
  @Column(HASH_COLUMN_NAME)
  val hash: String,
  @Column(CREATED_AT_COLUMN_NAME)
  val createdAt: BigInteger,
  @Column(LAST_MODIFIED_COLUMN_NAME)
  val lastModified: BigInteger,
  @Version
  var version: BigInteger?
) {
  constructor(
    userAccountId: BigInteger,
    hashingAlgorithm: KeyDerivationAlgorithmType,
    hash: String,
    createdAt: BigInteger,
    lastModified: BigInteger,
  ) : this(null, userAccountId, hashingAlgorithm, hash, createdAt, lastModified, null)

  companion object {
    const val TABLE_NAME = "user_password"
    const val ALIAS_PREFIX = "up"
    const val ID_COLUMN_NAME = "id"
    const val USER_ACCOUNT_ID_COLUMN_NAME = "user_account_id"
    const val KEY_DERIVATION_ALGORITHM_COLUMN_NAME = "key_derivation_algorithm"
    const val HASH_COLUMN_NAME = "hash"
    const val CREATED_AT_COLUMN_NAME = "created_at"
    const val LAST_MODIFIED_COLUMN_NAME = "last_modified"
    const val VERSION_COLUMN_NAME = "version"

    const val ID_ALIAS = "$ALIAS_PREFIX.$ID_COLUMN_NAME"
    const val USER_ACCOUNT_ID_ALIAS = "$ALIAS_PREFIX.$USER_ACCOUNT_ID_COLUMN_NAME"
    const val KEY_DERIVATION_ALGORITHM_ALIAS = "$ALIAS_PREFIX.$KEY_DERIVATION_ALGORITHM_COLUMN_NAME"
    const val HASH_ALIAS = "$ALIAS_PREFIX.$HASH_COLUMN_NAME"
    const val CREATED_AT_ALIAS = "$ALIAS_PREFIX.$CREATED_AT_COLUMN_NAME"
    const val LAST_MODIFIED_ALIAS = "$ALIAS_PREFIX.$LAST_MODIFIED_COLUMN_NAME"
    const val VERSION_ALIAS = "$ALIAS_PREFIX.$VERSION_COLUMN_NAME"

    fun create(
      userAccountId: BigInteger,
      keyDerivationAlgorithmType: KeyDerivationAlgorithmType,
      hash: String
    ): UserPassword {
      val createdAt = Instant.now().toEpochMilli().toBigInteger()
      return UserPassword(
        userAccountId,
        keyDerivationAlgorithmType,
        hash,
        createdAt,
        createdAt
      )
    }

    fun update(oldPassword: UserPassword, newKeyDerivationAlgorithmType: KeyDerivationAlgorithmType, newHash: String): UserPassword {
      val lastModified = Instant.now().toEpochMilli().toBigInteger()
      return UserPassword(
        oldPassword.id,
        oldPassword.userAccountId,
        newKeyDerivationAlgorithmType,
        newHash,
        oldPassword.createdAt,
        lastModified,
        oldPassword.version
      )
    }

    fun fromRow(row: Map<String, Any>): UserPassword {
      return UserPassword(
        (row[ID_ALIAS] as Long).toBigInteger(),
        (row[USER_ACCOUNT_ID_ALIAS] as Long).toBigInteger(),
        KeyDerivationAlgorithmType.fromType(row[KEY_DERIVATION_ALGORITHM_ALIAS] as String),
        (row[HASH_ALIAS] as String),
        (row[CREATED_AT_ALIAS] as Long).toBigInteger(),
        (row[LAST_MODIFIED_ALIAS] as Long).toBigInteger(),
        (row[VERSION_ALIAS] as Long).toBigInteger()
      )
    }
  }
}

interface ReactiveUserPasswordRepository : ReactiveCrudRepository<UserPassword, BigInteger> {

  @Query(
    value = """
      SELECT ${UserPassword.ALIAS_PREFIX}.*
      FROM ${UserPassword.TABLE_NAME} ${UserPassword.ALIAS_PREFIX}
      INNER JOIN ${UserKeyData.TABLE_NAME} ${UserKeyData.ALIAS_PREFIX} ON ${UserKeyData.USER_ACCOUNT_ID_ALIAS} = ${UserPassword.USER_ACCOUNT_ID_ALIAS}
      WHERE lower(${UserKeyData.PUBLIC_KEY_HEX_ALIAS}) = lower(:publicKeyHex)
    """
  )
  fun findUserPasswordByPublicKeyHex(publicKeyHex: String): Mono<UserPassword?>

  @Query(
    value = """
      SELECT ${UserPassword.ALIAS_PREFIX}.*
      FROM ${UserPassword.TABLE_NAME} ${UserPassword.ALIAS_PREFIX}
      INNER JOIN ${UserKeyData.TABLE_NAME} ${UserKeyData.ALIAS_PREFIX} ON ${UserKeyData.USER_ACCOUNT_ID_ALIAS} = ${UserPassword.USER_ACCOUNT_ID_ALIAS}
      INNER JOIN ${ProviderExternalUser.TABLE_NAME} ${ProviderExternalUser.ALIAS_PREFIX} ON ${ProviderExternalUser.USER_KEY_DATA_ID_ALIAS} = ${UserKeyData.ID_ALIAS}
      WHERE ${ProviderExternalUser.PROVIDER_MSA_ID_ALIAS} = :providerMsaId
      AND ${ProviderExternalUser.PROVIDER_EXTERNAL_ID_ALIAS} = :providerExternalId
    """
  )
  fun findUserPasswordByProviderMsaIdAndProviderExternalId(
    providerMsaId: BigInteger,
    providerExternalId: String
  ): Mono<UserPassword?>

  fun findByUserAccountId(userAccountId: BigInteger): Mono<UserPassword?>
  fun deleteByUserAccountId(userAccountId: BigInteger): Mono<Void>
  fun deleteByUserAccountIdIn(userAccountIds: List<BigInteger>): Mono<Void>
}
