package io.amplica.custodial_wallet.orchestration.siwa

import io.amplica.custodial_wallet.client.redis.dto.SiwaIdentifierAndCaptchaToken
import io.amplica.custodial_wallet.client.redis.dto.SiwaRequest
import io.amplica.custodial_wallet.client.redis.dto.SiwaSession
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifier
import io.amplica.custodial_wallet.dto.*
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import org.springframework.util.MultiValueMap
import java.math.BigInteger
import java.net.URI
import java.util.*

sealed interface SiwaResponse<out T>

data class ViewResponse<T>(
  val template: String,
  val model: T,
  val sessionId: String?,
  val matomo: MatomoData? = null,
) : SiwaResponse<T>

data class CallbackResponse(
  val callbackUrl: String,
  val sessionId: String,
  val authorizationCode: String
) : SiwaResponse<Nothing>

data class RedirectResponse(
  val location: URI,
  val sessionId: String,
) : SiwaResponse<Nothing>

data class TokenResponse(val token: String)

interface SiwaOrchestrationService {
  /**
   * Saves out the request to be "claim checked" lor later
   */
  suspend fun saveSiwaRequest(request: SiwaRequest, sessionId: String?): String

  /**
   * Accepts initial request from provider and initiates the SiwA process.
   */
  suspend fun acceptSiwaRequest(
    request: SiwaRequest,
    queryParams: MultiValueMap<String, String>?,
    sessionId: String?
  ): ViewResponse<SiwaProps>

  /**
   * Accepts session ID from provider to continue a SiwA process where the request was previously sent to us and saved.
   */
  suspend fun acceptSavedSiwaRequestBySessionId(sessionId: String): ViewResponse<SiwaProps>

  /**
   * Accepts identifier supplied by user
   */
  suspend fun acceptUserIdentifier(
    sessionId: String,
    siwaIdentifierAndCaptchaToken: SiwaIdentifierAndCaptchaToken,
    userIp: String?,
    overrideBlockingSecret: String?,
    locale: Locale,
    xCaptchaHeaderValue: String?,
  ): SiwaResponse<SiwaProps>

  /**
   * User submits the authentication code we sent them (clicks link or enters code)
   */
  suspend fun acceptAuthenticationCode(
    authenticationCode: String?,
    sessionId: String,
  ): SiwaResponse<SiwaProps>

  /**
   * User acceptance of permissions and any associated data to persist
   *
   * @return redirectUrl e.g. "redirect:http://localhost:8080/"
   */
  suspend fun acceptAcceptanceAndData(
    sessionId: String,
    userPayloadsAcceptanceAndData: UserPayloadsAcceptanceAndDataCommand,
  ): SiwaResponse<SiwaProps>

  /**
   * Allows providers to retrieve the signed payloads
   */
  suspend fun retrieveSiwaPayload(authorizationCode: String): SiwaPayloadResponse

  suspend fun getAsyncSubmission(submissionId: String): AsyncSubmissionResponse

  suspend fun getTokenForSiwaSessionId(siwaSessionId: String): TokenResponse

  suspend fun getSiwaErrorStartPage(siwaSession: SiwaSession, apiError: ApiError): ViewResponse<StartProps>

  /**
   * This is a smell but shoudl ultimately be refactored in such away that other implementations can rely on it
   */
  suspend fun createNewUserAccountAndKeyPairs(
    userIdentifier: UserIdentifier,
    providerMsaId: BigInteger,
    shouldGenerateGraphKey: Boolean,
    userKeyPairType: KeyPairType,
  ): BigInteger
}
