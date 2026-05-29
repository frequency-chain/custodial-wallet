package io.amplica.custodial_wallet.controller.util

import io.amplica.custodial_wallet.client.redis.CustodialWalletRedisClient
import io.amplica.custodial_wallet.client.redis.dto.SessionInfo
import io.amplica.custodial_wallet.client.redis.dto.WebsiteSession
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException

class RetryHelper(private val redisClient: CustodialWalletRedisClient, private val incorrectTokenRetryLimit: Int) {
  suspend fun checkIncorrectTokenRetriesViolation(sessionId: String, session: SessionInfo){
    val updatedSession = SessionInfo.updateIncorrectTokenRetries(session, session.incorrectTokenRetries + 1)
    if(updatedSession.incorrectTokenRetries > incorrectTokenRetryLimit) {
      throw ApiException(ApiError.INCORRECT_TOKEN_RETRY_LIMIT_EXCEEDED, "SessionId=${sessionId} has exceeded the amount of incorrect token entries of=${incorrectTokenRetryLimit}")
    }
    redisClient.saveSessionInfoBySessionId(sessionId, updatedSession)
  }

  suspend fun checkIncorrectTokenRetriesViolation(session: WebsiteSession){
    session.incorrectTokenRetries++
    if(session.incorrectTokenRetries > incorrectTokenRetryLimit) {
      throw ApiException(ApiError.INCORRECT_TOKEN_RETRY_LIMIT_EXCEEDED, "SessionId=${session.sessionId} has exceeded the amount of incorrect token entries of=${incorrectTokenRetryLimit}")
    }
    redisClient.saveWebsiteSession(session)
  }

  suspend fun validateResendEmailOrCode(sessionId: String, expirationInMillis: Long, resendLimit: Int): Boolean {
    val sessionInfo = redisClient.findSessionInfoBySessionId(sessionId) ?: throw ApiException(ApiError.NO_SESSION_INFO_FOUND_ERROR, "No SessionInfo was found for sessionId=$sessionId")
    val updatedResendCount = sessionInfo.resendCount + 1
    val updatedResendTimeInMillis = System.currentTimeMillis()
    redisClient.saveSessionInfoBySessionId(sessionId, SessionInfo(sessionInfo.tosAgreement, sessionInfo.callbackUrl, updatedResendTimeInMillis, updatedResendCount, sessionInfo.incorrectTokenRetries))
    val secondsBetweenResend = updatedResendTimeInMillis - sessionInfo.resendTimeInMillis
    if(secondsBetweenResend < expirationInMillis) {
      throw ApiException(ApiError.RESEND_REQUEST_INVALID, "Email or code resend request sent before configured time allotted")
    }
    if(updatedResendCount > resendLimit) {
      throw ApiException(ApiError.RESEND_LIMIT_EXCEEDED, "Email or code resend request sent exceeds configured limit")
    }
    return true
  }
}