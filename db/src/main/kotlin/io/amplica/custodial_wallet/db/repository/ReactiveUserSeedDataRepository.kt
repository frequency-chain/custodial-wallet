package io.amplica.custodial_wallet.db.repository

import com.fasterxml.jackson.annotation.JsonValue
import io.amplica.custodial_wallet.client.kms.KmsEncryptionAlgorithm
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigInteger
import java.time.Instant


enum class SeedUsageType(@JsonValue val type: String) {
  CONTEXT_ITEM_MASTER("CONTEXT_ITEM_MASTER"),
  HCP_MASTER("HCP_MASTER");
}

@Table(UserSeedData.TABLE_NAME)
data class UserSeedData(
  @Id
  var id: BigInteger? = null,

  @Column(USER_ACCOUNT_ID_COLUMN_NAME)
  val userAccountId: BigInteger,

  @Column(SEED_USAGE_TYPE_COLUMN_NAME)
  val seedUsageType: SeedUsageType,

  @Column(ENCRYPTED_SEED_HEX_COLUMN_NAME)
  val encryptedSeedHex: String,

  @Column(ENCRYPTED_SEED_PHRASE_HEX_COLUMN_NAME)
  val encryptedSeedPhraseHex: String?,

  @Column(KMS_ENCRYPTION_KEY_ID_COLUMN_NAME)
  val kmsEncryptionKeyId: String,

  @Column(KMS_ENCRYPTION_ALGORITHM_COLUMN_NAME)
  val kmsEncryptionAlgorithm: KmsEncryptionAlgorithm,

  @Column(CREATED_AT_COLUMN_NAME)
  val createdAt: BigInteger,

  @Column(LAST_MODIFIED_COLUMN_NAME)
  val lastModified: BigInteger,

  @Version
  var version: BigInteger?
) {

  companion object {
    const val TABLE_NAME = "user_seed_data"
    const val ALIAS_PREFIX = "usd"

    const val ID_COLUMN_NAME = "id"
    const val USER_ACCOUNT_ID_COLUMN_NAME = "user_account_id"
    const val SEED_USAGE_TYPE_COLUMN_NAME = "seed_usage_type"
    const val ENCRYPTED_SEED_PHRASE_HEX_COLUMN_NAME = "encrypted_seed_phrase_hex"
    const val ENCRYPTED_SEED_HEX_COLUMN_NAME = "encrypted_seed_hex"
    const val KMS_ENCRYPTION_KEY_ID_COLUMN_NAME = "kms_encryption_key_id"
    const val KMS_ENCRYPTION_ALGORITHM_COLUMN_NAME = "kms_encryption_algorithm"
    const val CREATED_AT_COLUMN_NAME = "created_at"
    const val LAST_MODIFIED_COLUMN_NAME = "last_modified"
    const val VERSION_COLUMN_NAME = "version"

    const val ID_ALIAS = "$ALIAS_PREFIX.$ID_COLUMN_NAME"
    const val USER_ACCOUNT_ID_ALIAS = "$ALIAS_PREFIX.$USER_ACCOUNT_ID_COLUMN_NAME"
    const val SEED_USAGE_TYPE_ALIAS = "$ALIAS_PREFIX.$SEED_USAGE_TYPE_COLUMN_NAME"
    const val ENCRYPTED_SEED_PHRASE_HEX_ALIAS = "$ALIAS_PREFIX.$ENCRYPTED_SEED_HEX_COLUMN_NAME"
    const val ENCRYPTED_SEED_HEX_ALIAS = "$ALIAS_PREFIX.$ENCRYPTED_SEED_HEX_COLUMN_NAME"
    const val KMS_ENCRYPTION_KEY_ID_ALIAS = "$ALIAS_PREFIX.$KMS_ENCRYPTION_KEY_ID_COLUMN_NAME"
    const val KMS_ENCRYPTION_ALGORITHM_ALIAS = "$ALIAS_PREFIX.$KMS_ENCRYPTION_ALGORITHM_COLUMN_NAME"
    const val CREATED_AT_ALIAS = "$ALIAS_PREFIX.$CREATED_AT_COLUMN_NAME"
    const val LAST_MODIFIED_ALIAS = "$ALIAS_PREFIX.$LAST_MODIFIED_COLUMN_NAME"
    const val VERSION_ALIAS = "$ALIAS_PREFIX.$VERSION_COLUMN_NAME"

    fun create(
      userAccountId: BigInteger,
      seedUsageType: SeedUsageType,
      encryptedSeedPhraseHex: String?,
      encryptedSeedHex: String,
      kmsEncryptionKeyId: String,
      kmsEncryptionAlgorithm: KmsEncryptionAlgorithm,
    ): UserSeedData {
      val now = Instant.now().toEpochMilli().toBigInteger()

      return UserSeedData(
        id = null,
        userAccountId = userAccountId,
        seedUsageType = seedUsageType,
        encryptedSeedPhraseHex = encryptedSeedPhraseHex,
        encryptedSeedHex = encryptedSeedHex,
        kmsEncryptionKeyId = kmsEncryptionKeyId,
        kmsEncryptionAlgorithm = kmsEncryptionAlgorithm,
        createdAt = now,
        lastModified = now,
        version = null
      )
    }
  }

}


interface ReactiveUserSeedDataRepository : ReactiveCrudRepository<UserSeedData, BigInteger> {

  fun findByUserAccountIdAndSeedUsageType(
    userAccountId: BigInteger,
    seedUsageType: SeedUsageType,
  ): Flux<UserSeedData>

  fun findFirstByUserAccountIdAndSeedUsageTypeOrderByCreatedAtDesc(
    userAccountId: BigInteger,
    seedUsageType: SeedUsageType,
  ): Mono<UserSeedData>

}
