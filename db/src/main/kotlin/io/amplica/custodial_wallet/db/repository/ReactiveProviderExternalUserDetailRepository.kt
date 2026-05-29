package io.amplica.custodial_wallet.db.repository

import com.fasterxml.jackson.annotation.JsonValue
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifierType
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifier
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.annotation.Version
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.r2dbc.core.DatabaseClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigInteger
import java.time.Instant
import java.util.*

enum class UserDetailType(@JsonValue val type: String) {
  EMAIL("EMAIL"),
  PHONE_NUMBER("PHONE_NUMBER");

  companion object {
    /**
     * Alternate constructor that accepts the Redis DTO `UserIdentifierType`
     */
    fun fromUserIdentifierType(userIdentifierType: UserIdentifierType): UserDetailType {
      return when (userIdentifierType) {
        UserIdentifierType.EMAIL -> EMAIL
        UserIdentifierType.PHONE_NUMBER -> PHONE_NUMBER
      }
    }
  }
}

data class UserDetail(
  val value: String,
  val type: UserDetailType,
  @Deprecated("This is no longer maintained in any way, it should not be used") val priority: Int = 1
) {
  companion object {
    fun fromProviderExternalUserDetail(providerExternalUserDetail: ProviderExternalUserDetail): UserDetail {
      return UserDetail(
        providerExternalUserDetail.userDetailValue,
        providerExternalUserDetail.userDetailType,
        providerExternalUserDetail.userDetailPriority
      )
    }

    fun fromUserIdentifier(userIdentifier: UserIdentifier): UserDetail {
      return UserDetail(
        userIdentifier.value,
        UserDetailType.fromUserIdentifierType(userIdentifier.type),
        0 //Defaulting to 0 as we are no longer using this in the API but it's non nullable
      )
    }
  }
}

@Table("provider_external_user_detail")
class ProviderExternalUserDetail(
  @Id
  var id: BigInteger? = null,
  @Column("provider_external_user_id")
  val providerExternalUserId: BigInteger,
  @Column("user_account_id")
  val userAccountId: BigInteger,
  @Column("user_detail_value")
  val userDetailValue: String,
  @Column("user_detail_type")
  val userDetailType: UserDetailType,
  @Column("user_detail_priority")
  val userDetailPriority: Int,
  @Column("user_identifier_id")
  val userIdentifierId: BigInteger,
  @Column("created_at")
  val createdAt: BigInteger,
  @Column("last_modified")
  var lastModified: BigInteger,
  @Version
  var version: BigInteger?,
) {
  @Transient
  var providerExternalUser: ProviderExternalUser? = null

  constructor(
    providerExternalUserId: BigInteger,
    userAccountId: BigInteger,
    userDetailValue: String,
    userDetailType: UserDetailType,
    userDetailPriority: Int,
    userIdentifierId: BigInteger,
    createdAt: BigInteger,
    lastModified: BigInteger,
  ) :
      this(
        null,
        providerExternalUserId,
        userAccountId,
        userDetailValue,
        userDetailType,
        userDetailPriority,
        userIdentifierId,
        createdAt,
        lastModified,
        null
      )

  companion object {
    fun create(providerExternalUserId: BigInteger, userAccountId: BigInteger, userDetail: UserDetail, userIdentifierId: BigInteger): ProviderExternalUserDetail {
      val createdAt = Instant.now().toEpochMilli().toBigInteger()
      return ProviderExternalUserDetail(
        providerExternalUserId,
        userAccountId,
        userDetail.value,
        userDetail.type,
        userDetail.priority,
        userIdentifierId,
        createdAt,
        createdAt
      )
    }
  }
}

interface ReactiveProviderExternalUserDetailRepository : ReactiveCrudRepository<ProviderExternalUserDetail, BigInteger>,
  ReactiveProviderExternalUserDetailDao {
  fun findByUserAccountIdIn(userAccountIds: List<BigInteger>): Flux<ProviderExternalUserDetail>
  fun findByProviderExternalUserIdIn(providerExternalUserIds: List<BigInteger>): Flux<ProviderExternalUserDetail>

  fun findByUserDetailValueAndUserDetailType(
    userDetailValue: String,
    userDetailType: UserDetailType
  ): Flux<ProviderExternalUserDetail>

  @Query(value = "SELECT details.* FROM provider_external_user_detail as details JOIN provider_external_user as u " +
          "ON details.provider_external_user_id = u.id " +
          "WHERE details.user_detail_value = :userDetailValue " +
          "AND details.user_detail_type = :userDetailType " +
          "AND u.provider_external_id = :providerExternalId " +
          "AND u.provider_msa_id = :providerMsaId",)
  fun findOneProviderExternalUserDetailByUserDetailValueAndUserDetailTypeAndProviderExternalIdAndProviderMsaId(
    userDetailValue: String,
    userDetailType: UserDetailType,
    providerExternalId: String,
    providerMsaId: BigInteger
  ): Mono<ProviderExternalUserDetail?>

  @Query(value = "SELECT details.* FROM provider_external_user_detail as details JOIN provider_external_user as u " +
      "ON details.provider_external_user_id = u.id " +
      "and u.provider_msa_id = :providerMsaId AND u.provider_external_id = :providerExternalUserId",)
  fun findByProviderMsaIdAndExternalUserId(providerMsaId: BigInteger, providerExternalUserId: String): Flux<ProviderExternalUserDetail>

  @Query(value = "SELECT details.* FROM provider_external_user_detail as details JOIN provider_external_user as u " +
          "ON details.provider_external_user_id = u.id " +
          "INNER JOIN user_key_data as keyData ON u.user_key_data_id=keyData.id " +
          "WHERE u.provider_msa_id= :providerMsaId " +
          "AND keyData.encrypted_private_key_type = :keyPairType " +
          "AND lower(keyData.public_key_hex) = lower(:publicKeyHex) " +
          "AND keyData.key_usage_type = :keyUsageType " +
          "ORDER BY details.user_detail_priority ASC",)
  fun findByProviderMsaIdAndKeyPairTypeAndPublicKeyHexAndKeyUsageType(providerMsaId: BigInteger, keyPairType: KeyPairType, publicKeyHex: String, keyUsageType: KeyUsageType): Flux<ProviderExternalUserDetail>
}

interface ReactiveProviderExternalUserDetailDao {
  fun findUserAccountIdsByUserDetailsIn(userDetails: List<UserDetail>): Flux<BigInteger>
}

class ReactiveProviderExternalUserDetailDaoImpl @Autowired constructor(private val databaseClient: DatabaseClient,
  private val mappingR2dbcConverter: MappingR2dbcConverter) : ReactiveProviderExternalUserDetailDao {

  override fun findUserAccountIdsByUserDetailsIn(userDetails: List<UserDetail>): Flux<BigInteger> {
    val sql = "SELECT DISTINCT (provider_external_user_detail.user_account_id) as user_account_id " +
        "FROM provider_external_user_detail " +
        "WHERE (provider_external_user_detail.user_detail_value, provider_external_user_detail.user_detail_type) IN (:providerExternalUserDetails)"
    val details = userDetails.map { arrayOf(it.value.lowercase(Locale.US), it.type.name) }
    return databaseClient
      .sql(sql)
      .bind("providerExternalUserDetails", details)
      .map { row, metadata -> mappingR2dbcConverter.read(BigInteger::class.java, row, metadata) }
      .all()
  }
}