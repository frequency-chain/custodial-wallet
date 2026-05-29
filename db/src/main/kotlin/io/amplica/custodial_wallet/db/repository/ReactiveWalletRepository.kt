package io.amplica.custodial_wallet.db.repository

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigInteger
import java.time.Instant

@Table(Wallet.TABLE_NAME)
data class Wallet(
  @Id
  val id: BigInteger? = null,

  @Column(USER_ACCOUNT_ID_COLUMN_NAME)
  val userAccountId: BigInteger,

  @Column(PUBLIC_KEY_COLUMN_NAME)
  val publicKeyBase64Url: String,

  @Column(CREATED_AT_COLUMN_NAME)
  val createdAt: Long,

  @Column(LAST_MODIFIED_COLUMN_NAME)
  val lastModified: Long,

  @Version
  var version: Long?
) {

  companion object {
    const val TABLE_NAME = "wallet"

    const val USER_ACCOUNT_ID_COLUMN_NAME = "user_account_id"
    const val PUBLIC_KEY_COLUMN_NAME = "public_key_base64_url"
    const val CREATED_AT_COLUMN_NAME = "created_at"
    const val LAST_MODIFIED_COLUMN_NAME = "last_modified"

    fun create(
      userAccountId: BigInteger,
      publicKeyBase64Url: String,
    ): Wallet {
      val now = Instant.now().toEpochMilli()
      return Wallet(
        null,
        userAccountId,
        publicKeyBase64Url,
        now,
        now,
        null
      )
    }
  }
}

interface ReactiveWalletRepository : ReactiveCrudRepository<Wallet, BigInteger> {
  fun findAllByUserAccountId(userAccountId: BigInteger): Flux<Wallet>
  fun deleteAllByUserAccountIdIn(userAccountIds: List<BigInteger>): Mono<Void>
}
