package io.amplica.custodial_wallet.email

import io.amplica.custodial_wallet.email.client.*
import io.amplica.custodial_wallet.email.client.conf.AwsSesProperties
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.service.provider_metadata.ProviderMetadata
import io.amplica.custodial_wallet.template.TemplateConstants
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.*
import java.net.URI
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*

class DefaultEmailServiceTest {

  companion object {
    private const val ALL_PRIMARY_PROPERTIES = 100
    private const val ALL_SECONDARY_PROPERTIES = 0

    enum class AwsSesPropertiesType(val percentage: Int) {
      PRIMARY(ALL_PRIMARY_PROPERTIES), SECONDARY(ALL_SECONDARY_PROPERTIES);
    }
  }

  private lateinit var sesClient: SesClient
  private lateinit var primarySesProperties: AwsSesProperties
  private lateinit var secondarySesProperties: AwsSesProperties

  private val otpExpiration = Duration.of(10, ChronoUnit.MINUTES)
  private val hostname = "http://localhost"
  private val userIdentifier = "peter.frank@unfinished.com"
  private val token = "token"
  private val sessionId = "sessionId"
  private val locale = Locale.getDefault()
  private val messageId = "messageId"

  private lateinit var sendEmailCapture: ArgumentCaptor<SendEmailRequest>

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    sesClient = mock()
    whenever(sesClient.templateExists(any())).thenReturn(TemplateExistsResponse(true))

