package io.amplica.custodial_wallet.db.repository

import com.fasterxml.jackson.annotation.JsonValue
import io.amplica.custodial_wallet.client.kms.KmsEncryptionAlgorithm
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.domain.Pageable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigInteger
import java.time.Instant

enum class DerivedKeyUsageType(@JsonValue val type: String) {
  CONTEXT_ITEM("CONTEXT_ITEM"),
  CONTEXT_GROUP("CONTEXT_GROUP"),
  ON_CHAIN("ON_CHAIN");
}

@Table(UserDerivedKeyData.TABLE_NAME)
data class UserDerivedKeyData(

  @Id
  var id: BigInteger? = null,

  @Column(USER_SEED_DATA_ID_COLUMN_NAME)
  val userSeedDataId: BigInteger,

  @Column(DERIVATION_PATH_COLUMN_NAME)
  val derivationPath: String,

  @Column(DERIVED_KEY_USAGE_TYPE_COLUMN_NAME)
  val derivedKeyUsageType: DerivedKeyUsageType,

  @Column(ENCRYPTED_KEY_HEX_COLUMN_NAME)
  val encryptedKeyHex: String,

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
    const val TABLE_NAME = "user_derived_key_data"
    const val ALIAS_PREFIX = "udkd"

    const val ID_COLUMN_NAME = "id"
    const val USER_SEED_DATA_ID_COLUMN_NAME = "user_seed_data_id"
    const val DERIVATION_PATH_COLUMN_NAME = "derivation_path"
    const val DERIVED_KEY_USAGE_TYPE_COLUMN_NAME = "derived_key_usage_type"
    const val ENCRYPTED_KEY_HEX_COLUMN_NAME = "encrypted_key_hex"
    const val KMS_ENCRYPTION_KEY_ID_COLUMN_NAME = "kms_encryption_key_id"
    const val KMS_ENCRYPTION_ALGORITHM_COLUMN_NAME = "kms_encryption_algorithm"
    const val CREATED_AT_COLUMN_NAME = "created_at"
    const val LAST_MODIFIED_COLUMN_NAME = "last_modified"
    const val VERSION_COLUMN_NAME = "version"

    const val ID_ALIAS = "$ALIAS_PREFIX.$ID_COLUMN_NAME"
    const val USER_SEED_DATA_ID_ALIAS = "$ALIAS_PREFIX.$USER_SEED_DATA_ID_COLUMN_NAME"
    const val DERIVATION_PATH_ALIAS = "$ALIAS_PREFIX.$DERIVATION_PATH_COLUMN_NAME"
    const val DERIVED_KEY_USAGE_TYPE_ALIAS = "$ALIAS_PREFIX.$DERIVED_KEY_USAGE_TYPE_COLUMN_NAME"
    const val ENCRYPTED_KEY_HEX_ALIAS = "$ALIAS_PREFIX.$ENCRYPTED_KEY_HEX_COLUMN_NAME"
    const val KMS_ENCRYPTION_KEY_ID_ALIAS = "$ALIAS_PREFIX.$KMS_ENCRYPTION_KEY_ID_COLUMN_NAME"
    const val KMS_ENCRYPTION_ALGORITHM_ALIAS = "$ALIAS_PREFIX.$KMS_ENCRYPTION_ALGORITHM_COLUMN_NAME"
    const val CREATED_AT_ALIAS = "$ALIAS_PREFIX.$CREATED_AT_COLUMN_NAME"
    const val LAST_MODIFIED_ALIAS = "$ALIAS_PREFIX.$LAST_MODIFIED_COLUMN_NAME"
    const val VERSION_ALIAS = "$ALIAS_PREFIX.$VERSION_COLUMN_NAME"

    fun create(
      userSeedDataId: BigInteger,
      derivationPath: String,
      derivedKeyUsageType: DerivedKeyUsageType,
      encryptedKeyHex: String,
      kmsEncryptionKeyId: String,
      kmsEncryptionAlgorithm: KmsEncryptionAlgorithm
    ): UserDerivedKeyData {
      val now = Instant.now().toEpochMilli().toBigInteger()

      return UserDerivedKeyData(
        id = null,
        userSeedDataId = userSeedDataId,
        derivationPath = derivationPath,
        derivedKeyUsageType = derivedKeyUsageType,
        encryptedKeyHex = encryptedKeyHex,
        kmsEncryptionKeyId = kmsEncryptionKeyId,
        kmsEncryptionAlgorithm = kmsEncryptionAlgorithm,
        createdAt = now,
        lastModified = now,
        version = null
      )
    }
  }
}


interface ReactiveUserDerivedKeyDataRepository : ReactiveCrudRepository<UserDerivedKeyData, BigInteger> {

  fun findByUserSeedDataId(userSeedDataId: BigInteger): Flux<UserDerivedKeyData>

  fun findByUserSeedDataIdAndDerivedKeyUsageType(
    userSeedDataId: BigInteger,
    derivedKeyUsageType: DerivedKeyUsageType
  ): Flux<UserDerivedKeyData>

  fun findByDerivationPath(
    derivationPath: String,
    pageable: Pageable
  ): Flux<UserDerivedKeyData>

  fun findFirstByDerivationPathOrderByCreatedAtDesc(derivationPath: String): Mono<UserDerivedKeyData>

  fun findFirstByDerivationPathStartsWithOrderByCreatedAtDesc(derivationPath: String): Mono<UserDerivedKeyData>

  fun findFirstByUserSeedDataIdAndDerivedKeyUsageTypeOrderByCreatedAtDesc(
    userSeedDataId: BigInteger,
    derivedKeyUsageType: DerivedKeyUsageType
  ): Mono<UserDerivedKeyData>
}
