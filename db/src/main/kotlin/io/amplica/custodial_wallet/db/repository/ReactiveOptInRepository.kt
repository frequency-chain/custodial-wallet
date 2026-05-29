package io.amplica.custodial_wallet.db.repository

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import com.google.common.collect.FluentIterable
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigInteger
import java.time.Instant
import java.util.*

enum class OptInType(@JsonValue val type: String) {
  COMMUNITY_REWARDS("communityRewards");

  companion object {
    @Suppress("UnstableApiUsage") //from is marked @Beta but its been beta for years
    private val TYPE_INDEX: Map<String, OptInType> =
      FluentIterable.from(entries.toTypedArray()).uniqueIndex { it.type.uppercase(Locale.US) }

    @JvmStatic
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    fun fromType(type: String): OptInType {
      return TYPE_INDEX[type.uppercase(Locale.US)]
        ?: throw IllegalArgumentException("Type=${type} is not recognized")
    }
  }
}

@Table(CustodialWalletOptIn.TABLE_NAME)
data class CustodialWalletOptIn(
  @Id
  var id: BigInteger? = null,
  @Column(USER_ACCOUNT_ID_COLUMN_NAME)
  val userAccountId: BigInteger,
  @Column(OPT_IN_TYPE_COLUMN_NAME)
  val optInType: OptInType,
  @Column(IS_OPTED_IN_COLUMN_NAME)
  val isOptedIn: Boolean,
  @Column(CREATED_AT_COLUMN_NAME)
  val createdAt: BigInteger,
  @Column(LAST_MODIFIED_COLUMN_NAME)
  val lastModified: BigInteger,
  @Version
  var version: BigInteger?
) {
  constructor(
    userAccountId: BigInteger,
    optInType: OptInType,
    isOptedIn: Boolean,
    createdAt: BigInteger,
    lastModified: BigInteger,
  ) : this(null, userAccountId, optInType, isOptedIn, createdAt, lastModified, null)
  companion object {
    const val TABLE_NAME = "opt_in"
    const val ALIAS_PREFIX = "oi"
    const val ID_COLUMN_NAME = "id"
    const val USER_ACCOUNT_ID_COLUMN_NAME = "user_account_id"
    const val OPT_IN_TYPE_COLUMN_NAME = "opt_in_type"
    const val IS_OPTED_IN_COLUMN_NAME = "is_opted_in"
    const val CREATED_AT_COLUMN_NAME = "created_at"
    const val LAST_MODIFIED_COLUMN_NAME = "last_modified"
    const val VERSION_COLUMN_NAME = "version"

    const val ID_ALIAS = "$ALIAS_PREFIX.${ID_COLUMN_NAME}"
    const val USER_ACCOUNT_ID_ALIAS = "$ALIAS_PREFIX.${USER_ACCOUNT_ID_COLUMN_NAME}"
    const val OPT_IN_ALIAS = "$ALIAS_PREFIX.${OPT_IN_TYPE_COLUMN_NAME}"
    const val IS_OPTED_IN_ALIAS = "$ALIAS_PREFIX.${IS_OPTED_IN_COLUMN_NAME}"
    const val CREATED_AT_ALIAS = "$ALIAS_PREFIX.${CREATED_AT_COLUMN_NAME}"
    const val LAST_MODIFIED_ALIAS = "$ALIAS_PREFIX.${LAST_MODIFIED_COLUMN_NAME}"
    const val VERSION_ALIAS = "$ALIAS_PREFIX.${VERSION_COLUMN_NAME}"

    fun create(
      userAccountId: BigInteger,
      optInType: OptInType,
      isOptedIn: Boolean
    ): CustodialWalletOptIn {
      val createdAt = Instant.now().toEpochMilli().toBigInteger()
      return CustodialWalletOptIn(
        userAccountId,
        optInType,
        isOptedIn,
        createdAt,
        createdAt
      )
    }
  }
}

interface ReactiveOptInRepository : ReactiveCrudRepository<CustodialWalletOptIn, BigInteger> {
  fun findAllByUserAccountId(userAccountId: BigInteger): Flux<CustodialWalletOptIn>

  // Guaranteed by UNIQUE constraint
  fun findByUserAccountIdAndOptInType(
    userAccountId: BigInteger,
    optInType: OptInType
  ): Mono<CustodialWalletOptIn>
}
