package io.amplica.custodial_wallet.db.repository

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigInteger
import java.time.Instant
import java.util.*

@Table(Credential.TABLE_NAME)
data class Credential(
  @Id
  val id: BigInteger? = null,

  @Column(USER_ACCOUNT_ID_COLUMN_NAME)
  val userAccountId: BigInteger? = null,

  @Column(WALLET_ID_COLUMN_NAME)
  val walletId: BigInteger? = null,

  @Column(AUTHENTICATOR_UUID_COLUMN_NAME)
  val authenticatorUuid: UUID,

  @Column(CREDENTIAL_ID_COLUMN_NAME)
  val credentialIdBase64Url: String,

  // The credential public key serialized in the COSE format
  @Column(PUBLIC_KEY_COLUMN_NAME)
  val publicKeyCose: String,

  @Column(COMPRESSED_PUBLIC_KEY_COLUMN_NAME)
  val compressedPublicKeyBase64Url: String,

  // See 'signCount' property in the WebAuthN-3 spec
  @Column(SIGN_COUNT_COLUMN_NAME)
  val signCount: Long,

  // See 'Backup Eligibility' flag in the WebAuthN-3 spec
  @Column(BACKUP_ELIGIBLE_COLUMN_NAME)
  val backupEligible: Boolean,

  @Column(BACKED_UP_COLUMN_NAME)
  val backedUp: Boolean,

  @Column(CREATED_AT_COLUMN_NAME)
  val createdAt: Long,

  @Column(LAST_MODIFIED_COLUMN_NAME)
  val lastModified: Long,

  @Version
  var version: Long?,
) {

  @Transient
  var transports: Set<String> = emptySet()

  @Transient
  var walletMetadata: WalletMetadata? = null

  companion object {
    const val TABLE_NAME = "credential"

    const val USER_ACCOUNT_ID_COLUMN_NAME = "user_account_id"
    const val WALLET_ID_COLUMN_NAME = "wallet_id"
    const val AUTHENTICATOR_UUID_COLUMN_NAME = "authenticator_uuid"
    const val CREDENTIAL_ID_COLUMN_NAME = "credential_id_base64_url"
    const val PUBLIC_KEY_COLUMN_NAME = "public_key_cose"
    const val COMPRESSED_PUBLIC_KEY_COLUMN_NAME = "compressed_public_key_base64_url"
    const val SIGN_COUNT_COLUMN_NAME = "sign_count"
    const val BACKUP_ELIGIBLE_COLUMN_NAME = "backup_eligible"
    const val BACKED_UP_COLUMN_NAME = "backed_up"
    const val CREATED_AT_COLUMN_NAME = "created_at"
    const val LAST_MODIFIED_COLUMN_NAME = "last_modified"

    fun create(
      authenticatorUuid: UUID,
      credentialIdBase64Url: String,
      publicKeyBase64Url: String,
      compressedPublicKeyBase64Url: String,
      signCount: Long,
      backupEligible: Boolean,
      backedUp: Boolean,
      transports: Set<String>
    ): Credential {
      val now = Instant.now().toEpochMilli()
      return Credential(
        null,
        null,
        null,
        authenticatorUuid,
        credentialIdBase64Url,
        publicKeyBase64Url,
        compressedPublicKeyBase64Url,
        signCount,
        backupEligible,
        backedUp,
        now,
        now,
        null
      ).apply { this.transports = transports }
    }
  }
}

interface ReactiveCredentialRepository : ReactiveCrudRepository<Credential, BigInteger> {
  fun findByCredentialIdBase64Url(credentialIdBase64Url: String): Mono<Credential>
  fun findAllByWalletId(walletId: BigInteger): Flux<Credential>
  fun findByUserAccountId(userAccountId: BigInteger): Flux<Credential>
}
