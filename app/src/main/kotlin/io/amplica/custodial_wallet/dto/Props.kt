package io.amplica.custodial_wallet.dto

import io.amplica.custodial_wallet.client.redis.dto.UserIdentifier
import io.amplica.custodial_wallet.exception.ApiError

sealed interface SiwaProps

data class GlobalApiError(
  val exception: List<ApiError>
)

data class ErrorProps(
  val apiError: ApiError,
  val errorMessage: String?,
  val stackTrace: String,
  val sessionId: String?,
  val xSessionId: String?,
  val xTraceId: String?,
  val providerName: String?,
  val siwaErrorTitle: String?,
  val siwaErrorDescription: String?,
)

data class StartProps(
  val siteKey: String,
  val captchaEnabled: Boolean,
  val providerName: String,
  val phoneNumberEnabled: Boolean,
  val prefilledEmail: String? = null,
  val prefilledPhoneNumber: String? = null,
  val error: GlobalApiError? = null,
  // TODO(#1574): Add provider image prop here
): SiwaProps

data class MagicLinkVerificationSentProps(
  val email: String,
  val expireTime: String,
  val providerName: String,
  val resendInterval: Long,
  val resendLimit: Int,
  val userIdentifierAdminUrl: String?,
  val error: GlobalApiError? = null,
): SiwaProps

data class OtpVerificationSentProps(
  val userIdentifier: UserIdentifier,
  val providerName: String,
  val otpTimeoutMinutes: Int,
  val resendInterval: Long,
  val resendLimit: Int,
  val userIdentifierAdminUrl: String?,
  val error: GlobalApiError? = null,
): SiwaProps

data class PayloadsProps(
  val claimHandle: Boolean,
  val identifier: String,
  val providerName: String,
  val permissionMessages: List<String>, // 'message.properties' keys
  val prefillUserHandle: String? = null,
  val error: GlobalApiError? = null,
  val showIcsTerms: Boolean = false,
): SiwaProps

data class EmailLoginProps(
  val redirect: String,
  val providerName: String,
): SiwaProps

data class SiwaSubmissionInProgressProps(
  val submissionId: String,
  val redirect: String,
  val providerName: String,
): SiwaProps

data class CreateWalletProps(
  val redirect: String,
  val error: GlobalApiError?
): SiwaProps

//Should these passkey props be taken out of SiwaProps? Or maybe refactor to not be specifically Siwa?
data class PasskeyWalletProps(
  val redirect: String,
  val frequencyAddress: String,
  val username: String,
  val isRecoveryFlow: Boolean? = false,
  val accountPublicKeyHex: String? = null,
  val error: GlobalApiError? = null,
): SiwaProps

data class PasskeyIFrameProps(
  val frequencyAddress: String,
  val username: String,
  val passkeyWalletRecovery: Boolean? = false,
): SiwaProps

data class MatomoProps(
  val enabled: Boolean,
  val url: String,
  val siteId: Int,
  val enableHeartbeat: Boolean,
  val data: MatomoData?,
) {
  fun withData(data: MatomoData?): MatomoProps {
    return if(data != null) {
      this.copy(data = data)
    } else { this }
  }
}

data class ProviderBoostingDemoProps(
  val frequencyAddress: String,
  val providers: List<ProviderUserInfo>
)
