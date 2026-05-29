package io.amplica.custodial_wallet.db.repository

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import com.google.common.collect.FluentIterable
import io.amplica.custodial_wallet.client.kms.EncryptedKey
import io.amplica.custodial_wallet.client.kms.KmsEncryptionAlgorithm
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import io.amplica.custodial_wallet.util.toHex
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
import java.util.*


enum class KeyUsageType(@JsonValue val type: String) {
  ACCOUNT("ACCOUNT"),
  GRAPH("GRAPH"),
  ICS("ICS");

  companion object {
    @Suppress("UnstableApiUsage") //from is marked @Beta but its been beta for years
    private val TYPE_INDEX: Map<String, KeyUsageType> =
      FluentIterable.from(values()).uniqueIndex { it.type.uppercase(Locale.US) }

    @JvmStatic
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    fun fromType(type: String): KeyUsageType {
      return TYPE_INDEX[type.uppercase(Locale.US)]
        ?: throw IllegalArgumentException("Type=${type} is not recognized")
    }
  }
}

data class UserData(
  val userAccountId: BigInteger,
  val publicKeyHex: String,
  val encryptedPrivateKeyHex: String,
  val encryptedPrivateKeyType: KeyPairType,
  val kmsEncryptionKeyId: String,
  val kmsEncryptionKeyIdType: KmsEncryptionAlgorithm,
  val providerMsaId: BigInteger,
  val providerExternalId: String,
  val userKeyDataId: BigInteger,
  val providerExternalUserId: BigInteger,
  val userDetailValue: String,
  val userDetailType: UserDetailType,
  val userDetailPriority: Int,
)