    primarySesProperties = AwsSesProperties(
      "primary",
      "primary@primary.com",
      "primaryLoginTemplate",
      "primarySignupTemplate",
      "primaryDirectLoginTemplate",
      "primaryAddIdentifierTemplate",
      "primaryTokensReceivedTemplate"

    )
    secondarySesProperties = AwsSesProperties(
      "secondary",
      "secondary@secondary.com",
      "secondaryLoginTemplate",
      "secondarySignupTemplate",
      "secondaryDirectLoginTemplate",
      "secondaryAddIdentifierTemplate",
      "secondaryTokensReceivedTemplate"
    )
    sendEmailCapture = ArgumentCaptor.captor()
  }

  private fun getEmailService(awsSesPropertiesType: AwsSesPropertiesType): EmailService = DefaultEmailService(
    sesClient,
    awsSesPropertiesType.percentage,
    primarySesProperties,
    secondarySesProperties,
    otpExpiration,
    hostname,
  )

  private fun getProperties(awsSesPropertiesType: AwsSesPropertiesType): AwsSesProperties =
    when (awsSesPropertiesType) {
      AwsSesPropertiesType.PRIMARY -> {
        primarySesProperties
      }

      AwsSesPropertiesType.SECONDARY -> {
        secondarySesProperties
      }
    }

  @ParameterizedTest
  @EnumSource(AwsSesPropertiesType::class)
  fun sendSignUpEmailFails(awsSesPropertiesType: AwsSesPropertiesType): Unit = runBlocking {
    //GIVEN
    val sendEmailResponse = SendEmailResponse(messageId)
    doReturn(sendEmailResponse).`when`(sesClient).sendEmail(argThat { arg -> arg.sourceEmail == "fail" })
    val emailService = getEmailService(awsSesPropertiesType)
    val providerMetadata = ProviderMetadata("MeWe", "mewe", emptyList(), emptyMap())

    //WHEN THEN
    Assertions.assertThatThrownBy {
      runBlocking {
        emailService.sendSignUpEmail(userIdentifier, URI.create(hostname), token, sessionId, locale, providerMetadata)
      }
    }.isInstanceOf(NullPointerException::class.java)
  }

  @ParameterizedTest
  @EnumSource(AwsSesPropertiesType::class)
  fun sendSignUpEmail(awsSesPropertiesType: AwsSesPropertiesType) = runBlocking {
    //GIVEN
    val awsProperties = getProperties(awsSesPropertiesType)
    val sendEmailResponse = SendEmailResponse(messageId)
    doReturn(sendEmailResponse).`when`(sesClient)
      .sendEmail(argThat { arg -> arg.sourceEmail == awsProperties.sourceEmail })
    val emailService = getEmailService(awsSesPropertiesType)
    val providerMetadata = ProviderMetadata("MeWe", "mewe", emptyList(), emptyMap())

    //WHEN THEN
    emailService.sendSignUpEmail(userIdentifier, URI.create(hostname), token, sessionId, locale, providerMetadata)
  }

  @ParameterizedTest
  @EnumSource(AwsSesPropertiesType::class)
  fun sendLoginEmail(awsSesPropertiesType: AwsSesPropertiesType) = runBlocking {
    //GIVEN
    val awsProperties = getProperties(awsSesPropertiesType)
    val sendEmailResponse = SendEmailResponse(messageId)
    doReturn(sendEmailResponse).`when`(sesClient)
      .sendEmail(argThat { arg -> arg.sourceEmail == awsProperties.sourceEmail })
    val emailService = getEmailService(awsSesPropertiesType)
    val providerMetadata = ProviderMetadata("MeWe", "mewe", emptyList(), emptyMap())

    //WHEN THEN
    emailService.sendLoginEmail(userIdentifier, URI.create(hostname), token, sessionId, locale, providerMetadata)
  }

  @ParameterizedTest
  @EnumSource(AwsSesPropertiesType::class)
  fun sendDirectLoginEmail(awsSesPropertiesType: AwsSesPropertiesType) = runBlocking {
    //GIVEN
    val awsProperties = getProperties(awsSesPropertiesType)
    val sendEmailResponse = SendEmailResponse(messageId)
    doReturn(sendEmailResponse).`when`(sesClient)
      .sendEmail(argThat { arg -> arg.sourceEmail == awsProperties.sourceEmail })
    val emailService = getEmailService(awsSesPropertiesType)

    //WHEN THEN
    emailService.sendDirectLoginEmail(userIdentifier, URI.create(hostname).toString(), sessionId, locale)
  }

  @ParameterizedTest
  @EnumSource(AwsSesPropertiesType::class)
  fun sendNewIdentifierVerificationEmail(awsSesPropertiesType: AwsSesPropertiesType) = runBlocking {
    //GIVEN
    val awsProperties = getProperties(awsSesPropertiesType)
    val sendEmailResponse = SendEmailResponse(messageId)
    doReturn(sendEmailResponse).`when`(sesClient)
      .sendEmail(argThat { arg -> arg.sourceEmail == awsProperties.sourceEmail })
    val emailService = getEmailService(awsSesPropertiesType)

    //WHEN THEN
    emailService.sendNewIdentifierVerificationEmail(userIdentifier, URI.create(hostname).toString(), sessionId, locale)
  }

  @ParameterizedTest
  @EnumSource(AwsSesPropertiesType::class)
  fun sendSigningAuthenticationCodeEmail(awsSesPropertiesType: AwsSesPropertiesType) = runBlocking {
    //GIVEN
    val awsProperties = getProperties(awsSesPropertiesType)
    val sendEmailResponse = SendEmailResponse(messageId)
    doReturn(sendEmailResponse).`when`(sesClient)
      .sendEmail(argThat { arg -> arg.sourceEmail == awsProperties.sourceEmail })
    val emailService = getEmailService(awsSesPropertiesType)
    val providerMetadata = ProviderMetadata("MeWe", "mewe", emptyList(), emptyMap())

    //WHEN THEN
    emailService.sendSigningAuthenticationCodeEmail(
      userIdentifier, URI.create(hostname).toString(), token, locale, providerMetadata
    )
  }

  @ParameterizedTest
  @EnumSource(AwsSesPropertiesType::class)
  fun sendCommunityRewardsTokensReceivedEmail(awsSesPropertiesType: AwsSesPropertiesType) = runBlocking {
    //GIVEN
    val awsProperties = getProperties(awsSesPropertiesType)
    val sendEmailResponse = SendEmailResponse(messageId)
    doReturn(sendEmailResponse).`when`(sesClient)
      .sendEmail(argThat { arg -> arg.sourceEmail == awsProperties.sourceEmail })
    val emailService = getEmailService(awsSesPropertiesType)

    //WHEN THEN
    emailService.sendCommunityRewardsTokensReceivedEmail(
      userIdentifier, token, sessionId, locale, 10.5, URI.create(hostname))
  }

  @ParameterizedTest
  @EnumSource(AwsSesPropertiesType::class)
  fun getEmailTemplateNameOrFallback(awsSesPropertiesType: AwsSesPropertiesType): Unit = runBlocking {
    //GIVEN
    val awsProperties = getProperties(awsSesPropertiesType)
    val emailService = getEmailService(awsSesPropertiesType)

    //WHEN
    val templateName = emailService.getEmailTemplateNameOrFallback(
      awsProperties.loginTemplateName,
      locale,
      TemplateConstants.DEFAULT_PROVIDER_NAME,
    )

    //THEN
    Assertions.assertThat(templateName)
      .isEqualTo("${awsProperties.loginTemplateName}_${TemplateConstants.DEFAULT_PROVIDER_NAME}_${locale.language}_${locale.country.lowercase()}")
  }

  @ParameterizedTest
  @EnumSource(AwsSesPropertiesType::class)
  fun getEmailTemplateNameOrFallbackForUndefinedShortcode(awsSesPropertiesType: AwsSesPropertiesType): Unit =
    runBlocking {
      //GIVEN
      val awsProperties = getProperties(awsSesPropertiesType)
      val emailService = getEmailService(awsSesPropertiesType)

      //WHEN
      val templateName = emailService.getEmailTemplateNameOrFallback(awsProperties.loginTemplateName, locale)

      //THEN
      Assertions.assertThat(templateName)
        .isEqualTo("${awsProperties.loginTemplateName}_${locale.language}_${locale.country.lowercase()}")
    }

  @ParameterizedTest
  @EnumSource(AwsSesPropertiesType::class)
  fun getEmailTemplateNameOrFallbackForDefaultFallback(awsSesPropertiesType: AwsSesPropertiesType): Unit = runBlocking {
    //GIVEN
    val awsProperties = getProperties(awsSesPropertiesType)
    val templateExistsResponse = TemplateExistsResponse(false)
    whenever(sesClient.templateExists(any())).thenReturn(templateExistsResponse)
    whenever(sesClient.templateExists(TemplateExistsRequest(awsProperties.loginTemplateName))).thenReturn(
      TemplateExistsResponse(true)
    )
    val emailService = getEmailService(awsSesPropertiesType)

    //WHEN
    val templateName = emailService.getEmailTemplateNameOrFallback(
      awsProperties.loginTemplateName,
      locale,
      "example-provider",
    )

    //THEN
    Assertions.assertThat(templateName).isEqualTo(awsProperties.loginTemplateName)
  }

  @ParameterizedTest
  @EnumSource(AwsSesPropertiesType::class)
  fun getEmailTemplateNameOrFallbackForNoTemplateFound(awsSesPropertiesType: AwsSesPropertiesType): Unit = runBlocking {
    //GIVEN
    val awsProperties = getProperties(awsSesPropertiesType)
    val templateExistsResponse = TemplateExistsResponse(false)
    whenever(sesClient.templateExists(any())).thenReturn(templateExistsResponse)
    val emailService = getEmailService(awsSesPropertiesType)

    //WHEN THEN
    Assertions.assertThatThrownBy {
      runBlocking {
        emailService.getEmailTemplateNameOrFallback(awsProperties.loginTemplateName, locale, "example-provider")
      }
    }.isInstanceOf(ApiException::class.java)
      .hasFieldOrPropertyWithValue("apiError", ApiError.NO_EMAIL_TEMPLATE_FOUND_ERROR)
      .hasMessage("No email template with the given base template name: ${awsProperties.loginTemplateName}")
  }
}