package io.amplica.custodial_wallet.controller.util

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.db.repository.AuditSessionRecord
import io.amplica.custodial_wallet.db.repository.FinalizedState
import io.amplica.custodial_wallet.db.repository.Flow
import io.amplica.custodial_wallet.db.repository.State
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.*

class AuditUtilTest {
  private lateinit var mockDatabaseService: CustodialWalletDatabaseService
  private lateinit var audit: AuditUtil

  private lateinit var auditSessionRecordCreate: AuditSessionRecord
  private lateinit var auditSessionRecordUpdate: AuditSessionRecord
  private lateinit var auditSessionRecordError: AuditSessionRecord

  private val sessionId = "sessionId"
  private val flow = Flow.ONBOARD
  private val state1 = State.REQUEST_RECEIVED
  private val state2 = State.EMAIL_SENT
  private val finalizedState = FinalizedState.INCOMPLETE
  private val finalizedStateError = FinalizedState.FAILED

  @Nested
  @DisplayName("Enabled Tests")
  inner class EnabledTests{
    @BeforeEach
    fun setUp() {
      mockDatabaseService = mock()
      audit = AuditUtil(true)

      auditSessionRecordCreate = AuditSessionRecord.create(sessionId, flow, state1, finalizedState, null)
      auditSessionRecordUpdate = AuditSessionRecord(0.toBigInteger(), sessionId, flow, state2, finalizedState, null, auditSessionRecordCreate.createdAt, auditSessionRecordCreate.lastModified, 1.toBigInteger())
      auditSessionRecordError = AuditSessionRecord.update(auditSessionRecordUpdate, flow, state2, finalizedStateError, "error")
    }

    @Test
    fun aroundAuditCreateRecord(): Unit = runBlocking {
      whenever(mockDatabaseService.createAuditSessionRecord(any())).thenReturn(auditSessionRecordCreate)
      val response = "response"
      val responseReceived = audit.aroundCreate(
        mockDatabaseService,
        { sessionId },
        auditSessionRecordCreate.flow,
        auditSessionRecordCreate.state,
        auditSessionRecordCreate.finalizedState
      ) {
        response
      }

      // Verify Response is passed through the audit correctly
      Assertions.assertThat(response).isEqualTo(responseReceived)
      // Verify the database create was called with the correct information
      verify(mockDatabaseService, times(1)).createAuditSessionRecord(argThat { record -> record.sessionId == auditSessionRecordCreate.sessionId })
      verify(mockDatabaseService, times(1)).createAuditSessionRecord(argThat { record -> record.flow == auditSessionRecordCreate.flow })
      verify(mockDatabaseService, times(1)).createAuditSessionRecord(argThat { record -> record.state == auditSessionRecordCreate.state })
      verify(mockDatabaseService, times(1)).createAuditSessionRecord(argThat { record -> record.finalizedState == auditSessionRecordCreate.finalizedState })
    }

    @Test
    fun aroundAuditUpdateRecord(): Unit = runBlocking {
      whenever(mockDatabaseService.updateAuditSessionRecord(any())).thenReturn(auditSessionRecordUpdate)
      whenever(mockDatabaseService.findAuditSessionRecordBySessionId(any())).thenReturn(auditSessionRecordUpdate)
      val response = "response"
      val responseReceived = audit.aroundUpdate(
        mockDatabaseService,
        { sessionId },
        auditSessionRecordUpdate.state,
        auditSessionRecordUpdate.finalizedState
      ) {
        response
      }

      Assertions.assertThat(response).isEqualTo(responseReceived)
      verify(mockDatabaseService, times(1)).updateAuditSessionRecord(argThat { record -> record.sessionId == auditSessionRecordUpdate.sessionId })
      verify(mockDatabaseService, times(1)).updateAuditSessionRecord(argThat { record -> record.flow == auditSessionRecordUpdate.flow })
      verify(mockDatabaseService, times(1)).updateAuditSessionRecord(argThat { record -> record.state == auditSessionRecordUpdate.state })
      verify(mockDatabaseService, times(1)).updateAuditSessionRecord(argThat { record -> record.finalizedState == auditSessionRecordUpdate.finalizedState })
    }

    @Test
    fun aroundAuditDelayedSessionId(): Unit = runBlocking {
      whenever(mockDatabaseService.createAuditSessionRecord(any())).thenReturn(auditSessionRecordCreate)
      val response = "response"
      val deferredSessionId = DeferredSupplier<String>()
      val responseReceived = audit.aroundCreate(
        mockDatabaseService,
        deferredSessionId,
        auditSessionRecordCreate.flow,
        auditSessionRecordCreate.state,
        auditSessionRecordCreate.finalizedState
      ) {
        deferredSessionId.value = sessionId
        response
      }

      Assertions.assertThat(response).isEqualTo(responseReceived)
      verify(mockDatabaseService, times(1)).createAuditSessionRecord(argThat { record -> record.sessionId == auditSessionRecordCreate.sessionId })
      verify(mockDatabaseService, times(1)).createAuditSessionRecord(argThat { record -> record.flow == auditSessionRecordCreate.flow })
      verify(mockDatabaseService, times(1)).createAuditSessionRecord(argThat { record -> record.state == auditSessionRecordCreate.state })
      verify(mockDatabaseService, times(1)).createAuditSessionRecord(argThat { record -> record.finalizedState == auditSessionRecordCreate.finalizedState })
    }

    @Test
    fun aroundAuditEndpointError(): Unit = runBlocking {
      val exceptionMessage = "Bad thing happened"
      val result = runCatching {
        whenever(mockDatabaseService.updateAuditSessionRecord(any())).thenReturn(auditSessionRecordError)
        whenever(mockDatabaseService.findAuditSessionRecordBySessionId(any())).thenReturn(auditSessionRecordUpdate)
        audit.aroundUpdate(
          mockDatabaseService,
          { sessionId },
          auditSessionRecordUpdate.state,
          auditSessionRecordUpdate.finalizedState
        ) {
          throw Exception(exceptionMessage)
        }

      }.onFailure {
        Assertions.assertThat(it)
          .isInstanceOf(Exception::class.java)
          .hasFieldOrPropertyWithValue("message", exceptionMessage)
      }

      Assertions.assertThat(result.isFailure).isTrue
      verify(mockDatabaseService, times(1)).updateAuditSessionRecord(argThat { record -> record.sessionId == auditSessionRecordError.sessionId })
      verify(mockDatabaseService, times(1)).updateAuditSessionRecord(argThat { record -> record.flow == auditSessionRecordError.flow })
      verify(mockDatabaseService, times(1)).updateAuditSessionRecord(argThat { record -> record.state == auditSessionRecordError.state })
      verify(mockDatabaseService, times(1)).updateAuditSessionRecord(argThat { record -> record.finalizedState == auditSessionRecordError.finalizedState })
    }
  }

