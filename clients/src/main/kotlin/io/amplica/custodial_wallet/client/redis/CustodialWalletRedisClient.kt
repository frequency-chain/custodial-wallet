package io.amplica.custodial_wallet.client.redis

import io.amplica.custodial_wallet.client.redis.dto.*
import java.math.BigInteger
import java.time.Duration

interface CustodialWalletRedisClient {
  val timeToLive: Duration

  suspend fun saveLoginRequest(loginRequest: LoginRequest): String
  suspend fun saveLoginRequestByToken(loginRequest: LoginRequest, token: String, sessionId: String? = null): String
  suspend fun findLoginRequestByToken(sessionId: String, token: String): LoginRequest?
  suspend fun findLoginRequestBySessionId(sessionId: String): LoginRequest?
  suspend fun replaceLoginRequestToken(sessionId: String, newToken: String)
  suspend fun deleteLoginRequestByToken(sessionId: String, token: String)

  suspend fun saveSignUpRequest(signUpRequest: SignUpRequest): String
  suspend fun saveSignUpRequestByToken(signUpRequest: SignUpRequest, token: String, sessionId: String? = null): String
  suspend fun findSignUpRequestByToken(sessionId: String, token: String): SignUpRequest?
  suspend fun findSignUpRequestBySessionId(sessionId: String): SignUpRequest?
  suspend fun replaceSignUpRequestToken(sessionId: String, newToken: String)
  suspend fun deleteSignUpRequestByToken(sessionId: String, token: String)

  suspend fun saveSessionInfoBySessionId(sessionId: String, sessionInfo: SessionInfo)
  suspend fun findSessionInfoBySessionId(sessionId: String): SessionInfo?

  suspend fun saveWebsiteSession(websiteSession: WebsiteSession): String
  suspend fun saveWebsiteSessionByAuthenticationCode(authenticationCode: String, websiteSession: WebsiteSession): String
  suspend fun saveWebsiteSessionByAuthorizationCode(authorizationCode: String, websiteSession: WebsiteSession): String
  suspend fun saveWebsiteSessionByVerificationCode(verificationCode: String, websiteSession: WebsiteSession): String
  suspend fun findWebsiteSessionBySessionId(sessionId: String): WebsiteSession?
  suspend fun findWebsiteSessionBySessionIdAndAuthenticationCode(sessionId: String, authenticationCode: String): WebsiteSession?
  suspend fun findWebsiteSessionBySessionIdAndAuthorizationCode(sessionId: String, authorizationCode: String): WebsiteSession?
  suspend fun findWebsiteSessionBySessionIdAndVerificationCode(sessionId: String, verificationCode: String): WebsiteSession?
  suspend fun getAndDeleteWebsiteSessionByAuthenticationCode(sessionId: String, authenticationCode: String): WebsiteSession?
  suspend fun deleteWebsiteSessionBySessionId(sessionId: String)

  suspend fun<T : Any> savePayloadToSign(payloadToSign: PayloadToSignRequest<T>): String
  suspend fun<T : Any> savePayloadToSignByAuthenticationCode(authenticationCode: String, payloadToSign: PayloadToSignRequest<T>): String
  suspend fun<T : Any> savePayloadToSignByAuthorizationCode(authorizationCode: String, payloadToSign: PayloadToSignRequest<T>): String
  suspend fun<T> findPayloadToSignBySessionId(sessionId: String): PayloadToSignRequest<T>?
  suspend fun<T> findPayloadToSignBySessionIdAndAuthenticationCode(sessionId: String, authenticationCode: String): PayloadToSignRequest<T>?
  suspend fun<T> findPayloadToSignBySessionIdAndAuthorizationCode(sessionId: String, authorizationCode: String): PayloadToSignRequest<T>?
  suspend fun deletePayloadToSignBySessionId(sessionId: String)

  suspend fun saveBatchPayloadToSignRequest(batchPayloadToSign: BatchPayloadToSignRequest): String
  suspend fun saveBatchPayloadToSignRequestByAuthenticationCode(authenticationCode: String, batchPayloadToSign: BatchPayloadToSignRequest): String
  suspend fun saveBatchPayloadToSignRequestByAuthorizationCode(authorizationCode: String, batchPayloadToSign: BatchPayloadToSignRequest): String
  suspend fun findBatchPayloadToSignRequestBySessionId(sessionId: String): BatchPayloadToSignRequest?
  suspend fun findBatchPayloadToSignRequestBySessionIdAndAuthenticationCode(sessionId: String, authenticationCode: String): BatchPayloadToSignRequest?
  suspend fun findBatchPayloadToSignRequestBySessionIdAndAuthorizationCode(sessionId: String, authorizationCode: String): BatchPayloadToSignRequest?
  suspend fun deleteBatchPayloadToSignRequestBySessionId(sessionId: String)

  suspend fun saveSiwaSession(siwaSession: SiwaSession)
  suspend fun findSiwaSessionBySessionId(sessionId: String): SiwaSession?
  suspend fun findSiwaSessionByAuthorizationCode(authorizationCode: String): SiwaSession?
  suspend fun findSiwaSessionIdByAuthorizationCode(authorizationCode: String): String?
  suspend fun saveSiwaSessionIdByAuthorizationCode(sessionId: String, authorizationCode: String): Boolean
  suspend fun deleteSiwaSessionBySessionId(sessionId: String)

  suspend fun saveSesTemplates(sesTemplates: Set<SesTemplate>)
  suspend fun findAllSesTemplates(): Set<SesTemplate>

  suspend fun saveProviderCount(providerCount: ProviderCount)
  suspend fun findProviderCount(key: String): ProviderCount?

  suspend fun saveMigrationTaskNoMsaCount(noMsaCount: Int)
  suspend fun findMigrationTaskNoMsaCount(): Int?
  suspend fun deleteMigrationTaskNoMsaCount()

  suspend fun saveUserActivityRecord(record: UserActivityRecord)
  suspend fun findUserActivityRecord(userAccountId: BigInteger): UserActivityRecord?

  suspend fun<RESULT> saveAsyncSubmission(asyncSubmission: AsyncSubmission<RESULT>): AsyncSubmission<RESULT>
  suspend fun<RESULT> findAsyncSubmission(id: String): AsyncSubmission<RESULT>?
}
