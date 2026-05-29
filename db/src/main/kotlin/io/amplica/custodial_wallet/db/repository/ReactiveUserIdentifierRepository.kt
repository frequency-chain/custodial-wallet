package io.amplica.custodial_wallet.db.repository

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono
import java.math.BigInteger
import java.time.Instant


@Table(UserIdentifier.TABLE_NAME)
data class UserIdentifier(
  @Id
  var id: BigInteger?,
  @Column(VALUE_COLUMN_NAME)
  val value: String,
  @Column(TYPE_COLUMN_NAME)
  val type: UserDetailType,
  @Column(VERIFIED_DATE)
  val verifiedDate: BigInteger?,
  @Column(CREATED_AT_COLUMN_NAME)
  val createdAt: BigInteger,
  @Column(LAST_MODIFIED_COLUMN_NAME)
  val lastModified: BigInteger,
  @Version
  var version: BigInteger? = null
) {

  constructor(
    value: String,
    type: UserDetailType,
    verifiedDate: BigInteger?,
    createdAt: BigInteger,
    lastModified: BigInteger,
  ) :
      this(
        null,
        value,
        type,
        verifiedDate,
        createdAt,
        lastModified
      )

  companion object {
    const val TABLE_NAME = "user_identifier"

    const val VALUE_COLUMN_NAME = "value"
    const val TYPE_COLUMN_NAME = "type"
    const val VERIFIED_DATE = "verified_date"
    const val CREATED_AT_COLUMN_NAME = "created_at"
    const val LAST_MODIFIED_COLUMN_NAME = "last_modified"

    const val ALIAS = "ui"
    const val ID_ALIAS = "$ALIAS.id"
    const val VALUE_ALIAS = "$ALIAS.$VALUE_COLUMN_NAME"
    const val TYPE_ALIAS = "$ALIAS.$TYPE_COLUMN_NAME"

    fun create(userDetail: UserDetail): UserIdentifier {
      val now = Instant.now().toEpochMilli().toBigInteger()
      return UserIdentifier(
        userDetail.value,
        userDetail.type,
        now,
        now,
        now
      )
    }
  }

  fun updateVerifiedDateToNow(): UserIdentifier {
    return this.copy(verifiedDate = Instant.now().toEpochMilli().toBigInteger())
  }
}

interface ReactiveUserIdentifierRepository : ReactiveCrudRepository<UserIdentifier, BigInteger> {
  // There is a uniqueness constraint at the schema level that guarantees no more than one
  fun findOneByValueAndType(value: String, type: UserDetailType): Mono<UserIdentifier>
}


