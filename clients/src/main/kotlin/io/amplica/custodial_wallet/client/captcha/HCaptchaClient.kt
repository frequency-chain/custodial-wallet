package io.amplica.custodial_wallet.client.captcha

import com.fasterxml.jackson.annotation.JsonProperty
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import net.logstash.logback.argument.StructuredArguments.fields
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import kotlin.time.measureTime

data class HCaptchaSiteVerifyResponse(
  @JsonProperty("success") val success: Boolean, // whether this request was a valid hCaptcha token for your site
  //@JsonProperty("challenge_ts") val challengeTimestamp: String, // timestamp of the challenge load (ISO format yyyy-MM-dd'T'HH:mm:ssZZ)
  //@JsonProperty("hostname") val hostname: String, // the hostname of the site where the hCaptcha was solved
  @JsonProperty("score") val score: Float, // the risk score for this request (0.0 - 1.0)
  //@JsonProperty("score_reason") val scoreReason: List<String>, // reasons for this score
  @JsonProperty("sitekey") val siteKey: String?, // sitekey of the request
  //@JsonProperty("behavior_counts") val behaviorCounts: Map<String, Int>?, // obj of form: {'ip_device': 1, .. etc}
  //@JsonProperty("similarity") val similarity: Float?, // how similar is this? (0.0 - 1.0, -1 on err)
  //@JsonProperty("similarity_failures") val similarityFailures: Int?, // count of similar_tokens not processed
  //@JsonProperty("similarity_error_details") val similarityErrorDetails: List<String>?, // array of strings for any similarity errors
  //@JsonProperty("scoped_uid_0") val scopedUid0: String?, // encoded clientID
  //@JsonProperty("scoped_uid_1") val scopedUid1: String?, // encoded IP
  //@JsonProperty("scoped_uid_2") val scopedUid2: String?, // encoded IP (APT)
  //@JsonProperty("risk_insights") val riskInsights: Any?, // Risk Insights (APT + RI)
  //@JsonProperty("schemaIds") val sigs: Any?, // Advanced Threat Signatures (APT)
  //@JsonProperty("tags") val tags: List<String>?, // tags added via Rules
  @JsonProperty("error-codes") val errorCodes: List<String>?,
  )

data class HCaptchaHealthCheckResponse(val status: HCaptchaStatus)
data class HCaptchaStatus(val description: String, val indicator: String)