@Table(UserKeyData.TABLE_NAME)
data class UserKeyData(
  @Id
  var id: BigInteger? = null,
  @Column(USER_ACCOUNT_ID_COLUMN_NAME)
  val userAccountId: BigInteger,
  @Column(PUBLIC_KEY_HEX_COLUMN_NAME)
  val publicKeyHex: String,
  @Column(ENCRYPTED_PRIVATE_KEY_HEX_COLUMN_NAME)
  val encryptedPrivateKeyHex: String,
  @Column(ENCRYPTED_PRIVATE_KEY_TYPE_COLUMN_NAME)
  val encryptedPrivateKeyType: KeyPairType,
  @Column(KMS_ENCRYPTION_KEY_ID_COLUMN_NAME)
  val kmsEncryptionKeyId: String,
  @Column(KMS_ENCRYPTION_KEY_ID_TYPE_COLUMN_NAME)
  val kmsEncryptionKeyIdType: KmsEncryptionAlgorithm,
  @Column(KEY_USAGE_TYPE_COLUMN_NAME)
  val keyUsageType: KeyUsageType,
  @Column(USER_SEED_DATA_ID_COLUMN_NAME)
  val userSeedDataId: BigInteger?,
  @Column(CREATED_AT_COLUMN_NAME)
  val createdAt: BigInteger,
  @Column(LAST_MODIFIED_COLUMN_NAME)
  val lastModified: BigInteger,
  @Version
  var version: BigInteger?
) {
  @Transient
  var userAccount: UserAccount? = null

  constructor(
    userAccountId: BigInteger,
    publicKeyHex: String,
    encryptedPrivateKeyHex: String,
    encryptedPrivateKeyType: KeyPairType,
    kmsEncryptionKeyId: String,
    kmsEncryptionKeyIdType: KmsEncryptionAlgorithm,
    keyUsageType: KeyUsageType,
    userSeedDataId: BigInteger?,
    createdAt: BigInteger,
    lastModified: BigInteger,
  ) : this(
    null,
    userAccountId,
    publicKeyHex,
    encryptedPrivateKeyHex,
    encryptedPrivateKeyType,
    kmsEncryptionKeyId,
    kmsEncryptionKeyIdType,
    keyUsageType,
    userSeedDataId,
    createdAt,
    lastModified,
    null
  )

  companion object {
    const val TABLE_NAME = "user_key_data"
    const val ALIAS_PREFIX = "ukd"
    const val ID_COLUMN_NAME = "id"
    const val USER_ACCOUNT_ID_COLUMN_NAME = "user_account_id"
    const val PUBLIC_KEY_HEX_COLUMN_NAME = "public_key_hex"
    const val ENCRYPTED_PRIVATE_KEY_HEX_COLUMN_NAME = "encrypted_private_key_hex"
    const val ENCRYPTED_PRIVATE_KEY_TYPE_COLUMN_NAME = "encrypted_private_key_type"
    const val KMS_ENCRYPTION_KEY_ID_COLUMN_NAME = "kms_encryption_key_id"
    const val KMS_ENCRYPTION_KEY_ID_TYPE_COLUMN_NAME = "kms_encryption_key_id_type"
    const val KEY_USAGE_TYPE_COLUMN_NAME = "key_usage_type"
    const val USER_SEED_DATA_ID_COLUMN_NAME = "user_seed_data_id"
    const val CREATED_AT_COLUMN_NAME = "created_at"
    const val LAST_MODIFIED_COLUMN_NAME = "last_modified"
    const val VERSION_COLUMN_NAME = "version"

    const val ID_ALIAS = "$ALIAS_PREFIX.$ID_COLUMN_NAME"
    const val USER_ACCOUNT_ID_ALIAS = "$ALIAS_PREFIX.$USER_ACCOUNT_ID_COLUMN_NAME"
    const val PUBLIC_KEY_HEX_ALIAS = "$ALIAS_PREFIX.$PUBLIC_KEY_HEX_COLUMN_NAME"
    const val ENCRYPTED_PRIVATE_KEY_HEX_ALIAS = "$ALIAS_PREFIX.$ENCRYPTED_PRIVATE_KEY_HEX_COLUMN_NAME"
    const val ENCRYPTED_PRIVATE_KEY_TYPE_ALIAS = "$ALIAS_PREFIX.$ENCRYPTED_PRIVATE_KEY_TYPE_COLUMN_NAME"
    const val KMS_ENCRYPTION_KEY_ID_ALIAS = "$ALIAS_PREFIX.$KMS_ENCRYPTION_KEY_ID_COLUMN_NAME"
    const val KMS_ENCRYPTION_KEY_ID_TYPE_ALIAS = "$ALIAS_PREFIX.$KMS_ENCRYPTION_KEY_ID_TYPE_COLUMN_NAME"
    const val KEY_USAGE_TYPE_ALIAS = "$ALIAS_PREFIX.$KEY_USAGE_TYPE_COLUMN_NAME"
    const val USER_SEED_DATA_ID_ALIAS = "$ALIAS_PREFIX.$USER_SEED_DATA_ID_COLUMN_NAME"
    const val CREATED_AT_ALIAS = "$ALIAS_PREFIX.$CREATED_AT_COLUMN_NAME"
    const val LAST_MODIFIED_ALIAS = "$ALIAS_PREFIX.$LAST_MODIFIED_COLUMN_NAME"
    const val VERSION_ALIAS = "$ALIAS_PREFIX.$VERSION_COLUMN_NAME"

    fun create(
      userAccountId: BigInteger,
      publicKey: ByteArray,
      encryptedPrivateKey: EncryptedKey,
      encryptedPrivateKeyType: KeyPairType,
      keyUsageType: KeyUsageType,
      userSeedDataId: BigInteger? = null,
    ): UserKeyData {
      val publicKeyHex = toHex(publicKey)
      val encryptedPrivateKeyHex = toHex(encryptedPrivateKey.encryptedValue)
      val kmsDecryptionKey = encryptedPrivateKey.kmsDecryptionKey
      val kmsEncryptionKeyId = kmsDecryptionKey.decryptionKeyId
      val kmsEncryptionKeyIdType = kmsDecryptionKey.decryptionAlgorithm
      val createdAt = Instant.now().toEpochMilli().toBigInteger()
      return UserKeyData(
        userAccountId,
        publicKeyHex,
        encryptedPrivateKeyHex,
        encryptedPrivateKeyType,
        kmsEncryptionKeyId,
        kmsEncryptionKeyIdType,
        keyUsageType,
        userSeedDataId,
        createdAt,
        createdAt
      )
    }

    fun fromRow(row: Map<String, Any>): UserKeyData {
      return UserKeyData(
        (row[ID_ALIAS] as Long).toBigInteger(),
        (row[USER_ACCOUNT_ID_ALIAS] as Long).toBigInteger(),
        row[PUBLIC_KEY_HEX_ALIAS] as String,
        row[ENCRYPTED_PRIVATE_KEY_HEX_ALIAS] as String,
        KeyPairType.fromType(row[ENCRYPTED_PRIVATE_KEY_TYPE_ALIAS] as String),
        row[KMS_ENCRYPTION_KEY_ID_ALIAS] as String,
        KmsEncryptionAlgorithm.fromAlgorithm(row[KMS_ENCRYPTION_KEY_ID_TYPE_ALIAS] as String),
        KeyUsageType.fromType(row[KEY_USAGE_TYPE_ALIAS] as String),
        (row[USER_SEED_DATA_ID_ALIAS] as Long?)?.toBigInteger(),
        (row[CREATED_AT_ALIAS] as Long).toBigInteger(),
        (row[LAST_MODIFIED_ALIAS] as Long).toBigInteger(),
        (row[VERSION_ALIAS] as Long).toBigInteger()
      )
    }
  }
}


