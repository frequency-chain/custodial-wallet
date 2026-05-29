package io.amplica.custodial_wallet.controller.util

import io.amplica.custodial_wallet.client.redis.CustodialWalletRedisClient
import io.amplica.custodial_wallet.client.redis.dto.SessionInfo
import io.amplica.custodial_wallet.client.redis.dto.WebsiteSession
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.orchestration.CustodialWalletOrchestrationServiceTest
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.mockito.kotlin.*
import java.net.URI

class RetryHelperTest {
  private val incorrectTokenRetryLimit: Int = 5
  private val resendLimit: Int = 5
  private val expirationInMillis: Long = 90000
  val authorizedMsaId = 1.toBigInteger()
  private val authorizedReturnHost = "google.com"
  private val authorizedReturnURL: URI = URI.create("https://$authorizedReturnHost")
  private lateinit var redisClient: CustodialWalletRedisClient
  private lateinit var retryHelper: RetryHelper
  @BeforeEach
  fun setUp() {
    redisClient = mock()
    retryHelper = RetryHelper(redisClient, 1)
  }

  @Test
  fun checkIncorrectTokenRetriesViolationNoIssue(): Unit = runBlocking {
    //GIVEN
    val sessionId = "someSessionId"
    val sessionInfo = SessionInfo(true, null, System.currentTimeMillis(), 0, 0)
    val expectedSessionInfo = SessionInfo.updateIncorrectTokenRetries(sessionInfo, 1)
    val sessionInfoArgumentCaptor = argumentCaptor<SessionInfo>()
    whenever(redisClient.saveSessionInfoBySessionId(eq(sessionId), sessionInfoArgumentCaptor.capture())).thenReturn(Unit)

    //WHEN
    retryHelper.checkIncorrectTokenRetriesViolation(sessionId, sessionInfo)

    //THEN
    val updatedSessionInfo = sessionInfoArgumentCaptor.firstValue
    Assertions.assertThat(updatedSessionInfo).isNotNull
    Assertions.assertThat(updatedSessionInfo).isEqualTo(expectedSessionInfo)
  }

  @Test
  fun checkIncorrectTokenRetriesViolationLimitExceeded(): Unit = runBlocking {
    //GIVEN
    val sessionId = "someSessionId"
    val sessionInfo = SessionInfo(true, null, System.currentTimeMillis(), 0, incorrectTokenRetryLimit + 1)

    //WHEN THEN
    Assertions.assertThatThrownBy{
      runBlocking {
        retryHelper.checkIncorrectTokenRetriesViolation(sessionId, sessionInfo)
      }
    }.isInstanceOf(ApiException::class.java)
      .hasFieldOrPropertyWithValue("apiError", ApiError.INCORRECT_TOKEN_RETRY_LIMIT_EXCEEDED)
  }

  @Test
  fun checkIncorrectTokenRetriesViolationWebsite():Unit = runBlocking {
    //GIVEN
    val sessionId = "someSessionId"
    val websiteSession = WebsiteSession(sessionId, null, sessionId = sessionId)
    val websiteSessionArgumentCaptor = argumentCaptor<WebsiteSession>()
    whenever(redisClient.findWebsiteSessionBySessionId(sessionId)).thenReturn(websiteSession)
    whenever(redisClient.saveWebsiteSession(websiteSessionArgumentCaptor.capture())).thenReturn(sessionId)

    //WHEN
    retryHelper.checkIncorrectTokenRetriesViolation(websiteSession)

    //THEN
    val updatedWebsiteSession = websiteSessionArgumentCaptor.firstValue
    Assertions.assertThat(updatedWebsiteSession).isNotNull
    Assertions.assertThat(updatedWebsiteSession.incorrectTokenRetries).isEqualTo(1)
  }

  @Test
  fun testCheckIncorrectTokenRetriesViolationWebsiteLimitExceeded() {
    //GIVEN
    val sessionId = "someSessionId"
    val websiteSession = WebsiteSession(sessionId, null, sessionId = sessionId, incorrectTokenRetries = incorrectTokenRetryLimit + 1)

    //WHEN THEN
    Assertions.assertThatThrownBy {
      runBlocking {
        retryHelper.checkIncorrectTokenRetriesViolation(websiteSession)
      }
    }.isInstanceOf(ApiException::class.java)
      .hasFieldOrPropertyWithValue("apiError", ApiError.INCORRECT_TOKEN_RETRY_LIMIT_EXCEEDED)
  }

  @Test
  fun validResendEmailOrSmsCode(): Unit = runBlocking {
    val sampleSessionInfo = SessionInfo(true, authorizedReturnURL.toString(), System.currentTimeMillis() - 120000L, 1, 0)
    val tempSessionId = "tempSessionId"
    whenever(redisClient.findSessionInfoBySessionId(tempSessionId)).thenReturn(sampleSessionInfo)
    val retVal = retryHelper.validateResendEmailOrCode(tempSessionId, expirationInMillis, resendLimit)
    Assertions.assertThat(retVal).isTrue
  }

  @Test
  fun invalidResendIfResendLimitExceeded(): Unit = runBlocking {
    val sampleSessionInfo = SessionInfo(true, authorizedReturnURL.toString(), System.currentTimeMillis() - 120000L, 21, 0)
    val tempSessionId = "tempSessionId"
    whenever(redisClient.findSessionInfoBySessionId(tempSessionId)).thenReturn(sampleSessionInfo)
    Assertions.assertThatThrownBy {
      runBlocking { retryHelper.validateResendEmailOrCode(tempSessionId, expirationInMillis, resendLimit) }
    }.isInstanceOf(ApiException::class.java).hasMessage("Email or code resend request sent exceeds configured limit")
  }

  @Test
  fun invalidResendIfResendTimerNotExpired(): Unit = runBlocking {
    val sampleSessionInfo = SessionInfo(true, authorizedReturnURL.toString(), System.currentTimeMillis() - 6000L, 1, 0)
    val tempSessionId = "tempSessionId"
    whenever(redisClient.findSessionInfoBySessionId(tempSessionId)).thenReturn(sampleSessionInfo)
    Assertions.assertThatThrownBy {
      runBlocking { retryHelper.validateResendEmailOrCode(tempSessionId, expirationInMillis, resendLimit) }
    }.isInstanceOf(ApiException::class.java)
      .hasMessage("Email or code resend request sent before configured time allotted")
  }
}