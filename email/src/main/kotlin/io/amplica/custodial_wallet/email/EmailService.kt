package io.amplica.custodial_wallet.email

import io.amplica.custodial_wallet.service.provider_metadata.ProviderMetadata
import java.net.URI
import java.util.*

object EmailTemplateDataKey {
  const val EXPIRATION_TIME_MINUTES = "expirationTimeMinutes"
  const val SESSION_ID = "sessionId"
  const val TOKEN = "token"
  const val PROVIDER_NAME = "providerName"
  const val PROVIDER_IMAGE_URL = "providerImageUrl"
  const val URL_LINK = "url"
  const val TOKEN_AMOUNT = "tokenAmount"
}

interface EmailService {
  suspend fun sendSignUpEmail(
    userIdentifierValue: String,
    uri: URI,
    token: String,
    sessionId: String,
    locale: Locale,
    providerMetadata: ProviderMetadata
  )
  suspend fun sendLoginEmail(
    userIdentifierValue: String,
    uri: URI,
    token: String,
    sessionId: String,
    locale: Locale,
    providerMetadata: ProviderMetadata
  )
  suspend fun sendDirectLoginEmail(
    contactMethod: String,
    authenticationCode: String,
    sessionId: String,
    locale: Locale,
  )
  suspend fun sendNewIdentifierVerificationEmail(
    contactMethod: String,
    verificationCode: String,
    sessionId: String,
    locale: Locale
  )
  suspend fun sendSigningAuthenticationCodeEmail(
    userIdentifierValue: String,
    token: String,
    sessionId: String,
    locale: Locale,
    providerMetadata: ProviderMetadata
  )

  suspend fun sendCommunityRewardsTokensReceivedEmail(
    contactMethod: String,
    token: String,
    sessionId: String,
    locale: Locale,
    numberOfTokens: Double,
    faAccountPageUrl: URI,
  )
  suspend fun getEmailTemplateNameOrFallback(
    baseTemplateName: String,
    locale: Locale,
    providerShortCode: String? = null
  ): String
}