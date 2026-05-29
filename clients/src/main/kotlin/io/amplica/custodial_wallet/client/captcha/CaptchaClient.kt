package io.amplica.custodial_wallet.client.captcha

enum class CaptchaStatus {
  GOOD, BAD, RESEND
}

data class VerifyCaptchaRequest(
  val responseToken: String?,
  val userIPAddress: String?,
)

abstract class CaptchaClient(val enabled: Boolean, val siteKey: String) {
  abstract suspend fun verifyCaptchaStatus(captchaToken: String?, userIp: String?, xCaptchaHeaderValue: String?)
  abstract suspend fun healthcheck(): Boolean
}