interface ReactiveUserKeyDataRepository : ReactiveCrudRepository<UserKeyData, BigInteger> {
  fun findByUserAccountId(userAccountId: BigInteger): Flux<UserKeyData>
  fun findByUserAccountIdAndKeyUsageType(userAccountId: BigInteger, keyUsageType: KeyUsageType): Flux<UserKeyData>
  fun findByEncryptedPrivateKeyTypeAndPublicKeyHexIgnoreCaseIn(
    keyPairType: KeyPairType,
    publicKeysInHex: List<String>
  ): Flux<UserKeyData>

  fun findByUserAccountIdAndKeyUsageTypeAndEncryptedPrivateKeyType(
    userAccountId: BigInteger,
    keyUsageType: KeyUsageType,
    keyPairType: KeyPairType
  ): Flux<UserKeyData>

  @Query(
    value = "SELECT user_key_data.* " +
            "FROM user_key_data " +
            "JOIN provider_external_user " +
            "ON user_key_data.id = provider_external_user.user_key_data_id " +
            "JOIN provider_external_user_detail " +
            "ON provider_external_user.id = provider_external_user_detail.provider_external_user_id " +
            "WHERE provider_external_user.provider_msa_id = :providerMsaId " +
            "and provider_external_user_detail.user_detail_value = :userDetailValue " +
            "and provider_external_user_detail.user_detail_type = :userDetailType " +
            "and user_key_data.key_usage_type = (:keyUsageType)"
  )
  fun findByProviderMsaIdAndUserDetailValueAndUserDetailTypeAndKeyUsageType(
    providerMsaId: BigInteger,
    userDetailValue: String,
    userDetailType: UserDetailType,
    keyUsageType: KeyUsageType
  ): Flux<UserKeyData>

  @Query(
    "SELECT user_key_data.* " +
            "FROM user_key_data INNER JOIN provider_external_user ON user_key_data.id = provider_external_user.user_key_data_id " +
            "WHERE provider_external_user.provider_msa_id = :providerMsaId " +
            "AND user_key_data.encrypted_private_key_type = :keyPairType " +
            "AND lower(user_key_data.public_key_hex) = lower(:publicKeyHex) " +
            "AND user_key_data.key_usage_type = :keyUsageType"
  )
  fun findOneByProviderMsaIdAndKeyPairTypeAndPublicKeyHexAndKeyUsageType(
    providerMsaId: BigInteger,
    keyPairType: KeyPairType,
    publicKeyHex: String,
    keyUsageType: KeyUsageType
  ): Mono<UserKeyData?>

  fun deleteByUserAccountIdIn(userAccountIds: List<BigInteger>): Mono<Void>

