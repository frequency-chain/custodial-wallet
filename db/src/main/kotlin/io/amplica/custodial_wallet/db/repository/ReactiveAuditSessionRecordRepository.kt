package io.amplica.custodial_wallet.db.repository

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono
import java.math.BigInteger
import java.time.Instant

enum class Flow{
  ONBOARD,
  @Deprecated("This is to ensure we can still deserialize old records but use the more specific signup enums SIGN_UP_SMS SIGN_UP_EMAIL") SIGNUP,
  SIGN_UP_SMS,
  SIGN_UP_EMAIL,
  @Deprecated("This is to ensure we can still deserialize old records but use the more specific login enums LOGIN_SMS LOGIN_EMAIL") LOGIN,
  LOGIN_SMS,
  LOGIN_EMAIL,
  DIRECT_LOGIN,
  TOKEN_REPLACED,
  CHANGE_HANDLE,
  REVOKE_DELEGATION,
  ADD_IDENTIFIER,
  GET_FINALIZED_HEAD_NUMBER,
  LATEST_BLOCK_NUMBER,
  CHANGE_PASSWORD,
  PASSKEY_REGISTRATION,
  PASSKEY_TRANSACTION
}

enum class State{
  REQUEST_RECEIVED,
  TOS_ACCEPTED,
  EMAIL_SENT,
  SMS_SENT,
  SMS_CODE_REQUESTED,
  TOKEN_REPLACED,
  PAYLOAD_DELIVERED,
  URL_SENT,
  TOKEN_VALIDATED,
  PASSWORD_CHANGED,
  REGISTRATION_ACCEPTED,
  CREDENTIAL_RETRIEVED
}

enum class FinalizedState{
  INCOMPLETE, COMPLETED, FAILED
}

@Table("audit_session_record")
data class AuditSessionRecord(
  @Id
  var id: BigInteger? = null,
  @Column("session_id")
  val sessionId: String,
  @Column("flow")
  val flow: Flow,
  @Column("state")
  val state: State,
  @Column("finalized_state")
  val finalizedState: FinalizedState,
  @Column("stack_trace")
  val stackTrace: String?,
  @Column("created_at")
  val createdAt: BigInteger?,
  @Column("last_modified")
  var lastModified: BigInteger?,
  @Version
  val version: BigInteger?
) {
  constructor(
    id: BigInteger?,
    sessionId: String,
    flow: Flow,
    state: State,
    finalizedState: FinalizedState,
    stackTrace: String?,
    createdAt: BigInteger?,
    lastModified: BigInteger?
  ) :
      this(
        id,
        sessionId,
        flow,
        state,
        finalizedState,
        stackTrace,
        createdAt,
        lastModified,
        null
      )
  companion object {
    fun create(sessionId: String, flow: Flow, state: State, finalizedState: FinalizedState, stackTrace: String?): AuditSessionRecord {
      val createdAt = Instant.now().toEpochMilli().toBigInteger()
      return AuditSessionRecord(
        null,
        sessionId,
        flow,
        state,
        finalizedState,
        stackTrace,
        createdAt,
        createdAt,
        null
      )
    }

    fun update(oldAuditSessionRecord: AuditSessionRecord, flow: Flow, state: State, finalizedState: FinalizedState, stackTrace: String?): AuditSessionRecord {
      val lastModified = Instant.now().toEpochMilli().toBigInteger()
      return AuditSessionRecord(
        oldAuditSessionRecord.id!!,
        oldAuditSessionRecord.sessionId,
        flow,
        state,
        finalizedState,
        stackTrace,
        oldAuditSessionRecord.createdAt,
        lastModified,
        oldAuditSessionRecord.version
      )
    }
  }
}


interface ReactiveAuditSessionRecordRepository : ReactiveCrudRepository<AuditSessionRecord, BigInteger> {
  fun findOneBySessionId(sessionId: String): Mono<AuditSessionRecord>
}