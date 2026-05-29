package io.amplica.custodial_wallet.controller.util

import io.amplica.custodial_wallet.dto.GlobalApiError
import io.amplica.custodial_wallet.exception.ApiError

object ThymeleafHelper {

    private val siwaErrorMessageCodeMap = hashMapOf(
        ApiError.SIWA_SESSION_NOT_FOUND to "expired-session",
        ApiError.NO_MSA_ID_FOUND_ERROR to "provider-not-on-chain",
        ApiError.MSA_ID_NOT_ON_WHITELIST_ERROR to "provider-not-whitelisted",
        ApiError.PROVIDER_METADATA_NOT_FOUND to "provider-no-metadata",
        ApiError.RESEND_LIMIT_EXCEEDED to "resend-limit",
        ApiError.ALL_SMS_CLIENTS_FAILED to "sms-send",
        ApiError.UNKNOWN_NOTIFICATION_SERVICE_ERROR to "sms-send",
        ApiError.SIWA_SESSION_NOT_FOUND_FOR_TOKEN to "incorrect-token",
        ApiError.INCORRECT_TOKEN_RETRY_LIMIT_EXCEEDED to "retry-limit",
        ApiError.CAPTCHA_REQUIREMENT_NOT_SATISFIED to "captcha-not-satisfied",
        ApiError.RESEND_CAPTCHA_ATTEMPT to "resend-captcha",
        ApiError.HCAPTCHA_SITE_VERIFY_REQUEST_ERROR to "verify-captcha",
        ApiError.BAD_CAPTCHA_ATTEMPT to "unknown", // Set to unknown to prevent leaking info on captcha bot detection
        ApiError.BLOCKED_CONTACT_METHOD to "unknown", // Set to unknown to prevent leaking block list
        ApiError.INVALID_SHARED_SECRET to "unknown", // Set to unknown to prevent leaking shared secret detection
        ApiError.SIWA_INVALID_STATE to "unknown" // Set to unknown because the error is too vague.
        )

    fun hasError(expectedError: String, error: GlobalApiError?): Boolean {
        if(error == null) return false
        val expectedApiError = ApiError.valueOf(expectedError)
        return error.exception.contains(expectedApiError)
    }

    fun errorExists(error: GlobalApiError?): Boolean {
        return error != null
    }

    fun getErrorMessageCode(error: GlobalApiError?): String {
        val apiError = error?.exception?.firstOrNull() ?: return "unknown"
        return siwaErrorMessageCodeMap[apiError] ?: "unknown"
    }

}