  @Query(
    value = "SELECT user_key_data.* FROM user_key_data " +
            "WHERE user_key_data.user_account_id IN ( " +
            "    SELECT user_key_data.user_account_id " +
            "    FROM user_key_data " +
            "             INNER JOIN provider_external_user ON user_key_data.id = provider_external_user.user_key_data_id " +
            "    WHERE provider_external_user.provider_msa_id = :providerMsaId " +
            "      AND provider_external_user.provider_external_id = :providerExternalId " +
            ")  AND user_key_data.key_usage_type = :keyUsageType",
  )
  fun findByProviderMsaIdAndProviderExternalIdAndKeyUsageTypeInUserAccount(
    providerMsaId: BigInteger,
    providerExternalId: String,
    keyUsageType: KeyUsageType
  ): Flux<UserKeyData>

  @Query(
    value = """
      SELECT ukd.* 
      FROM user_key_data ukd
      INNER JOIN provider_external_user peu ON ukd.id=peu.user_key_data_id
      WHERE peu.provider_msa_id = :prpviderMsaId
        AND peu.provider_external_id = :providerExternalId
        AND ukd.key_usage_type = :keyUsageType
    """
  )
  fun findByProviderMsaIdAndProviderExternalIdAndKeyUsageType(
    providerMsaId: BigInteger,
    providerExternalId: String,
    keyUsageType: KeyUsageType
  ): Flux<UserKeyData>

  @Query(
    value = "SELECT user_key_data.*, provider_external_user.*, provider_external_user_detail.* " +
            "FROM user_key_data " +
            "JOIN provider_external_user " +
            "ON user_key_data.id = provider_external_user.user_key_data_id " +
            "JOIN provider_external_user_detail " +
            "ON provider_external_user.id = provider_external_user_detail.provider_external_user_id " +
            "WHERE provider_external_user_detail.user_detail_value = :userDetailValue " +
            "and provider_external_user_detail.user_detail_type = :userDetailType " +
            "and user_key_data.user_account_id " +
            "IN (:userAccountIds)",
  )
  fun findUserDataByUserAccountIds(
    userDetailValue: String,
    userDetailType: UserDetailType,
    userAccountIds: List<BigInteger>
  ): Flux<UserData>

  @Query(
    value = "SELECT user_key_data.*, provider_external_user.*, provider_external_user_detail.* " +
            "FROM provider_external_user_detail " +
            "JOIN provider_external_user " +
            "ON provider_external_user.id = provider_external_user_detail.provider_external_user_id " +
            "JOIN user_key_data " +
            "ON user_key_data.id = provider_external_user.user_key_data_id " +
            "WHERE user_key_data.user_account_id " +
            "IN (:userAccountIds) " +
            "ORDER BY provider_external_user_detail.user_detail_priority ASC",
  )
  fun findUserDataByUserAccountIds(
    userAccountIds: List<BigInteger>
  ): Flux<UserData>

  @Query(
    value = """
      SELECT ukd1.* 
      FROM user_key_data ukd1
      WHERE NOT EXISTS (
        SELECT 1
        FROM user_key_data ukd2
        WHERE ukd1.user_account_id = ukd2.user_account_id
        AND ukd2.encrypted_private_key_type = :missingKeyPairType
        AND ukd2.key_usage_type = :keyUsageType
        ORDER BY ukd1.created_at ASC
      )
      AND ukd1.encrypted_private_key_type = :existingKeyPairType
      AND ukd1.key_usage_type = :keyUsageType
      ORDER BY ukd1.user_account_id ASC
      LIMIT :limit
      OFFSET :offset
    """
  )
  fun findUsersWithoutKeyOfType(
    existingKeyPairType: KeyPairType,
    keyUsageType: KeyUsageType,
    missingKeyPairType: KeyPairType,
    limit: Int,
    offset: Int
  ): Flux<UserKeyData>

  fun findFirstByUserSeedDataIdOrderByCreatedAtDesc(userSeedDataId: BigInteger): Mono<UserKeyData>

}