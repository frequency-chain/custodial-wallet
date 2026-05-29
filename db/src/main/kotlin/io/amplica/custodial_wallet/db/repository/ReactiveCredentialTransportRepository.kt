package io.amplica.custodial_wallet.db.repository

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import java.math.BigInteger
import java.time.Instant


@Table(CredentialTransport.TABLE_NAME)
data class CredentialTransport(
  @Id
  @Column(ID_COLUMN_NAME)
  val id: BigInteger? = null,

  @Column(CREDENTIAL_ID_COLUMN_NAME)
  val credentialId: BigInteger,

  @Column(TRANSPORT_COLUMN_NAME)
  val transport: String,

  @Column(CREATED_AT_COLUMN_NAME)
  val createdAt: Long,

  @Column(LAST_MODIFIED_COLUMN_NAME)
  val lastModified: Long,

  @Version
  var version: Long?
) {

  companion object {
    const val TABLE_NAME = "credential_transport"

    const val ID_COLUMN_NAME = "id"
    const val CREDENTIAL_ID_COLUMN_NAME = "credential_id"
    const val TRANSPORT_COLUMN_NAME = "transport"
    const val CREATED_AT_COLUMN_NAME = "created_at"
    const val LAST_MODIFIED_COLUMN_NAME = "last_modified"

    fun create(
      credentialId: BigInteger,
      transport: String
    ): CredentialTransport {
      val now = Instant.now().toEpochMilli()
      return CredentialTransport(
        null,
        credentialId,
        transport,
        now,
        now,
        null
      )
    }
  }
}

interface ReactiveCredentialTransportRepository : ReactiveCrudRepository<CredentialTransport, BigInteger> {
  fun findAllByCredentialId(credentialId: BigInteger): Flux<CredentialTransport>
}