class HCaptchaClient(
  enabled: Boolean,
  siteKey: String,
  private val verifyClient: WebClient,
  private val statusClient: WebClient,
  private val secretKey: String,
  private val includeUserIP: Boolean,
  private val xCaptchaHeaderValueSecret: String,
  ) : CaptchaClient(enabled, siteKey) {

  companion object {
    private val LOG = LoggerFactory.getLogger(HCaptchaClient::class.java)
    const val VERIFY_CAPTCHA_ENDPOINT = "/siteverify"
    const val HEALTHCHECK_ENDPOINT = "/api/v2/status.json"
  }

  override suspend fun verifyCaptchaStatus(captchaToken: String?, userIp: String?, xCaptchaHeaderValue: String?) {
    if (enabled) {
      if (captchaToken == null) {
        throw ApiException(ApiError.CAPTCHA_REQUIREMENT_NOT_SATISFIED, "Captcha requirements were not met with token: $captchaToken")
      } else {
        val captchaStatus: CaptchaStatus
        val duration = measureTime {
          val ignoreScore = xCaptchaHeaderValueSecret == xCaptchaHeaderValue
          captchaStatus = verifyCaptcha(
            VerifyCaptchaRequest(captchaToken, userIp), ignoreScore,
          )
        }
        LOG.info("Time to call hCaptcha in millis=${duration.inWholeMilliseconds}")
        when (captchaStatus) {
          CaptchaStatus.BAD ->
            throw ApiException(ApiError.BAD_CAPTCHA_ATTEMPT, "There was an error with your captcha response token: $captchaToken")

          CaptchaStatus.RESEND ->
            throw ApiException(ApiError.RESEND_CAPTCHA_ATTEMPT, "There was an error with your captcha response token: $captchaToken")

          CaptchaStatus.GOOD -> {}
        }
      }
    }
  }

  suspend fun verifyCaptcha(verifyCaptchaRequest: VerifyCaptchaRequest, ignoreScore: Boolean): CaptchaStatus {
    if (!enabled) return CaptchaStatus.GOOD
    val captchaToken = verifyCaptchaRequest.responseToken
    if (captchaToken.isNullOrEmpty()) {
      throw ApiException(
        ApiError.CAPTCHA_REQUIREMENT_NOT_SATISFIED,
        "Captcha Token null after test response substitution for test values. Illegal state"
      )
    }

    // NOTE: If the userIP comes in null this will act the same as if we have disabled sending user IP.
    val userIP: String? = when (includeUserIP) {
      true -> verifyCaptchaRequest.userIPAddress
      false -> null
    }

    return try {
      val verifyFormRequest = BodyInserters.fromFormData("secret", secretKey)
        .with("response", captchaToken)
        .with("sitekey", siteKey)
      if (userIP != null) {
        LOG.info("Attaching user IP to verification request: {}", userIP)
        verifyFormRequest.with("remoteip", userIP)
      }

      val response = verifyClient.post()
        .uri(VERIFY_CAPTCHA_ENDPOINT)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(verifyFormRequest)
        .retrieve()
        .onStatus(HttpStatusCode::isError) { clientResponse ->
          clientResponse.bodyToMono(HCaptchaSiteVerifyResponse::class.java)
            .map { errorResponse ->
              formHCaptchaError(errorResponse)
            }
        }
        .awaitBody<HCaptchaSiteVerifyResponse>()

      determineHCaptchaStatus(response, ignoreScore)
    } catch (x: Exception) {
      throw ApiException(
        ApiError.HCAPTCHA_SITE_VERIFY_REQUEST_ERROR,
        "Error Encountered attempting to send POST request to hCaptcha for request: ${verifyCaptchaRequest.responseToken} with error: ${x.message}",
        x
      )
    }
  }

  override suspend fun healthcheck(): Boolean {
    val response = statusClient.get()
      .uri(HEALTHCHECK_ENDPOINT)
      .retrieve()
      .awaitBody<HCaptchaHealthCheckResponse>()
    return determineHCaptchaHealthCheckStatus(response)
  }

  private fun determineHCaptchaHealthCheckStatus(hCaptchaHealthCheckResponse: HCaptchaHealthCheckResponse): Boolean {
    // "endpoint includes an indicator - one of none, minor, major, or critical"
    // - https://www.hcaptchastatus.com/api#status
    return hCaptchaHealthCheckResponse.status.indicator == "none"
  }

  private fun determineHCaptchaStatus(
    hCaptchaSiteVerifyResponse: HCaptchaSiteVerifyResponse,
    ignoreScore: Boolean
  ): CaptchaStatus {
    LOG.info(
      "HCaptcha Status received {}", fields(hCaptchaSiteVerifyResponse)
    )
    if (hCaptchaSiteVerifyResponse.errorCodes != null) throw formHCaptchaError(hCaptchaSiteVerifyResponse)
    val success = hCaptchaSiteVerifyResponse.success
    val score = hCaptchaSiteVerifyResponse.score

    return when {
      ignoreScore -> CaptchaStatus.GOOD // Override when header value is passed for testing
      score >= 0.8 -> CaptchaStatus.BAD // High score means bot
      score >= 0.7 -> CaptchaStatus.RESEND // Mid score means unsure, so have them captcha again
      success -> CaptchaStatus.GOOD // Low score and success true means everything looks good
      else -> CaptchaStatus.RESEND // Low score but no success means there was an issue, redo.
    }
  }

  private fun formHCaptchaError(hCaptchaSiteVerifyResponse: HCaptchaSiteVerifyResponse): ApiException {
    var errorMessage = "Error Codes returned from hCaptcha Request"
    hCaptchaSiteVerifyResponse.errorCodes?.forEach { errorCode ->
      errorMessage += " - $errorCode"
    }
    return ApiException(ApiError.HCAPTCHA_SITE_VERIFY_REQUEST_ERROR, errorMessage)
  }
}
