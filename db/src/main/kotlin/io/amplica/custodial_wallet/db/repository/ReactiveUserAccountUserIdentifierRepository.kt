package io.amplica.custodial_wallet.db.repository

import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigInteger


@Table(UserAccountUserIdentifier.TABLE_NAME)
class UserAccountUserIdentifier(
  @Column(USER_ACCOUNT_ID_COLUMN_NAME)
  val userAccountId: BigInteger,
  @Column(USER_IDENTIFIER_ID_COLUMN_NAME)
  val userIdentifierId: BigInteger,
) {

  companion object {
    const val TABLE_NAME = "user_account_user_identifier"

    const val USER_ACCOUNT_ID_COLUMN_NAME = "user_account_id"
    const val USER_IDENTIFIER_ID_COLUMN_NAME = "user_identifier_id"

    const val ALIAS = "uaui"
    const val ID_ALIAS = "$ALIAS.id"
    const val USER_ACCOUNT_ID_ALIAS = "$ALIAS.$USER_ACCOUNT_ID_COLUMN_NAME"
    const val USER_IDENTIFIER_ID_ALIAS = "$ALIAS.$USER_IDENTIFIER_ID_COLUMN_NAME"
  }
}

// NOTE: Spring JDBC does not support composite primary keys--see https://github.com/spring-projects/spring-data-relational/issues/574
interface ReactiveUserAccountUserIdentifierRepository :
  ReactiveCrudRepository<UserAccountUserIdentifier, UserAccountUserIdentifier> {
  fun findByUserAccountId(userAccountId: BigInteger): Flux<UserAccountUserIdentifier>
  // Guaranteed by UNIQUE constraint
  fun findByUserIdentifierId(userIdentifierId: BigInteger): Mono<UserAccountUserIdentifier>

  // Guaranteed by UNIQUE constraint
  fun findByUserAccountIdAndUserIdentifierId(userAccountId: BigInteger, userIdentifierId: BigInteger): Mono<UserAccountUserIdentifier>

  fun deleteByUserAccountId(userAccountId: BigInteger): Mono<Void>
  fun deleteByUserAccountIdIn(userAccountIds: List<BigInteger>): Mono<Void>
}
