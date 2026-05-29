package io.amplica.custodial_wallet.orchestration.siwa

import io.amplica.custodial_wallet.client.redis.dto.SiwaIdentifierAndCaptchaToken
import io.amplica.custodial_wallet.client.redis.dto.SiwaRequest
import io.amplica.custodial_wallet.client.redis.dto.SiwaSession
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifier
import io.amplica.custodial_wallet.dto.*
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import org.springframework.util.MultiValueMap
import java.math.BigInteger
import java.util.*

class TimingSiwaOrchestrationService(private val delegate: SiwaOrchestrationService) : SiwaOrchestrationService {
  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(TimingSiwaOrchestrationService::class.java)
  }

  private suspend fun <T> time(name: String, block: suspend () -> T): T {
    return io.amplica.custodial_wallet.util.time(LOG, Level.INFO, name, block)
  }

  override suspend fun saveSiwaRequest(request: SiwaRequest, sessionId: String?): String {
    return time("startSiwaRequest") {
      delegate.saveSiwaRequest(request, sessionId)
    }
  }

  override suspend fun acceptSiwaRequest(
    request: SiwaRequest,
    queryParams: MultiValueMap<String, String>?,
    sessionId: String?
  ): ViewResponse<SiwaProps> {
    return time("acceptSiwaRequest") {
      delegate.acceptSiwaRequest(request, queryParams, sessionId)
    }
  }

  override suspend fun acceptSavedSiwaRequestBySessionId(sessionId: String): ViewResponse<SiwaProps> {
    return time("acceptSavedSiwaRequestBySessionId") {
      delegate.acceptSavedSiwaRequestBySessionId(sessionId)
    }
  }

  override suspend fun acceptUserIdentifier(
    sessionId: String,
    siwaIdentifierAndCaptchaToken: SiwaIdentifierAndCaptchaToken,
    userIp: String?,
    overrideBlockingSecret: String?,
    locale: Locale,
    xCaptchaHeaderValue: String?
  ): SiwaResponse<SiwaProps> {
    return time("acceptUserIdentifier") {
      delegate.acceptUserIdentifier(
        sessionId,
        siwaIdentifierAndCaptchaToken,
        userIp,
        overrideBlockingSecret,
        locale,
        xCaptchaHeaderValue
      )
    }
  }

  override suspend fun acceptAuthenticationCode(
    authenticationCode: String?,
    sessionId: String,
  ): SiwaResponse<SiwaProps> {
    return time("acceptAuthenticationCode") {
      delegate.acceptAuthenticationCode(authenticationCode, sessionId)
    }
  }

  override suspend fun acceptAcceptanceAndData(
    sessionId: String,
    userPayloadsAcceptanceAndData: UserPayloadsAcceptanceAndDataCommand
  ): SiwaResponse<SiwaProps> {
    return time("acceptAcceptanceAndData") {
      delegate.acceptAcceptanceAndData(sessionId, userPayloadsAcceptanceAndData)
    }
  }

  override suspend fun retrieveSiwaPayload(authorizationCode: String): SiwaPayloadResponse {
    return time("retrieveSiwaPayload") {
      delegate.retrieveSiwaPayload(authorizationCode)
    }
  }

  override suspend fun getAsyncSubmission(submissionId: String): AsyncSubmissionResponse {
    return time("getAsyncSubmission") {
      delegate.getAsyncSubmission(submissionId)
    }
  }

  override suspend fun getTokenForSiwaSessionId(siwaSessionId: String): TokenResponse {
    return time("getTokenForSiwaSessionId") {
      delegate.getTokenForSiwaSessionId(siwaSessionId)
    }
  }

  override suspend fun getSiwaErrorStartPage(siwaSession: SiwaSession, apiError: ApiError): ViewResponse<StartProps> {
    return time("getSiwaErrorStartPage") {
      delegate.getSiwaErrorStartPage(siwaSession, apiError)
    }
  }

  override suspend fun createNewUserAccountAndKeyPairs(
    userIdentifier: UserIdentifier,
    providerMsaId: BigInteger,
    shouldGenerateGraphKey: Boolean,
    userKeyPairType: KeyPairType
  ): BigInteger {
    return time("createNewUserAccountAndKeyPairs") {
      delegate.createNewUserAccountAndKeyPairs(userIdentifier, providerMsaId, shouldGenerateGraphKey, userKeyPairType)
    }
  }
}
