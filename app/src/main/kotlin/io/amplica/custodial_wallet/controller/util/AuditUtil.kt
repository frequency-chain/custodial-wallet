package io.amplica.custodial_wallet.controller.util

import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.db.repository.AuditSessionRecord
import io.amplica.custodial_wallet.db.repository.FinalizedState
import io.amplica.custodial_wallet.db.repository.Flow
import io.amplica.custodial_wallet.db.repository.State
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.function.Supplier

class DeferredSupplier<T>(var value: T? = null) : Supplier<T?> {
  override fun get(): T? {
    return value
  }
}

/**
 * AuditUtil is used to add audit_session_record tuples in a table for troubleshooting
 *
 * @property enabled, if we should write to the DB or just log what we can
 * @constructor Create empty Audit util
 */
class AuditUtil(private val enabled: Boolean) {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(AuditUtil::class.java)

    fun logNoSessionMessage(flow: Flow?, state: State, finalizedState: FinalizedState) {
      LOG.warn("No sessionId found for action with flow: $flow, state: $state, and finalizedState: $finalizedState")
    }

    fun logErrorSavingAuditMessage(flow: Flow?, state: State, finalizedState: FinalizedState, x: Exception) {
      LOG.error("Error saving audit log for action with flow: $flow, state: $state, and finalizedState: $finalizedState", x)
    }
  }

  /**
   * Around create is used when we should create a new AuditSessionRecord in a given flow
   *
   * @param T
   * @param custodialWalletDbService
   * @param sessionIdPromise
   * @param flow
   * @param state
   * @param finalizedState
   * @param endpointToAudit
   * @receiver
   * @return
   */
  suspend fun<T> aroundCreate(custodialWalletDbService: CustodialWalletDatabaseService, sessionIdPromise: Supplier<String?>, flow: Flow, state: State, finalizedState: FinalizedState, endpointToAudit: suspend () -> T): T {
    var actualFinalizedState = finalizedState
    var actualStackTrace: String? = null
    try {
      return endpointToAudit()
    } catch (x: Exception) {
      actualFinalizedState = FinalizedState.FAILED
      actualStackTrace = x.stackTraceToString()
      throw x
    } finally {
      try {
        val sessionId = sessionIdPromise.get()
        if (sessionId != null) {
          val auditSessionRecord =
            AuditSessionRecord.create(sessionId, flow, state, actualFinalizedState, actualStackTrace)
          LOG.info("Attempting to create AuditSessionRecord {} enabled=${enabled}", auditSessionRecord)
          if(enabled) {
            custodialWalletDbService.createAuditSessionRecord(auditSessionRecord)
          }
        } else {
          logNoSessionMessage(flow, state, finalizedState)
        }
      } catch (x: Exception) {
        logErrorSavingAuditMessage(flow, state, finalizedState, x)
      }
    }
  }

  /**
   * Around update is used when we want to update a given AuditSessionRecord associated with a given sessionId
   *
   * @param T
   * @param custodialWalletDbService
   * @param sessionIdPromise
   * @param state
   * @param finalizedState
   * @param endpointToAudit
   * @receiver
   * @return
   */
  suspend fun<T> aroundUpdate(custodialWalletDbService: CustodialWalletDatabaseService, sessionIdPromise: Supplier<String?>, state: State, finalizedState: FinalizedState, endpointToAudit: suspend () -> T): T {
    var actualFinalizedState = finalizedState
    var actualStackTrace: String? = null
    try {
      return endpointToAudit()
    } catch (x: Exception) {
      actualFinalizedState = FinalizedState.FAILED
      actualStackTrace = x.stackTraceToString()
      throw x
    } finally {
      try {
        val sessionId = sessionIdPromise.get()
        if (sessionId != null) {
          try {
            if(enabled) {
              val auditSessionRecord = custodialWalletDbService.findAuditSessionRecordBySessionId(sessionId)
              val newAuditSessionRecord =
                AuditSessionRecord.update(
                  auditSessionRecord,
                  auditSessionRecord.flow,
                  state,
                  actualFinalizedState,
                  actualStackTrace
                )
              custodialWalletDbService.updateAuditSessionRecord(newAuditSessionRecord)
            }
          } catch(x: Exception) {
            LOG.warn("Failed to update audit log with sessionId: $sessionId", x)
          }
        } else {
          logNoSessionMessage(null, state, finalizedState)
        }
      } catch (x: Exception) {
        logErrorSavingAuditMessage(null, state, finalizedState, x)
      }
    }
  }

  /**
   * Around create is used when we should create a new AuditSessionRecord in a given flow
   *
   * @param T
   * @param custodialWalletDbService
   * @param sessionIdPromise
   * @param flow
   * @param state
   * @param finalizedState
   * @param endpointToAudit
   * @receiver
   * @return
   */
  suspend fun<T> aroundUpsert(custodialWalletDbService: CustodialWalletDatabaseService, sessionIdPromise: Supplier<String?>, flow: Flow, state: State, finalizedState: FinalizedState, endpointToAudit: suspend () -> T): T {
    var actualFinalizedState = finalizedState
    var actualStackTrace: String? = null
    try {
      return endpointToAudit()
    } catch (x: Exception) {
      actualFinalizedState = FinalizedState.FAILED
      actualStackTrace = x.stackTraceToString()
      throw x
    } finally {
      try {
        val sessionId = sessionIdPromise.get()
        if (sessionId != null) {
          if(enabled) {
            try {
              val existingAuditSessionRecord = custodialWalletDbService.findAuditSessionRecordBySessionId(sessionId)
              val newAuditSessionRecord =
                AuditSessionRecord.update(
                  existingAuditSessionRecord,
                  existingAuditSessionRecord.flow,
                  state,
                  actualFinalizedState,
                  actualStackTrace
                )
              custodialWalletDbService.updateAuditSessionRecord(newAuditSessionRecord)
            } catch(_: Exception) {
              val auditSessionRecord =
                AuditSessionRecord.create(sessionId, flow, state, actualFinalizedState, actualStackTrace)
              custodialWalletDbService.createAuditSessionRecord(auditSessionRecord)
            }
          }
        } else {
          logNoSessionMessage(flow, state, finalizedState)
        }
      } catch (x: Exception) {
        logErrorSavingAuditMessage(flow, state, finalizedState, x)
      }
    }
  }
}