  @Nested
  @DisplayName("Disabled Tests")
  inner class DisabledTests{
    @BeforeEach
    fun setUp() {
      mockDatabaseService = mock()
      audit = AuditUtil(false)

      auditSessionRecordCreate = AuditSessionRecord.create(sessionId, flow, state1, finalizedState, null)
      auditSessionRecordUpdate = AuditSessionRecord(0.toBigInteger(), sessionId, flow, state2, finalizedState, null, auditSessionRecordCreate.createdAt, auditSessionRecordCreate.lastModified, 1.toBigInteger())
      auditSessionRecordError = AuditSessionRecord.update(auditSessionRecordUpdate, flow, state2, finalizedStateError, "error")
    }

    @Test
    fun aroundAuditCreateRecord(): Unit = runBlocking {
      val response = "response"
      val responseReceived = audit.aroundCreate(
        mockDatabaseService,
        { sessionId },
        auditSessionRecordCreate.flow,
        auditSessionRecordCreate.state,
        auditSessionRecordCreate.finalizedState
      ) {
        response
      }

      Mockito.verifyNoInteractions(mockDatabaseService)
      // Verify Response is passed through the audit correctly
      Assertions.assertThat(response).isEqualTo(responseReceived)
    }

    @Test
    fun aroundAuditUpdateRecord(): Unit = runBlocking {
      val response = "response"
      val responseReceived = audit.aroundUpdate(
        mockDatabaseService,
        { sessionId },
        auditSessionRecordUpdate.state,
        auditSessionRecordUpdate.finalizedState
      ) {
        response
      }

      Mockito.verifyNoInteractions(mockDatabaseService)
      Assertions.assertThat(response).isEqualTo(responseReceived)
    }

    @Test
    fun aroundAuditDelayedSessionId(): Unit = runBlocking {
      val response = "response"
      val deferredSessionId = DeferredSupplier<String>()
      val responseReceived = audit.aroundCreate(
        mockDatabaseService,
        deferredSessionId,
        auditSessionRecordCreate.flow,
        auditSessionRecordCreate.state,
        auditSessionRecordCreate.finalizedState
      ) {
        deferredSessionId.value = sessionId
        response
      }

      Mockito.verifyNoInteractions(mockDatabaseService)
      Assertions.assertThat(response).isEqualTo(responseReceived)
    }

    @Test
    fun aroundAuditEndpointError(): Unit = runBlocking {
      val exceptionMessage = "Bad thing happened"
      val result = runCatching {
        audit.aroundUpdate(
          mockDatabaseService,
          { sessionId },
          auditSessionRecordUpdate.state,
          auditSessionRecordUpdate.finalizedState
        ) {
          throw Exception(exceptionMessage)
        }

      }.onFailure {
        Assertions.assertThat(it)
          .isInstanceOf(Exception::class.java)
          .hasFieldOrPropertyWithValue("message", exceptionMessage)
      }

      Mockito.verifyNoInteractions(mockDatabaseService)
      Assertions.assertThat(result.isFailure).isTrue
    }
  }
}
