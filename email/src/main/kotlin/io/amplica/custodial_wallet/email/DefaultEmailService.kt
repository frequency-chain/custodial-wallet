package io.amplica.custodial_wallet.email

import io.amplica.custodial_wallet.email.client.SendEmailRequest
import io.amplica.custodial_wallet.email.client.SendEmailResponse
import io.amplica.custodial_wallet.email.client.SesClient
import io.amplica.custodial_wallet.email.client.TemplateExistsRequest
import io.amplica.custodial_wallet.email.client.conf.AwsSesProperties
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.service.organization.AssetType
import io.amplica.custodial_wallet.service.provider_metadata.ProviderMetadata
import net.logstash.logback.argument.StructuredArguments
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.util.UriComponentsBuilder
import software.amazon.awssdk.services.ses.model.SesException
import java.net.URI
import java.time.Duration
import java.util.*
import kotlin.math.abs

class DefaultEmailService(
  private val sesClient: SesClient,
  private val percentangeToPrimaryConfiguration: Int,
  private val awsSesProperties: AwsSesProperties,
  private val secondarySesProperties: AwsSesProperties,
  private val otpExpiration: Duration,
  private val hostName: String,
) : EmailService {
  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(DefaultEmailService::class.java)
  }

  override suspend fun sendSignUpEmail(
    userIdentifierValue: String,
    uri: URI,
    token: String,
    sessionId: String,
    locale: Locale,
    providerMetadata: ProviderMetadata
  ) {
    val urlLink = createUrlSessionLink(uri, token, sessionId)
    val sendEmailRequest = createSendEmailRequestWithUrlLinkSession(
      urlLink,
      awsSesProperties.signupTemplateName,
      userIdentifierValue,
      token,
      sessionId,
      locale,
      providerMetadata
    )
    sendEmailWithLogging(sesClient, sessionId, sendEmailRequest, userIdentifierValue)
  }

  override suspend fun sendLoginEmail(
    userIdentifierValue: String,
    uri: URI,
    token: String,
    sessionId: String,
    locale: Locale,
    providerMetadata: ProviderMetadata
  ) {
    val urlLink = createUrlSessionLink(uri, token, sessionId)
    val sendEmailRequest = createSendEmailRequestWithUrlLinkSession(
      urlLink,
      awsSesProperties.loginTemplateName,
      userIdentifierValue,
      token,
      sessionId,
      locale,
      providerMetadata
    )
    sendEmailWithLogging(sesClient, sessionId, sendEmailRequest, userIdentifierValue)
  }

  override suspend fun sendDirectLoginEmail(
    contactMethod: String,
    authenticationCode: String,
    sessionId: String,
    locale: Locale
  ) {
    val authenticationUrl = UriComponentsBuilder.fromUri(URI.create("$hostName/web/login/email"))
      .queryParam("authenticationCode", authenticationCode)
      .queryParam("sessionId", sessionId)
      .build()
      .toUriString()
    val sendEmailRequest = createSendEmailRequestWithUrlLinkSession(authenticationUrl, awsSesProperties.addIdentifierTemplateName, contactMethod, authenticationCode, sessionId, locale)
    sendEmailWithLogging(sesClient, sessionId, sendEmailRequest, contactMethod)
  }

  override suspend fun sendNewIdentifierVerificationEmail(
    contactMethod: String,
    verificationCode: String,
    sessionId: String,
    locale: Locale
  ) {
    val verificationUrl = UriComponentsBuilder.fromUri(URI.create("$hostName/web/add/email"))
      .queryParam("sessionId",sessionId)
      .queryParam("verificationCode",verificationCode)
      .build()
      .toUriString()
    val sendEmailRequest = createSendEmailRequestWithUrlLinkSession(verificationUrl, awsSesProperties.addIdentifierTemplateName, contactMethod, verificationCode, sessionId, locale)
    sendEmailWithLogging(sesClient, sessionId, sendEmailRequest, contactMethod)
  }

  override suspend fun sendSigningAuthenticationCodeEmail(
    userIdentifierValue: String,
    token: String,
    sessionId: String,
    locale: Locale,
    providerMetadata: ProviderMetadata
  ) {
    val authenticationUrl = UriComponentsBuilder.fromUri(URI.create("$hostName/web/sign/accept"))
      .queryParam("authenticationCode", token)
      .queryParam("sessionId",sessionId)
      .build()
      .toUriString()
    val sendEmailRequest = createSendEmailRequestWithUrlLinkSession(
      authenticationUrl,
      awsSesProperties.directLoginTemplateName,
      userIdentifierValue,
      token,
      sessionId,
      locale,
      providerMetadata
    )
    sendEmailWithLogging(sesClient, sessionId, sendEmailRequest, userIdentifierValue)
  }

  override suspend fun sendCommunityRewardsTokensReceivedEmail(
    contactMethod: String,
    token: String,
    sessionId: String,
    locale: Locale,
    numberOfTokens: Double,
    faAccountPageUrl: URI,
  ) {
    val urlLink = createUrlSessionLink(faAccountPageUrl, token, sessionId)
    val sendEmailRequest = createSendEmailRequestWithUrlLinkSession(
      urlLink,
      awsSesProperties.tokensReceivedTemplateName,
      contactMethod,
      token,
      sessionId,
      locale,
    )
    sendEmailRequest.additionalTemplateData[EmailTemplateDataKey.TOKEN_AMOUNT] = numberOfTokens
    sendEmailWithLogging(sesClient, sessionId, sendEmailRequest, contactMethod)
  }

  override suspend fun getEmailTemplateNameOrFallback(
    baseTemplateName: String,
    locale: Locale,
    providerShortCode: String?
  ): String {
    val possibleTemplateNames: List<String> = buildTemplateNameFallbackList(baseTemplateName, locale, providerShortCode)
    for (templateName in possibleTemplateNames) {
      val templateExistsRequest = TemplateExistsRequest(templateName)
      val templateExistsResponse = sesClient.templateExists(templateExistsRequest)
      if (templateExistsResponse.templateExists) {
        LOG.debug("For possibleTemplateNames={} templateName={} matches", possibleTemplateNames, templateName)
        return templateName
      }
    }
    throw ApiException(
      ApiError.NO_EMAIL_TEMPLATE_FOUND_ERROR,
      "No email template with the given base template name: $baseTemplateName"
    )
  }

  private suspend fun createSendEmailRequestWithUrlLinkSession(
    urlLink: String,
    baseTemplateName: String,
    destinationEmail: String,
    token: String,
    sessionId: String,
    locale: Locale,
    providerMetadata: ProviderMetadata? = null
  ): SendEmailRequest {
    val sendEmailRequest = createSendEmailRequest(baseTemplateName, destinationEmail, token, sessionId, locale, providerMetadata)
    sendEmailRequest.additionalTemplateData[EmailTemplateDataKey.URL_LINK] = urlLink
    return sendEmailRequest
  }

  private suspend fun createSendEmailRequest(
    baseTemplateName: String,
    destinationEmail: String,
    token: String,
    sessionId: String,
    locale: Locale,
    providerMetadata: ProviderMetadata ?= null,
  ): SendEmailRequest {
    val awsSesPropertiesToUse = determineAwsSesProperties(destinationEmail)
    val templateName = getEmailTemplateNameOrFallback(baseTemplateName, locale, providerMetadata?.shortcode)
    return  SendEmailRequest(
      destinationEmail,
      awsSesPropertiesToUse.sourceName,
      awsSesPropertiesToUse.sourceEmail,
      templateName,
      hashMapOf(
        EmailTemplateDataKey.EXPIRATION_TIME_MINUTES to otpExpiration.toMinutes(),
        EmailTemplateDataKey.SESSION_ID to sessionId,
        EmailTemplateDataKey.TOKEN to token,
        EmailTemplateDataKey.PROVIDER_NAME to (providerMetadata?.displayName ?: ""),
        EmailTemplateDataKey.PROVIDER_IMAGE_URL to (providerMetadata?.assets?.get(AssetType.BRAND_LOGO)?.url ?: "")
      )
    )
  }

  private fun createUrlSessionLink(url: URI, token: String, sessionId: String): String {
    return UriComponentsBuilder.fromUri(url)
      .queryParam("token", token)
      .queryParam("sessionId", sessionId)
      .queryParam("t", System.currentTimeMillis())
      .build()
      .toUriString()
  }

  private fun buildTemplateNameFallbackList(
    templateName: String,
    locale: Locale,
    providerShortCode: String?
  ): List<String> {
    val languageCode = locale.language
    val countryCode = locale.country.lowercase()

    return buildList {
      if (providerShortCode != null) {
        add("${templateName}_${providerShortCode}_${languageCode}_${countryCode}")
        add("${templateName}_${providerShortCode}_${languageCode}")
        add("${templateName}_${providerShortCode}")
      }
      add("${templateName}_${languageCode}_${countryCode}")
      add("${templateName}_${languageCode}")
      add(templateName)
    }
  }

  private suspend fun sendEmailWithLogging(
    sesClient: SesClient,
    sessionId: String,
    sendEmailRequest: SendEmailRequest,
    userIdentifierValue: String
  ): SendEmailResponse {
    try {
      LOG.info(
        "Attempting to send email {}",
        StructuredArguments.entries(
          mapOf(
            Pair("userIdentifierValue", userIdentifierValue),
            Pair("sessionId", sessionId)
          )
        )
      )
      val sendEmailResponse = sesClient.sendEmail(sendEmailRequest)
      LOG.info(
        "Email sent to {}",
        StructuredArguments.entries(
          mapOf(
            Pair("userIdentifierValue", userIdentifierValue),
            Pair("sessionId", sessionId),
            Pair("messageId", sendEmailResponse.messageId)
          )
        )
      )
      return sendEmailResponse
    } catch (x: SesException) {
      LOG.error(
        "SesException when trying to send an email to {} {}",
        x,
        mapOf(Pair("userIdentifierValue", userIdentifierValue), Pair("sessionId", sessionId))
      )
      throw x
    }
  }

  private fun determineAwsSesProperties(destinationEmail: String): AwsSesProperties {
    // Generate a value in the range 0..99 based on the destination address so that subsequent emails to the same
    // address are consistently sent using the same properties.
    val partitionPercentile = abs(destinationEmail.hashCode()) % 100

    if (partitionPercentile < percentangeToPrimaryConfiguration) {
      return awsSesProperties
    }

    return secondarySesProperties
  }
}
