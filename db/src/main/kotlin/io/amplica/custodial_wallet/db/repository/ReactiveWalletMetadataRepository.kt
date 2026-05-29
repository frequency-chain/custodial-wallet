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

@Table(WalletMetadata.TABLE_NAME)
data class WalletMetadata(
  @Id
  val id: BigInteger? = null,

  @Column(WALLET_ID_NAME)
  var walletId: BigInteger? = null,

  @Column(CREDENTIAL_ID_NAME)
  var credentialId: BigInteger? = null,

  // The compressed credential (P256) public key signed by the wallet account private key (SR25519)
  @Column(SIGNATURE_OF_CREDENTIAL_COLUMN_NAME)
  val signatureOfCredentialPublicKeyBase64Url: String,

  // The wallet account public key (SR25519) signed by the credential (P256) private key
  @Column(CREDENTIAL_SIGNATURE_OF_ACCOUNT_COLUMN_NAME)
  val credentialSignatureOfAccountPublicKeyBase64Url: String?,

  @Column(CREATED_AT_COLUMN_NAME)
  val createdAt: Long,

  @Column(LAST_MODIFIED_COLUMN_NAME)
  val lastModified: Long,

  @Version
  var version: Long?

) {

  companion object {
    const val TABLE_NAME = "wallet_metadata"
    const val WALLET_ID_NAME = "wallet_id"
    const val CREDENTIAL_ID_NAME = "credential_id"
    const val SIGNATURE_OF_CREDENTIAL_COLUMN_NAME = "signature_of_credential_base64_url"
    const val CREDENTIAL_SIGNATURE_OF_ACCOUNT_COLUMN_NAME = "credential_signature_of_account_base64_url"
    const val CREATED_AT_COLUMN_NAME = "created_at"
    const val LAST_MODIFIED_COLUMN_NAME = "last_modified"

    fun create(
      signatureOfCredentialPublicKeyBase64Url: String,
      credentialSignatureOfAccountPublicKeyBase64Url: String? = null
    ): WalletMetadata {
      val now = Instant.now().toEpochMilli()
      return WalletMetadata(
        null,
        null,
        null,
        signatureOfCredentialPublicKeyBase64Url,
        credentialSignatureOfAccountPublicKeyBase64Url,
        now,
        now,
        null
      )
    }
  }
}

interface ReactiveWalletMetadataRepository : ReactiveCrudRepository<WalletMetadata, BigInteger> {
  fun findAllByWalletId(walletId: BigInteger): Flux<WalletMetadata>
  fun findOneByCredentialId(credentialId: BigInteger): Mono<WalletMetadata>
}
