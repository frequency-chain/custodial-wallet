package io.amplica.custodial_wallet.orchestration.siwa

import arrow.core.left
import com.google.i18n.phonenumbers.PhoneNumberUtil
import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.blocking.BlockingStrategy
import io.amplica.custodial_wallet.client.captcha.CaptchaClient
import io.amplica.custodial_wallet.client.kms.EncryptedKey
import io.amplica.custodial_wallet.client.kms.KmsDecryptionKey
import io.amplica.custodial_wallet.client.kms.KmsEncryptionAlgorithm
import io.amplica.custodial_wallet.client.notification.NotificationServiceClient
import io.amplica.custodial_wallet.client.notification.PhoneNumberBlockReason
import io.amplica.custodial_wallet.client.notification.PhoneNumberBlockStatus
import io.amplica.custodial_wallet.client.notification.PhoneNumberLookupResponse
import io.amplica.custodial_wallet.client.redis.CustodialWalletRedisClient
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifier
import io.amplica.custodial_wallet.client.redis.generateUUID
import io.amplica.custodial_wallet.controller.util.NormalizationUtil
import io.amplica.custodial_wallet.db.repository.*
import io.amplica.custodial_wallet.db.spring.DelegatingTransactionalOperator
import io.amplica.custodial_wallet.dto.*
import io.amplica.custodial_wallet.email.EmailService
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiErrorDto
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.internationalization.MessageFactory
import io.amplica.custodial_wallet.orchestration.LookupOrchestrationService
import io.amplica.custodial_wallet.orchestration.passkey.PasskeyWalletService
import io.amplica.custodial_wallet.orchestration.signing.SigningOrchestrationService
import io.amplica.custodial_wallet.service.frequency.FrequencyService
import io.amplica.custodial_wallet.service.ics_whitelist.IcsWhitelistService
import io.amplica.custodial_wallet.service.key.GeneratedKeyPairsBundle
import io.amplica.custodial_wallet.service.key.KeyService
import io.amplica.custodial_wallet.service.provider_metadata.ProviderMetadata
import io.amplica.custodial_wallet.service.verifiable_credential.VerifiableCredentialService
import io.amplica.custodial_wallet.service.whitelist_checker.ConfigWhitelistChecker
import io.amplica.custodial_wallet.util.*
import io.amplica.custodial_wallet.util.key_creation.Encoding
import io.amplica.custodial_wallet.util.key_creation.X25519KeyPairCreator
import io.amplica.custodial_wallet.validator.PhoneNumberValidator
import io.amplica.custodial_wallet.verifiablecredentials.dto.*
import io.amplica.custodial_wallet.web.AUTHORIZATION_CODE_PARAMETER_NAME
import io.amplica.custodial_wallet.web.Environment
import io.amplica.frequency.crypto.provider.Sr25519CryptoProvider
import io.amplica.frequency.payload.AddGraphKeyPayload
import io.amplica.frequency.payload.AddProviderPayload
import io.amplica.frequency.payload.CreateHandlePayload
import io.amplica.frequency.util.FrequencyEnvironment
import io.amplica.frequency.util.GraphConfiguration
import io.amplica.frequency.util.GraphHelper
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.support.ParameterDeclarations
import org.mockito.Mockito.mock
import org.mockito.kotlin.*
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.util.UriComponentsBuilder
import java.math.BigInteger
import java.net.URI
import java.time.Instant
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Stream

const val RESPONSE_TOKEN = "good-token"

class SiwaRequestArgumentsProvider : ArgumentsProvider {
  override fun provideArguments(parameters: ParameterDeclarations, context: ExtensionContext): Stream<Arguments> {
    return Stream.of(
      Arguments.of(SIWA_REQUEST),
      Arguments.of(SIWA_REQUEST_NO_APPLICATION_CONTEXT),
    )
  }
}

class SiwaIdentifierAndCaptchaTokenArgumentsProvider : ArgumentsProvider {
  override fun provideArguments(parameters: ParameterDeclarations, context: ExtensionContext): Stream<Arguments> {
    return Stream.of(
      Arguments.of(SiwaIdentifierAndCaptchaToken(EMAIL_IDENTIFIER.value, EMAIL_IDENTIFIER.type, RESPONSE_TOKEN)),
      Arguments.of(SiwaIdentifierAndCaptchaToken(PLUS_ADDRESSED_EMAIL_IDENTIFIER.value, EMAIL_IDENTIFIER.type, RESPONSE_TOKEN)),
      Arguments.of(SiwaIdentifierAndCaptchaToken(SMS_IDENTIFIER.value, SMS_IDENTIFIER.type, RESPONSE_TOKEN)),
    )
  }
}

class SiwaIdentifierAndCaptchaTokenPlusAddressingArgumentsProvider : ArgumentsProvider {
  override fun provideArguments(parameters: ParameterDeclarations, context: ExtensionContext): Stream<Arguments> {
    return Stream.of(
      Arguments.of(SiwaIdentifierAndCaptchaToken(EMAIL_IDENTIFIER.value, EMAIL_IDENTIFIER.type, RESPONSE_TOKEN)),
      Arguments.of(SiwaIdentifierAndCaptchaToken(PLUS_ADDRESSED_EMAIL_IDENTIFIER_NOT_WHITELISTED.value, EMAIL_IDENTIFIER.type, RESPONSE_TOKEN)),
    )
  }
}

class InvalidEmailSiwaIdentifierAndCaptchaTokenArgumentsProvider : ArgumentsProvider {
  override fun provideArguments(parameters: ParameterDeclarations, context: ExtensionContext): Stream<Arguments> {

    return Stream.of(
      Arguments.of(SiwaIdentifierAndCaptchaToken("john.doe.at.example.com", UserIdentifierType.EMAIL, RESPONSE_TOKEN)),
      Arguments.of(SiwaIdentifierAndCaptchaToken("www.fdgdfg@sdfsdfsd", UserIdentifierType.EMAIL, RESPONSE_TOKEN)),
      Arguments.of(SiwaIdentifierAndCaptchaToken("foobar.gov", UserIdentifierType.EMAIL, RESPONSE_TOKEN)),
      Arguments.of(SiwaIdentifierAndCaptchaToken(SMS_IDENTIFIER.value, UserIdentifierType.EMAIL, RESPONSE_TOKEN)),
    )
  }
}

class InvalidSmsSiwaIdentifierAndCaptchaTokenArgumentsProvider : ArgumentsProvider {
  override fun provideArguments(parameters: ParameterDeclarations, context: ExtensionContext): Stream<Arguments> {
    return Stream.of(
      Arguments.of(SiwaIdentifierAndCaptchaToken("+15555555555", UserIdentifierType.PHONE_NUMBER, RESPONSE_TOKEN)),
      Arguments.of(SiwaIdentifierAndCaptchaToken("+45989784", UserIdentifierType.PHONE_NUMBER, RESPONSE_TOKEN)),
    )
  }
}

class BlockedPhoneSiwaIdentifierAndCaptchaTokenArgumentsProvider : ArgumentsProvider {
  override fun provideArguments(parameters: ParameterDeclarations, context: ExtensionContext): Stream<Arguments> {
    return Stream.of(
      Arguments.of(SiwaIdentifierAndCaptchaToken("+18484448888", UserIdentifierType.PHONE_NUMBER, RESPONSE_TOKEN)),
    )
  }
}

class BlockedEmailDomainEmailSiwaIdentifierAndCaptchaTokenArgumentsProvider : ArgumentsProvider {
  override fun provideArguments(parameters: ParameterDeclarations, context: ExtensionContext): Stream<Arguments> {
    return Stream.of(
      Arguments.of(SiwaIdentifierAndCaptchaToken("john.doe@test.com", UserIdentifierType.EMAIL, RESPONSE_TOKEN)),
      Arguments.of(SiwaIdentifierAndCaptchaToken("test+1@test.com", UserIdentifierType.EMAIL, RESPONSE_TOKEN)),
    )
  }
}


class DefaultSiwaOrchestrationServiceTest {

  private val realGraphHelper = GraphHelper(GraphConfiguration(FrequencyEnvironment.ROCOCO, listOf()))

  // Mocks
  private val mockDatabaseService: CustodialWalletDatabaseService = mock()
  private val mockEmailService: EmailService = mock()
  private val mockKeyService: KeyService = mock()
  private val mockLookupService: LookupOrchestrationService = mock()
  private val mockSigningOrchestrationService: SigningOrchestrationService = mock()
  private val mockVerifiableCredentialService: VerifiableCredentialService = mock()
  private val mockNotificationServiceClient: NotificationServiceClient = mock()
  private val mockRedisClient: CustodialWalletRedisClient = mock()
  private val mockGraphHelper: GraphHelper = mock()
  private val mockMessageFactory: MessageFactory = mock()
  private val phoneNumberValidator = PhoneNumberValidator(PhoneNumberUtil.getInstance())
  private val hostName = "example.com"
  private val chainReference = "testnet"
  private val providerAdminSharedSecret = "secret"
  private val mockCaip122MessageFactory: MessageFactory = mock()
  private val mockCaptchaClient: CaptchaClient = mock()
  private val siteKey = "site-key"
  private val userIp = "1.1.1.1"
  private val xCaptchHeaderValue: String? = null
  private val mockBlockingStrategy: BlockingStrategy = mock()
  private val mockPasskeyWalletService: PasskeyWalletService = mock()
  private val normalizationUtil: NormalizationUtil = NormalizationUtil(PhoneNumberUtil.getInstance())
  private val whitelistChecker = ConfigWhitelistChecker(
    true,
    setOf("mewe.com", "unfinished.com", "projectliberty.io", "example.com", "test.com"),
    true,
    setOf("test.com")
  )
  private val mockIcsWhitelistService: IcsWhitelistService = mock()
  private val mockFrequencyService: FrequencyService = mock()

  private val transactionalOperatorTestDouble = createTransactionalOperatorDouble()
  private val delegatingTransactionalOperator = DelegatingTransactionalOperator(transactionalOperatorTestDouble, transactionalOperatorTestDouble)

  private val developerTermsCopy = "By integrating with Frequency Access, you agree to the [Developer Terms of Service]"
  private val environment = Environment.TEST

  private val orchestrationService = DefaultSiwaOrchestrationService(
    PROPERTIES,
    mockDatabaseService,
    mapOf(SiwaEmailHandling.OTP to mockEmailService),
    mockKeyService,
    mockLookupService,
    mockSigningOrchestrationService,
    mockVerifiableCredentialService,
    mockNotificationServiceClient,
    mockRedisClient,
    mockGraphHelper,
    mockMessageFactory,
    phoneNumberValidator,
    mockBlockingStrategy,
    hostName,
    chainReference,
    mockCaip122MessageFactory,
    mockCaptchaClient,
    providerAdminSharedSecret,
    mockPasskeyWalletService,
    normalizationUtil,
    whitelistChecker,
    delegatingTransactionalOperator,
    developerTermsCopy,
    mockIcsWhitelistService,
    mockFrequencyService,
  )

  @Nested
  @DisplayName("saveSiwaRequest")
  inner class SaveSiwaRequest {
    // TODO ...
  }

  @Nested
  @DisplayName("acceptSiwaRequest")
  inner class AcceptSiwaRequest {

    @BeforeEach
    fun setUp(): Unit = runBlocking {
      whenever(mockCaptchaClient.healthcheck()).thenReturn(true)
      whenever(mockCaptchaClient.verifyCaptchaStatus(RESPONSE_TOKEN, userIp, xCaptchHeaderValue)).thenReturn(Unit)
      whenever(mockCaptchaClient.siteKey).thenReturn(siteKey)
      whenever(mockLookupService.retrieveMsaId(any())).thenReturn(PROVIDER_MSA_ID)
      whenever(mockLookupService.getProviderMetaData(eq(PROVIDER_MSA_ID)))
        .thenReturn(PROVIDER_METADATA)
      whenever(mockLookupService.getProviderMetadataForApplication(eq(PROVIDER_MSA_ID), any()))
        .thenReturn(PROVIDER_METADATA)
      whenever(mockSigningOrchestrationService.signMessage(ACCOUNT_KEY_PAIR, CAIP_122_LOGIN_PAYLOAD_RESPONSE.message))
        .thenReturn(LOGIN_SIGNATURE)
    }

    @ParameterizedTest
    @ArgumentsSource(SiwaRequestArgumentsProvider::class)
    fun acceptSiwaRequestAcceptsValidRequest(siwaRequest: SiwaRequest): Unit = runBlocking {
      // GIVEN
      whenever(
        mockSigningOrchestrationService.verifySignedPayload(
          same(siwaRequest.signatureRequest.publicKey),
          any(),
          same(siwaRequest.signatureRequest.signature)
        )
      ).thenReturn(true)

      // WHEN
      runBlocking { orchestrationService.acceptSiwaRequest(siwaRequest, null, null) }

      // THEN
      // Ensure SIWA session has been stored in redis
      verify(mockRedisClient, times(1)).saveSiwaSession(argThat { siwaSession ->
        siwaSession.siwaRequest == siwaRequest
      })
    }

    @ParameterizedTest
    @ArgumentsSource(SiwaRequestArgumentsProvider::class)
    fun acceptSiwaRequestAcceptsValidRequestWithCallbackUrlParams(siwaRequest: SiwaRequest): Unit = runBlocking {
      // GIVEN
      val signatureRequest = siwaRequest.signatureRequest
      whenever(
        mockSigningOrchestrationService.verifySignedPayload(
          same(siwaRequest.signatureRequest.publicKey),
          any(),
          same(siwaRequest.signatureRequest.signature)
        )
      ).thenReturn(true)
      val callbackUriComponents = UriComponentsBuilder.fromUriString(signatureRequest.payload.callback)
        .queryParam("foo", "bar")
        .queryParam("baz", "biff")
        .build()

      // WHEN
      orchestrationService.acceptSiwaRequest(siwaRequest, callbackUriComponents.queryParams, null)

      // THEN
      // Ensure SIWA session has been stored in redis
      verify(mockRedisClient, times(1)).saveSiwaSession(argThat { siwaSession ->
        siwaSession.fullCallbackUrl == callbackUriComponents.toUriString()
      })
    }

    @Test
    fun acceptSiwaRequestAcceptsWithIllegalCallback(): Unit = runBlocking {
      // GIVEN
      whenever(
        mockSigningOrchestrationService.verifySignedPayload(
          same(SIWA_REQUEST.signatureRequest.publicKey),
          any(),
          same(SIWA_REQUEST.signatureRequest.signature)
        )
      ).thenReturn(true)
      val signedSiwaSignatureRequest = SIWA_REQUEST.signatureRequest
      val signatureRequestPayload = signedSiwaSignatureRequest.payload

      val callbackUrl = "https://www.example.com?authorizationCode=illegal"
      val illegalPayload = SiwaSignatureRequest(callbackUrl, signatureRequestPayload.permissions, signatureRequestPayload.userIdentifierAdminUrl)
      val illegalSignatureRequest = SignedSiwaSignatureRequest(signedSiwaSignatureRequest.publicKey, signedSiwaSignatureRequest.signature, illegalPayload)
      val illegalSiwaRequest = SiwaRequest(illegalSignatureRequest, SIWA_REQUEST.requestedCredentials)

      // WHEN THEN
      Assertions.assertThatThrownBy {
        runBlocking {
          orchestrationService.acceptSiwaRequest(illegalSiwaRequest, null, null)
        }
      }.isInstanceOf(ApiException::class.java)
        .hasFieldOrPropertyWithValue("apiError", ApiError.RESERVED_PARAMETER_VIOLATION)
        .hasMessage("The SignedSiwaRequest contains the parameter $AUTHORIZATION_CODE_PARAMETER_NAME which is illegal")
    }

    @Test
    fun acceptSiwaRequestAcceptsWithIllegalCallbackUrlParams(): Unit = runBlocking {
      // GIVEN
      whenever(
        mockSigningOrchestrationService.verifySignedPayload(
          same(SIWA_REQUEST.signatureRequest.publicKey),
          any(),
          same(SIWA_REQUEST.signatureRequest.signature)
        )
      ).thenReturn(true)
      val signedSiwaSignatureRequest = SIWA_REQUEST.signatureRequest
      val queryParams = LinkedMultiValueMap<String, String>()
      queryParams.add(AUTHORIZATION_CODE_PARAMETER_NAME, "illegal")
      val illegalSignatureRequest = SignedSiwaSignatureRequest(
        signedSiwaSignatureRequest.publicKey,
        signedSiwaSignatureRequest.signature,
        signedSiwaSignatureRequest.payload,
      )
      val illegalSiwaRequest = SiwaRequest(illegalSignatureRequest, SIWA_REQUEST.requestedCredentials)

      // WHEN THEN
      Assertions.assertThatThrownBy {
        runBlocking {
          orchestrationService.acceptSiwaRequest(illegalSiwaRequest, queryParams, null)
        }
      }.isInstanceOf(ApiException::class.java)
        .hasFieldOrPropertyWithValue("apiError", ApiError.RESERVED_PARAMETER_VIOLATION)
        .hasMessage("The SignedSiwaRequest contains the parameter $AUTHORIZATION_CODE_PARAMETER_NAME which is illegal")
    }

    @Test
    fun acceptSiwaRequestRejectsInvalidSignature(): Unit = runBlocking {
      // GIVEN
      whenever(
        mockSigningOrchestrationService.verifySignedPayload(
          same(SIWA_REQUEST.signatureRequest.publicKey),
          any(),
          same(SIWA_REQUEST.signatureRequest.signature)
        )
      ).thenReturn(false)

      // WHEN
      Assertions.assertThatThrownBy { runBlocking { orchestrationService.acceptSiwaRequest(SIWA_REQUEST, null, null) } }
        .isInstanceOf(ApiException::class.java)
        .hasMessageContaining("The SignedSiwaRequest contains an invalid signature")
    }

    @Test
    fun acceptSiwaRequestRejectsNonWhitelistedProvider(): Unit = runBlocking {
      // GIVEN
      whenever(
        mockSigningOrchestrationService.verifySignedPayload(
          same(SIWA_REQUEST.signatureRequest.publicKey),
          any(),
          same(SIWA_REQUEST.signatureRequest.signature)
        )
      ).thenReturn(true)
      whenever(mockLookupService.getProviderMetaData(eq(PROVIDER_MSA_ID)))
        .thenThrow(ApiException(ApiError.MSA_ID_NOT_ON_WHITELIST_ERROR, ""))
      whenever(mockLookupService.getProviderMetadataForApplication(eq(PROVIDER_MSA_ID), any()))
        .thenThrow(ApiException(ApiError.MSA_ID_NOT_ON_WHITELIST_ERROR, ""))

      // WHEN
      Assertions.assertThatThrownBy { runBlocking { orchestrationService.acceptSiwaRequest(SIWA_REQUEST, null, null) } }
        .isInstanceOf(ApiException::class.java)
    }
  }

  @Nested
  @DisplayName("acceptSavedSiwaRequestBySessionId")
  inner class AcceptSavedSiwaRequestBySessionId {
    // TODO ...
  }

  @Nested
  @DisplayName("acceptUserIdentifier")
  inner class AcceptUserIdentifier {

    private val sessionId = generateUUID()
    private val initialSiwaSession = UnauthenticatedSiwaSession(
      SIWA_REQUEST,
      sessionId,
      CALLBACK_URL,
      USER_KEY_PAIR_TYPE,
      SiwaFlowKind.SOCIAL,
    )

    @BeforeEach
    fun setUp(): Unit = runBlocking {
      whenever(mockRedisClient.findSiwaSessionBySessionId(eq(sessionId))).thenReturn(initialSiwaSession)
      whenever(mockLookupService.findSiwaSessionOrThrow(eq(sessionId))).thenReturn(initialSiwaSession)
      whenever(mockCaptchaClient.healthcheck()).thenReturn(true)
      whenever(mockCaptchaClient.verifyCaptchaStatus(RESPONSE_TOKEN, userIp, xCaptchHeaderValue)).thenReturn(Unit)
      whenever(mockCaptchaClient.siteKey).thenReturn(siteKey)
    }

    @ParameterizedTest
    @ArgumentsSource(SiwaIdentifierAndCaptchaTokenArgumentsProvider::class)
    fun acceptsValidRequest(siwaIdentifierAndCaptchaToken: SiwaIdentifierAndCaptchaToken): Unit = runBlocking {
      // GIVEN
      whenever(mockLookupService.retrieveMsaId(eq(PROVIDER_PUBLIC_KEY))).thenReturn(PROVIDER_MSA_ID)
      whenever(mockLookupService.getProviderMetadataForApplication(eq(PROVIDER_MSA_ID), any())).thenReturn(PROVIDER_METADATA)
      whenever(
        mockMessageFactory.createMessage(
          any(), any(), anyOrNull(), anyOrNull()
        )
      ).thenReturn("Example text message")
      whenever(mockNotificationServiceClient.lookupPhoneNumber(SMS_IDENTIFIER.value)).thenReturn(
        PhoneNumberLookupResponse(
          PhoneNumberBlockStatus.NotBlocked
        )
      )

      // WHEN
      val response = runBlocking { orchestrationService.acceptUserIdentifier(
        sessionId,
        siwaIdentifierAndCaptchaToken,
        userIp,
        providerAdminSharedSecret,
        Locale.US,
        xCaptchHeaderValue,
      ) }
      val userIdentifier = UserIdentifier(siwaIdentifierAndCaptchaToken.value, siwaIdentifierAndCaptchaToken.type)


      // THEN
      confirmRequestSuccessful(response, userIdentifier, PROVIDER_METADATA, 1, true)
    }

    @ParameterizedTest
    @ArgumentsSource(SiwaIdentifierAndCaptchaTokenPlusAddressingArgumentsProvider::class)
    fun acceptsValidRequestRemovesPlusAddressing(siwaIdentifierAndCaptchaToken: SiwaIdentifierAndCaptchaToken): Unit = runBlocking {
      // GIVEN
      whenever(mockLookupService.retrieveMsaId(eq(PROVIDER_PUBLIC_KEY))).thenReturn(PROVIDER_MSA_ID)
      whenever(mockLookupService.getProviderMetadataForApplication(eq(PROVIDER_MSA_ID), any())).thenReturn(ALTERNATE_PROVIDER_METADATA)

      // WHEN
      val response = runBlocking { orchestrationService.acceptUserIdentifier(
        sessionId,
        siwaIdentifierAndCaptchaToken,
        userIp,
        providerAdminSharedSecret,
        Locale.US,
        xCaptchHeaderValue,
      ) }
      val userIdentifier = UserIdentifier(normalizationUtil.stripPlusAddressing(siwaIdentifierAndCaptchaToken.value), siwaIdentifierAndCaptchaToken.type)

      // THEN
      confirmRequestSuccessful(response, userIdentifier, ALTERNATE_PROVIDER_METADATA, 1, true)
    }

    @ParameterizedTest
    @ArgumentsSource(SiwaIdentifierAndCaptchaTokenArgumentsProvider::class)
    fun acceptsValidFreebeeResend(siwaIdentifierAndCaptchaToken: SiwaIdentifierAndCaptchaToken): Unit = runBlocking {
      // GIVEN
      val siwaSession = initialSiwaSession.copy(
        authentication = IdentifierVerification("000000", Instant.now(), 1, 0)
      )
      whenever(mockLookupService.findSiwaSessionOrThrow(eq(sessionId))).thenReturn(siwaSession)
      whenever(mockLookupService.retrieveMsaId(eq(PROVIDER_PUBLIC_KEY))).thenReturn(PROVIDER_MSA_ID)
      whenever(mockLookupService.getProviderMetadataForApplication(eq(PROVIDER_MSA_ID), any())).thenReturn(PROVIDER_METADATA)
      whenever(
        mockMessageFactory.createMessage(
          any(), any(), anyOrNull(), anyOrNull()
        )
      ).thenReturn("Example text message")
      whenever(mockNotificationServiceClient.lookupPhoneNumber(SMS_IDENTIFIER.value)).thenReturn(
        PhoneNumberLookupResponse(
          PhoneNumberBlockStatus.NotBlocked
        )
      )

      // WHEN
      val response = runBlocking { orchestrationService.acceptUserIdentifier(
        sessionId,
        siwaIdentifierAndCaptchaToken,
        userIp,
        providerAdminSharedSecret,
        Locale.US,
        xCaptchHeaderValue,
      ) }
      val userIdentifier = UserIdentifier(siwaIdentifierAndCaptchaToken.value, siwaIdentifierAndCaptchaToken.type)


      // THEN
      confirmRequestSuccessful(response, userIdentifier, PROVIDER_METADATA, 1, true)
    }

    @ParameterizedTest
    @ArgumentsSource(BlockedEmailDomainEmailSiwaIdentifierAndCaptchaTokenArgumentsProvider::class)
    fun acceptsValidRequestAndCompletesFlowWithoutSendingEmail(siwaIdentifierAndCaptchaToken: SiwaIdentifierAndCaptchaToken): Unit = runBlocking {
      // GIVEN
      val siwaSession = initialSiwaSession.copy(
        authentication = IdentifierVerification("000000", Instant.now(), 1, 0)
      )
      whenever(mockLookupService.findSiwaSessionOrThrow(eq(sessionId))).thenReturn(siwaSession)
      whenever(mockLookupService.retrieveMsaId(eq(PROVIDER_PUBLIC_KEY))).thenReturn(PROVIDER_MSA_ID)
      whenever(mockLookupService.getProviderMetadataForApplication(eq(PROVIDER_MSA_ID), any())).thenReturn(PROVIDER_METADATA)
      whenever(
        mockMessageFactory.createMessage(
          any(), any(), anyOrNull(), anyOrNull()
        )
      ).thenReturn("Example text message")

      // WHEN
      val response = runBlocking { orchestrationService.acceptUserIdentifier(
        sessionId,
        siwaIdentifierAndCaptchaToken,
        userIp,
        providerAdminSharedSecret,
        Locale.US,
        xCaptchHeaderValue,
      ) }
      val userIdentifier = UserIdentifier(siwaIdentifierAndCaptchaToken.value, siwaIdentifierAndCaptchaToken.type)


      // THEN
      confirmRequestSuccessful(response, userIdentifier, PROVIDER_METADATA, 1, false)
    }

    private suspend fun confirmRequestSuccessful(response: SiwaResponse<SiwaProps>, userIdentifier: UserIdentifier, providerMetadata: ProviderMetadata, timesCalled: Int, checkForAuthenticationCodeSent: Boolean) {
      when (userIdentifier.type) {
        UserIdentifierType.EMAIL -> {
          val expectedTemplateAndProps = determineExpectedTemplate(PROPERTIES.defaultSiwaEmailHandling, userIdentifier, providerMetadata)

          Assertions.assertThat(response).isEqualTo(
            ViewResponse(
              expectedTemplateAndProps.first,
              expectedTemplateAndProps.second,
              null,
              MatomoData(
                expectedTemplateAndProps.first,
                SiwaMatomoDimensions.create(
                  providerMetadata.displayName,
                  SiwaMatomoDimensions.IntentType.SIGNUP,
                  userIdentifier.type,
                  environment,
                ),
                MatomoEvent(MatomoEvent.Category.SIWA, expectedTemplateAndProps.first.substringAfter("siwa/"))
              )
            )
          )
        }

        UserIdentifierType.PHONE_NUMBER -> {
          Assertions.assertThat(response).isEqualTo(
            ViewResponse(
              DefaultSiwaOrchestrationService.OTP_TEMPLATE,
              OtpVerificationSentProps(
                userIdentifier,
                providerMetadata.displayName,
                REDIS_EXPIRATION_MINUTES.toInt(),
                RESEND_DURATION.toMillis(),
                RESEND_LIMIT,
                USER_IDENTIFIER_ADMIN_URL
              ),
              null,
              MatomoData(
                DefaultSiwaOrchestrationService.OTP_TEMPLATE         ,
                SiwaMatomoDimensions.create(
                  providerMetadata.displayName,
                  SiwaMatomoDimensions.IntentType.SIGNUP,
                  userIdentifier.type,
                  environment
                ),
                MatomoEvent(MatomoEvent.Category.SIWA, DefaultSiwaOrchestrationService.OTP_TEMPLATE.substringAfter("siwa/"))
              )
            )
          )
        }
      }

      // Ensure identifier has been stored in redis
      verify(mockRedisClient, times(timesCalled)).saveSiwaSession(argThat { siwaSession ->
        siwaSession.siwaRequest == SIWA_REQUEST && siwaSession.id == sessionId && siwaSession.userIdentifier == userIdentifier
      })

      if(checkForAuthenticationCodeSent) {
        when (userIdentifier.type) {
          // Email service has been invoked
          UserIdentifierType.EMAIL -> verify(mockEmailService, times(timesCalled)).sendSignUpEmail(
            eq(userIdentifier.value), any(), any(), any(), any(), any()
          )

          // SMS message has been fired off
          UserIdentifierType.PHONE_NUMBER -> {
            verify(mockNotificationServiceClient, times(timesCalled)).sendSms(
              argThat { arg -> arg.destinationPhoneNumber == userIdentifier.value },
              anyOrNull(),
              anyOrNull(),
              anyOrNull(),
            )
            verify(mockMessageFactory, times(timesCalled)).createMessage(
              eq(SIGN_UP_TEMPLATE_NAME), any(), anyOrNull(), any()
            )
          }
        }
      }
    }

    @ParameterizedTest
    @ArgumentsSource(SiwaIdentifierAndCaptchaTokenArgumentsProvider::class)
    fun rejectsTemporarilyBlockedProvider(siwaIdentifierAndCaptchaToken: SiwaIdentifierAndCaptchaToken): Unit = runBlocking {
      // GIVEN
      whenever(mockLookupService.findSiwaSessionOrThrow(eq(sessionId))).thenReturn(initialSiwaSession)
      whenever(mockLookupService.retrieveMsaId(eq(PROVIDER_PUBLIC_KEY))).thenReturn(PROVIDER_MSA_ID)
      whenever(mockLookupService.getProviderMetadataForApplication(eq(PROVIDER_MSA_ID), any())).thenReturn(PROVIDER_METADATA)
      whenever(
        mockMessageFactory.createMessage(
          any(), any(), anyOrNull(), anyOrNull()
        )
      ).thenReturn("Example text message")
      whenever(mockBlockingStrategy.checkOrThrow(PROVIDER_MSA_ID, sessionId)).thenThrow(
        ApiException(ApiError.TEMPORARILY_BANNED_PROVIDER, "")
      )
      whenever(mockNotificationServiceClient.lookupPhoneNumber(SMS_IDENTIFIER.value)).thenReturn(
        PhoneNumberLookupResponse(
          PhoneNumberBlockStatus.NotBlocked
        )
      )

      // WHEN
      Assertions.assertThatThrownBy {
        runBlocking { orchestrationService.acceptUserIdentifier(
          sessionId,
          siwaIdentifierAndCaptchaToken,
          userIp,
          null,
          Locale.US,
          xCaptchHeaderValue,
        ) }
      }.isInstanceOf(ApiException::class.java).hasFieldOrPropertyWithValue("apiError", ApiError.TEMPORARILY_BANNED_PROVIDER)
    }

    @ParameterizedTest
    @ArgumentsSource(InvalidEmailSiwaIdentifierAndCaptchaTokenArgumentsProvider::class)
    fun rejectsInvalidEmailUserIdentifier(invalidEmailSiwaIdentifierAndCaptchaToken: SiwaIdentifierAndCaptchaToken): Unit = runBlocking {
      //GIVEN
      whenever(mockLookupService.retrieveMsaId(eq(PROVIDER_PUBLIC_KEY))).thenReturn(PROVIDER_MSA_ID)
      whenever(mockLookupService.getProviderMetadataForApplication(eq(PROVIDER_MSA_ID), any())).thenReturn(PROVIDER_METADATA)

      // WHEN
      val response = runBlocking {
        orchestrationService.acceptUserIdentifier(
          sessionId,
          invalidEmailSiwaIdentifierAndCaptchaToken,
          userIp,
          providerAdminSharedSecret,
          Locale.US,
          xCaptchHeaderValue,
        )
      }

      //THEN
      Assertions.assertThat(response).isEqualTo(
        ViewResponse(
          DefaultSiwaOrchestrationService.START_TEMPLATE,
          StartProps(
            siteKey,
            false,
            PROVIDER_DISPLAY_NAME,
            true,
            error = GlobalApiError(listOf(ApiError.INVALID_EMAIL))
          ),
          null
        )
      )
    }

    @ParameterizedTest
    @ArgumentsSource(InvalidSmsSiwaIdentifierAndCaptchaTokenArgumentsProvider::class)
    fun rejectsInvalidSmsUserIdentifier(invalidSmsSiwaIdentifierAndCaptchaToken: SiwaIdentifierAndCaptchaToken): Unit =
      runBlocking {
        //GIVEN
        whenever(mockLookupService.retrieveMsaId(eq(PROVIDER_PUBLIC_KEY))).thenReturn(PROVIDER_MSA_ID)
        whenever(mockLookupService.getProviderMetadataForApplication(eq(PROVIDER_MSA_ID), any())).thenReturn(
          PROVIDER_METADATA
        )

        // WHEN
        val response = runBlocking {
          orchestrationService.acceptUserIdentifier(
            sessionId,
            invalidSmsSiwaIdentifierAndCaptchaToken,
            userIp,
            providerAdminSharedSecret,
            Locale.US,
            xCaptchHeaderValue,
          )
        }

        //THEN
        Assertions.assertThat(response).isEqualTo(
          ViewResponse(
            DefaultSiwaOrchestrationService.START_TEMPLATE,
            StartProps(
              siteKey,
              false,
              PROVIDER_DISPLAY_NAME,
              true,
              error = GlobalApiError(listOf(ApiError.NOT_A_PHONE_NUMBER))
            ),
            null
          )
        )
      }

    @ParameterizedTest
    @ArgumentsSource(BlockedPhoneSiwaIdentifierAndCaptchaTokenArgumentsProvider::class)
    fun rejectsBlockedSmsUserIdentifier(blockedPhoneSiwaIdentifierAndCaptchaToken: SiwaIdentifierAndCaptchaToken): Unit =
      runBlocking {
        //GIVEN
        whenever(mockLookupService.retrieveMsaId(eq(PROVIDER_PUBLIC_KEY))).thenReturn(PROVIDER_MSA_ID)
        whenever(mockLookupService.getProviderMetadataForApplication(eq(PROVIDER_MSA_ID), any())).thenReturn(
          PROVIDER_METADATA
        )
        whenever(mockNotificationServiceClient.lookupPhoneNumber("+18484448888")).thenReturn(
          PhoneNumberLookupResponse(
            PhoneNumberBlockStatus.Blocked(PhoneNumberBlockReason.FORBIDDEN_PREFIX)
          )
        )

        // WHEN
        val response = runBlocking {
          orchestrationService.acceptUserIdentifier(
            sessionId,
            blockedPhoneSiwaIdentifierAndCaptchaToken,
            userIp,
            providerAdminSharedSecret,
            Locale.US,
            xCaptchHeaderValue,
          )
        }

        //THEN
        Assertions.assertThat(response).isEqualTo(
          ViewResponse(
            DefaultSiwaOrchestrationService.START_TEMPLATE,
            StartProps(
              siteKey,
              false,
              PROVIDER_DISPLAY_NAME,
              true,
              error = GlobalApiError(listOf(ApiError.BLOCKED_PHONE_NUMBER))
            ),
            null
          )
        )
      }

    @ParameterizedTest
    @ArgumentsSource(SiwaIdentifierAndCaptchaTokenArgumentsProvider::class)
    fun rejectsRequestTooSoon(siwaIdentifierAndCaptchaToken: SiwaIdentifierAndCaptchaToken): Unit = runBlocking {
      val siwaSession = initialSiwaSession.copy(
        authentication = IdentifierVerification("000000", Instant.now(), 2, 0)
      )
      whenever(mockLookupService.findSiwaSessionOrThrow(eq(sessionId))).thenReturn(siwaSession)
      whenever(mockLookupService.retrieveMsaId(eq(PROVIDER_PUBLIC_KEY))).thenReturn(PROVIDER_MSA_ID)
      whenever(mockLookupService.getProviderMetadataForApplication(eq(PROVIDER_MSA_ID), any())).thenReturn(
        PROVIDER_METADATA
      )

      //WHEN
      val response = runBlocking {
        orchestrationService.acceptUserIdentifier(
          sessionId,
          siwaIdentifierAndCaptchaToken,
          userIp,
          providerAdminSharedSecret,
          Locale.US,
          xCaptchHeaderValue,
        )
      }
      val userIdentifier = UserIdentifier(siwaIdentifierAndCaptchaToken.value, siwaIdentifierAndCaptchaToken.type)

      //THEN
      val expectedResponse: ViewResponse<SiwaProps> = when (userIdentifier.type) {
        UserIdentifierType.EMAIL -> {
          val expectedTemplateAndProps = determineExpectedTemplate(
            PROPERTIES.defaultSiwaEmailHandling, userIdentifier, PROVIDER_METADATA,
            GlobalApiError(listOf(ApiError.RESEND_REQUEST_INVALID,
              ),
            )
          )
          ViewResponse(
            expectedTemplateAndProps.first,
            expectedTemplateAndProps.second,
            null
          )
        }

        UserIdentifierType.PHONE_NUMBER -> {
          ViewResponse(
            DefaultSiwaOrchestrationService.OTP_TEMPLATE,
            OtpVerificationSentProps(
              userIdentifier,
              PROVIDER_DISPLAY_NAME,
              REDIS_EXPIRATION_MINUTES.toInt(),
              RESEND_DURATION.toMillis(),
              RESEND_LIMIT,
              USER_IDENTIFIER_ADMIN_URL,
              GlobalApiError(listOf(ApiError.RESEND_REQUEST_INVALID))
            ),
            null
          )
        }
      }
      Assertions.assertThat(response).isEqualTo(expectedResponse)

    }

    @ParameterizedTest
    @ArgumentsSource(SiwaIdentifierAndCaptchaTokenArgumentsProvider::class)
    fun rejectsTooManyRequests(siwaUserIdentifierAndCaptchaToken: SiwaIdentifierAndCaptchaToken): Unit = runBlocking {
      val siwaSession = initialSiwaSession.copy(
        authentication = IdentifierVerification(
          "000000", Instant.now().minusSeconds(RESEND_DURATION.toSeconds() + 10), RESEND_LIMIT + 1, 0
        )
      )
      whenever(mockLookupService.findSiwaSessionOrThrow(eq(sessionId))).thenReturn(siwaSession)

      whenever(mockLookupService.retrieveMsaId(eq(PROVIDER_PUBLIC_KEY))).thenReturn(PROVIDER_MSA_ID)
      whenever(mockLookupService.getProviderMetadataForApplication(eq(PROVIDER_MSA_ID), any())).thenReturn(PROVIDER_METADATA)

      // WHEN
      Assertions.assertThatThrownBy {
        runBlocking { orchestrationService.acceptUserIdentifier(
          sessionId,
          siwaUserIdentifierAndCaptchaToken,
          userIp,
          providerAdminSharedSecret,
          Locale.US,
          xCaptchHeaderValue,
        ) }
      }.isInstanceOf(ApiException::class.java).hasFieldOrPropertyWithValue("apiError", ApiError.RESEND_LIMIT_EXCEEDED)
        .hasMessage("Too many resend requests have been made")
    }

    @Test
    fun rejectsSmsInIcsFlow(): Unit = runBlocking {
      // GIVEN
      val siwaIdentifierAndCaptchaToken = SiwaIdentifierAndCaptchaToken(
        SMS_IDENTIFIER.value,
        SMS_IDENTIFIER.type,
        RESPONSE_TOKEN,
      )

      val siwaSession = initialSiwaSession.copy(flowKind = SiwaFlowKind.ICS)
      whenever(mockLookupService.findSiwaSessionOrThrow(eq(sessionId))).thenReturn(siwaSession)

      whenever(mockLookupService.retrieveMsaId(eq(PROVIDER_PUBLIC_KEY))).thenReturn(PROVIDER_MSA_ID)
      whenever(mockLookupService.getProviderMetadataForApplication(eq(PROVIDER_MSA_ID), any())).thenReturn(PROVIDER_METADATA)

      // WHEN / THEN
      Assertions.assertThatThrownBy {
        runBlocking {
          orchestrationService.acceptUserIdentifier(
            sessionId,
            siwaIdentifierAndCaptchaToken,
            userIp,
            providerAdminSharedSecret,
            Locale.US,
            xCaptchHeaderValue,
          )
        }
      }.isInstanceOf(ApiException::class.java)
        .hasFieldOrPropertyWithValue("apiError", ApiError.SIWA_INVALID_REQUEST)
        .hasMessage("Phone numbers cannot be used in this flow=ICS")
    }

    @Nested
    @DisplayName("With an existing UserAccount")
    inner class WithExistingUserAccount {

      private val userAccount = UserAccount(
        1.toBigInteger(),
        Instant.now().toEpochMilli().toBigInteger(),
        Instant.now().toEpochMilli().toBigInteger(),
        1.toBigInteger()
      )

      @ParameterizedTest
      @ArgumentsSource(SiwaIdentifierAndCaptchaTokenArgumentsProvider::class)
      fun acceptsValidRequest(siwaIdentifierAndCaptchaToken: SiwaIdentifierAndCaptchaToken): Unit = runBlocking {
        // GIVEN
        whenever(mockLookupService.findSiwaSessionOrThrow(eq(sessionId))).thenReturn(initialSiwaSession)

        whenever(mockLookupService.findUserAccountByUserIdentifier(eq(EMAIL_IDENTIFIER))).thenReturn(userAccount)
        whenever(mockLookupService.findUserAccountByUserIdentifier(eq(PLUS_ADDRESSED_EMAIL_IDENTIFIER))).thenReturn(userAccount)
        whenever(mockLookupService.findUserAccountByUserIdentifier(eq(SMS_IDENTIFIER))).thenReturn(userAccount)
        whenever(mockLookupService.retrieveMsaId(eq(PROVIDER_PUBLIC_KEY))).thenReturn(PROVIDER_MSA_ID)
        whenever(mockLookupService.getProviderMetadataForApplication(eq(PROVIDER_MSA_ID), any())).thenReturn(PROVIDER_METADATA)

        whenever(
          mockMessageFactory.createMessage(
            any(), any(), anyOrNull(), anyOrNull()
          )
        ).thenReturn("Example text message")

        // WHEN
        val response = runBlocking {
          orchestrationService.acceptUserIdentifier(
            sessionId,
            siwaIdentifierAndCaptchaToken,
            userIp,
            providerAdminSharedSecret,
            Locale.US,
            xCaptchHeaderValue,
          )
        }
        val userIdentifier = UserIdentifier(siwaIdentifierAndCaptchaToken.value, siwaIdentifierAndCaptchaToken.type)

        // THEN
        when (userIdentifier.type) {
          UserIdentifierType.EMAIL -> {
            val expectedTemplateAndProps = determineExpectedTemplate(PROPERTIES.defaultSiwaEmailHandling, userIdentifier, PROVIDER_METADATA)
            Assertions.assertThat(response).isEqualTo(
              ViewResponse(
                expectedTemplateAndProps.first,
                expectedTemplateAndProps.second,
                null,
                MatomoData(
                  expectedTemplateAndProps.first,
                  SiwaMatomoDimensions.create(
                    PROVIDER_METADATA.displayName,
                    SiwaMatomoDimensions.IntentType.LOGIN,
                    userIdentifier.type,
                    environment,
                  ),
                  MatomoEvent(MatomoEvent.Category.SIWA, expectedTemplateAndProps.first.substringAfter("siwa/"))
                )

              )
            )
          }

          UserIdentifierType.PHONE_NUMBER -> {
            Assertions.assertThat(response).isEqualTo(
              ViewResponse(
                DefaultSiwaOrchestrationService.OTP_TEMPLATE,
                OtpVerificationSentProps(
                  userIdentifier,
                  PROVIDER_DISPLAY_NAME,
                  REDIS_EXPIRATION_MINUTES.toInt(),
                  RESEND_DURATION.toMillis(),
                  RESEND_LIMIT,
                  USER_IDENTIFIER_ADMIN_URL
                ),
                null,
                MatomoData(
                  DefaultSiwaOrchestrationService.OTP_TEMPLATE,
                  SiwaMatomoDimensions.create(
                    PROVIDER_METADATA.displayName,
                    SiwaMatomoDimensions.IntentType.LOGIN,
                    userIdentifier.type,
                    environment,
                  ),
                  MatomoEvent(MatomoEvent.Category.SIWA, DefaultSiwaOrchestrationService.OTP_TEMPLATE.substringAfter("siwa/"))
                )
              )
            )
          }
        }

        // Ensure identifier has been stored in redis
        verify(mockRedisClient, times(1)).saveSiwaSession(argThat { siwaSession ->
          siwaSession.siwaRequest == SIWA_REQUEST && siwaSession.id == sessionId && siwaSession.userIdentifier == userIdentifier
        })

        when (userIdentifier.type) {
          // Email service has been invoked with the 'login' template
          UserIdentifierType.EMAIL -> verify(mockEmailService, times(1)).sendLoginEmail(
            eq(userIdentifier.value), any(), any(), any(), any(), any()
          )

          // SMS message has been fired off
          UserIdentifierType.PHONE_NUMBER -> {
            verify(mockNotificationServiceClient, times(1)).sendSms(
              argThat { arg ->
                arg.destinationPhoneNumber == userIdentifier.value &&
                        arg.userIp == userIp
              },
              anyOrNull(),
              anyOrNull(),
              anyOrNull()
            )
            verify(mockMessageFactory, times(1)).createMessage(
              eq(LOGIN_TEMPLATE_NAME), any(), anyOrNull(), any()
            )
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("acceptAuthenticationCode")
  inner class AcceptAuthenticationCode {

    private val sessionId = generateUUID()
    private val authenticationCode = "123456"
    private val idVerification = IdentifierVerification(authenticationCode, Instant.now(), 1, 0)
    private val unauthenticatedSiwaSession = UnauthenticatedSiwaSession(
      SIWA_REQUEST,
      sessionId,
      CALLBACK_URL,
      USER_KEY_PAIR_TYPE,
      SiwaFlowKind.SOCIAL,
      EMAIL_IDENTIFIER,
      idVerification,
    )
    private val unauthenticatedSiwaSessionMagicLink = UnauthenticatedSiwaSession(
      SIWA_REQUEST_MAGIC_LINK,
      sessionId,
      CALLBACK_URL,
      USER_KEY_PAIR_TYPE,
      SiwaFlowKind.SOCIAL,
      EMAIL_IDENTIFIER,
      idVerification,
    )

    @Test
    fun succeeds(): Unit = runBlocking {
      // GIVEN
      whenever(mockLookupService.findSiwaSession(eq(sessionId))).thenReturn(unauthenticatedSiwaSession)
      whenever(mockLookupService.retrieveMsaId(eq(PROVIDER_PUBLIC_KEY))).thenReturn(PROVIDER_MSA_ID)
      whenever(mockLookupService.getProviderMetadataForApplication(eq(PROVIDER_MSA_ID), any())).thenReturn(PROVIDER_METADATA)
      // WHEN
      val response = orchestrationService.acceptAuthenticationCode(authenticationCode, sessionId)

      // THEN
      val viewResponse = when (response) {
        is ViewResponse -> response
        else -> Assertions.fail("Response must be of type `ViewResponseWithSessionId`")
      }

      // Correct response values are returned
      Assertions.assertThat(viewResponse.template).isEqualTo(DefaultSiwaOrchestrationService.PAYLOADS_TEMPLATE)
      Assertions.assertThat(viewResponse.model).isEqualTo(
        PayloadsProps(
          true,
          EMAIL_IDENTIFIER.value,
          PROVIDER_DISPLAY_NAME,
          listOf("permission.one.two.three", "permission.four")
        )
      )
      Assertions.assertThat(viewResponse.sessionId).isNotEqualTo(sessionId)

      // Unauthenticated session was deleted from redis
      verify(mockRedisClient, times(1)).deleteSiwaSessionBySessionId(
        eq(sessionId)
      )

      val mockRedisClientOrderVerifier = inOrder(mockRedisClient)

      // First: New authenticated session was stored in redis with expected values
      mockRedisClientOrderVerifier.verify(mockRedisClient).saveSiwaSession(argThat { savedSiwaSession ->
        when (savedSiwaSession) {
          is UnauthenticatedSiwaSession -> false
          is AuthenticatedSiwaSession -> {
            savedSiwaSession.siwaRequest == SIWA_REQUEST && savedSiwaSession.userIdentifier == EMAIL_IDENTIFIER && savedSiwaSession.id == viewResponse.sessionId && savedSiwaSession.intent == null
          }
        }
      })

      // Second: The intent is stored
      mockRedisClientOrderVerifier.verify(mockRedisClient).saveSiwaSession(argThat { savedSiwaSession ->
        when (savedSiwaSession) {
          is UnauthenticatedSiwaSession -> false
          is AuthenticatedSiwaSession -> {
            savedSiwaSession.siwaRequest == SIWA_REQUEST && savedSiwaSession.userIdentifier == EMAIL_IDENTIFIER && savedSiwaSession.id == viewResponse.sessionId && savedSiwaSession.intent != null
          }
        }
      })
    }

    @Test
    fun errorsForIncorrectCode(): Unit = runBlocking {
      // GIVEN
      whenever(mockLookupService.findSiwaSession(eq(sessionId))).thenReturn(unauthenticatedSiwaSession)
      whenever(mockLookupService.findSiwaSessionOrThrow(eq(sessionId))).thenReturn(unauthenticatedSiwaSession)
      whenever(mockLookupService.retrieveMsaId(eq(PROVIDER_PUBLIC_KEY))).thenReturn(PROVIDER_MSA_ID)
      whenever(mockLookupService.getProviderMetadataForApplication(eq(PROVIDER_MSA_ID), any())).thenReturn(PROVIDER_METADATA)

      // WHEN
      val response = orchestrationService.acceptAuthenticationCode("000000", sessionId)

      val smsSentProps = OtpVerificationSentProps(
        UserIdentifier(EMAIL_IDENTIFIER.value, UserIdentifierType.EMAIL),
        PROVIDER_DISPLAY_NAME,
        REDIS_EXPIRATION_MINUTES.toInt(),
        RESEND_DURATION.toMillis(),
        RESEND_LIMIT,
        USER_IDENTIFIER_ADMIN_URL,
        GlobalApiError(listOf(ApiError.SIWA_SESSION_NOT_FOUND_FOR_TOKEN))
      )

      // THEN
      Assertions.assertThat(response).isEqualTo(
        ViewResponse(DefaultSiwaOrchestrationService.OTP_TEMPLATE, smsSentProps, null)
      )

      // Updates incorrect attempt count in redis
      verify(mockRedisClient, times(1)).saveSiwaSession(argThat { savedSiwaSession ->
        when (savedSiwaSession) {
          is UnauthenticatedSiwaSession -> {
            Assertions.assertThat(savedSiwaSession.id).isEqualTo(unauthenticatedSiwaSession.id)
            Assertions.assertThat(savedSiwaSession.siwaRequest).isEqualTo(SIWA_REQUEST)
            Assertions.assertThat(savedSiwaSession.authentication).usingRecursiveComparison()
              .ignoringFields("incorrectAttemptCount").isEqualTo(idVerification)
            Assertions.assertThat(savedSiwaSession.authentication?.incorrectAttemptCount).isEqualTo(1)

            true
          }

          is AuthenticatedSiwaSession -> false
        }
      })
    }

    @Nested
    @DisplayName("At the limit of incorrect attempts")
    inner class WithMaxIncorrectAttempts {

      private val unauthenticatedSiwaSession = UnauthenticatedSiwaSession(
        SIWA_REQUEST,
        sessionId,
        CALLBACK_URL,
        USER_KEY_PAIR_TYPE,
        SiwaFlowKind.SOCIAL,
        EMAIL_IDENTIFIER,
        IdentifierVerification(authenticationCode, Instant.now(), 1, INCORRECT_ATTEMPTS_LIMIT)
      )

      @Test
      fun acceptAuthenticationCodeErrorsCorrectCode(): Unit = runBlocking {
        // GIVEN
        whenever(mockLookupService.findSiwaSession(eq(sessionId))).thenReturn(unauthenticatedSiwaSession)

        // WHEN
        Assertions.assertThatThrownBy {
          runBlocking {
            orchestrationService.acceptAuthenticationCode(authenticationCode, sessionId)
          }
        }.isInstanceOf(ApiException::class.java)
          .hasFieldOrPropertyWithValue("apiError", ApiError.INCORRECT_TOKEN_RETRY_LIMIT_EXCEEDED)
          .hasMessage("The wrong authentication code has been entered too many times")
      }

      @Test
      fun acceptAuthenticationCodeErrorsIncorrectCode(): Unit = runBlocking {
        // GIVEN
        whenever(mockLookupService.findSiwaSession(eq(sessionId))).thenReturn(unauthenticatedSiwaSession)

        // WHEN
        Assertions.assertThatThrownBy {
          runBlocking {
            orchestrationService.acceptAuthenticationCode("000000", sessionId)
          }
        }.isInstanceOf(ApiException::class.java)
          .hasFieldOrPropertyWithValue("apiError", ApiError.INCORRECT_TOKEN_RETRY_LIMIT_EXCEEDED)
          .hasMessage("The wrong authentication code has been entered too many times")
      }
    }

    @Nested
    @DisplayName("With an existing UserAccount")
    inner class WithExistingUserAccount {

      private val emailUserDetail = UserDetail(
        EMAIL_IDENTIFIER.value, UserDetailType.EMAIL
      )
      private val userAccount = UserAccount(
        USER_ACCOUNT_ID,
        Instant.now().toEpochMilli().toBigInteger(),
        Instant.now().toEpochMilli().toBigInteger(),
        1.toBigInteger()
      )

      @Nested
      @DisplayName("All Permissions Already Granted")
      inner class PermissionsAlreadyGranted {
        @BeforeEach
        fun setup(): Unit = runBlocking {
          // GIVEN
          whenever(mockLookupService.findSiwaSession(eq(sessionId))).thenReturn(unauthenticatedSiwaSession)

          whenever(mockLookupService.retrieveMsaId(eq(PROVIDER_PUBLIC_KEY))).thenReturn(PROVIDER_MSA_ID)
          whenever(mockLookupService.getProviderMetadataForApplication(eq(PROVIDER_MSA_ID), any())).thenReturn(PROVIDER_METADATA)
          whenever(mockLookupService.findUserAccountByUserIdentifier(eq(EMAIL_IDENTIFIER))).thenReturn(userAccount)
          whenever(mockGraphHelper.containsPrivateGraphSchemas(any())).thenReturn(true)

          // User has an MSA ID claimed on the blockchain
          whenever(mockLookupService.getMsaIdByPublicKey(eq(ACCOUNT_KEY_PAIR.publicKeyBytes))).thenReturn(USER_MSA_ID)

          // User has a graph key registered on chain
          whenever(mockLookupService.getGraphKeysRegisteredOnChainForUser(eq(USER_MSA_ID), any())).thenReturn(
            listOf(fromHex(GRAPH_USER_KEY_DATA.publicKeyHex))
          )

          // User has an account key stored in the custodial wallet
          whenever(
            mockLookupService.findUserKeyDataOrThrow(
              eq(USER_ACCOUNT_ID),
              eq(KeyUsageType.ACCOUNT),
              eq(io.amplica.custodial_wallet.util.key_creation.KeyPairType.SR25519),
            )
          ).thenReturn(ACCOUNT_USER_KEY_DATA)

          // User has a graph key stored in the custodial wallet
          whenever(
            mockLookupService.findUserKeyData(
              eq(USER_ACCOUNT_ID),
              eq(KeyUsageType.GRAPH),
              eq(io.amplica.custodial_wallet.util.key_creation.KeyPairType.X25519)
            )
          ).thenReturn(
            GRAPH_USER_KEY_DATA
          )
          whenever(
            mockLookupService.findUserKeyData(
              eq(USER_ACCOUNT_ID),
              eq(KeyUsageType.GRAPH),
              eq(io.amplica.custodial_wallet.util.key_creation.KeyPairType.X25519)
            )
          ).thenReturn(
            GRAPH_USER_KEY_DATA
          )

          // User has previously delegated all the permissions the provider is requesting
          whenever(
            mockLookupService.getGrantedSchemasByPublicKey(
              eq(ACCOUNT_KEY_PAIR.publicKeyBytes), eq(PROVIDER_MSA_ID)
            )
          ).thenReturn(SIGNATURE_REQUEST.payload.permissions)

          whenever(mockKeyService.decryptUserAccountKeyData(eq(ACCOUNT_USER_KEY_DATA))).thenReturn(ACCOUNT_KEY_PAIR)
          whenever(mockKeyService.decryptUserGraphKeyData(eq(GRAPH_USER_KEY_DATA))).thenReturn(GRAPH_KEY_PAIR)

          val locale = Locale.US
          val caip122LoginPayloadResponse = Caip122LoginPayloadResponse("someMessage")
          whenever(mockCaip122MessageFactory.createMessage(eq("caip122"), any(), isNull(), same(locale))).thenReturn(
            caip122LoginPayloadResponse.message
          )
          whenever(mockSigningOrchestrationService.signMessage(ACCOUNT_KEY_PAIR, caip122LoginPayloadResponse.message)).thenReturn(
            LOGIN_SIGNATURE
          )
        }

        @Test
        fun savesSessionWithIntentToLoginWhenAllPermissionsAlreadyGrantedOtp(): Unit = runBlocking{
          // GIVEN
          whenever(mockLookupService.findSiwaSession(eq(sessionId))).thenReturn(unauthenticatedSiwaSession)
          whenever(mockPasskeyWalletService.retrieveCredentials(any())).thenReturn(CredentialResponsesDto(emptyList()))

          // WHEN
          val response = orchestrationService.acceptAuthenticationCode(authenticationCode, sessionId)

          // THEN
          val callbackResponse = when (response) {
            is CallbackResponse -> response
            else -> Assertions.fail("Expected `response` to be of type `CallbackResponse`")
          }
          Assertions.assertThat(callbackResponse.callbackUrl).isEqualTo(CALLBACK_URL)

          // User identifier verification data is updated
          verify(mockDatabaseService, times(1)).updateUserIdentifierVerifiedDate(eq(emailUserDetail))

          val mockRedisClientOrderVerifier = inOrder(mockRedisClient)

          // First an authenticated SIWA session is saved (no userAccountId and no payloads)
          mockRedisClientOrderVerifier.verify(mockRedisClient).saveSiwaSession(argThat { savedSession ->
            when (savedSession) {
              is AuthenticatedSiwaSession -> {
                // New Session ID was generated for the Authenticated session
                Assertions.assertThat(savedSession.id).isNotEqualTo(sessionId)

                // Otherwise the session has the same information it started with
                savedSession.siwaRequest == SIWA_REQUEST &&
                        savedSession.userIdentifier == EMAIL_IDENTIFIER &&
                        savedSession.authorizationCode == null &&
                        savedSession.intent == null
              }

              is UnauthenticatedSiwaSession -> false
            }
          })

          // Second an authenticated SIWA session is saved and has everything we need to redirect to provider (userAccountId and payloads)
          mockRedisClientOrderVerifier.verify(mockRedisClient).saveSiwaSession(argThat { savedSession ->
            when (savedSession) {
              is AuthenticatedSiwaSession -> {
                // Session has authorization code in it
                savedSession.siwaRequest == SIWA_REQUEST &&
                        savedSession.userIdentifier == EMAIL_IDENTIFIER &&
                        savedSession.authorizationCode == callbackResponse.authorizationCode &&
                        savedSession.intent == SiwaIntent.Login(USER_ACCOUNT_ID, true)
              }

              is UnauthenticatedSiwaSession -> false
            }
          })
        }

        @Test
        fun savesSessionWithIntentToLoginWhenAllPermissionsAlreadyGrantedMagicLink(): Unit = runBlocking {
          // GIVEN
          whenever(mockLookupService.findSiwaSession(eq(sessionId))).thenReturn(unauthenticatedSiwaSessionMagicLink)
          whenever(mockPasskeyWalletService.retrieveCredentials(any())).thenReturn(CredentialResponsesDto(emptyList()))
          whenever(mockLookupService.getProviderMetadataForApplication(eq(PROVIDER_MSA_ID), any())).thenReturn(PROVIDER_METADATA)

          // WHEN
          val response = orchestrationService.acceptAuthenticationCode(authenticationCode, sessionId)

          // THEN
          val viewResponse = when (response) {
            is ViewResponse -> response
            else -> Assertions.fail("Expected `response` to be of type `ViewResponse`")
          }

          val emailLoginProps: EmailLoginProps = when(val model = viewResponse.model) {
            is EmailLoginProps -> model
            else -> Assertions.fail("Expected `model` to be of type `TestEmailLoginProps`")
          }
          val redirectUriComponents = UriComponentsBuilder.fromUriString(emailLoginProps.redirect).build()
          Assertions.assertThat(redirectUriComponents.path).isEqualTo(CALLBACK_URL)

          // User identifier verification data is updated
          verify(mockDatabaseService, times(1)).updateUserIdentifierVerifiedDate(eq(emailUserDetail))

          val mockRedisClientOrderVerifier = inOrder(mockRedisClient)

          // First an authenticated SIWA session is saved (no userAccountId and no payloads)
          mockRedisClientOrderVerifier.verify(mockRedisClient).saveSiwaSession(argThat { savedSession ->
            when (savedSession) {
              is AuthenticatedSiwaSession -> {
                // New Session ID was generated for the Authenticated session
                Assertions.assertThat(savedSession.id).isNotEqualTo(sessionId)

                // Otherwise the session has the same information it started with
                savedSession.siwaRequest == SIWA_REQUEST_MAGIC_LINK &&
                        savedSession.userIdentifier == EMAIL_IDENTIFIER &&
                        savedSession.authorizationCode == null &&
                        savedSession.intent == null
              }

              is UnauthenticatedSiwaSession -> false
            }
          })

          // Second an authenticated SIWA session is saved and has everything we need to redirect to provider (userAccountId and payloads)
          mockRedisClientOrderVerifier.verify(mockRedisClient).saveSiwaSession(argThat { savedSession ->
            when (savedSession) {
              is AuthenticatedSiwaSession -> {
                // Session has authorization code in it
                savedSession.siwaRequest == SIWA_REQUEST_MAGIC_LINK &&
                        savedSession.userIdentifier == EMAIL_IDENTIFIER &&
                        savedSession.authorizationCode == redirectUriComponents.queryParams[AUTHORIZATION_CODE_PARAMETER_NAME]!!.first() &&
                        savedSession.intent == SiwaIntent.Login(USER_ACCOUNT_ID, true)
              }

              is UnauthenticatedSiwaSession -> false
            }
          })
        }
      }

      // 'migration' of existing user to a new provider
      @Test
      fun savesSessionWithIntentToUpdateBlockchainWhenDelegationNeeded(): Unit = runBlocking {
        // GIVEN
        whenever(mockLookupService.findSiwaSession(eq(sessionId))).thenReturn(unauthenticatedSiwaSession)

        whenever(mockLookupService.retrieveMsaId(eq(PROVIDER_PUBLIC_KEY))).thenReturn(PROVIDER_MSA_ID)
        whenever(mockLookupService.getProviderMetadataForApplication(eq(PROVIDER_MSA_ID), any())).thenReturn(PROVIDER_METADATA)
        whenever(mockLookupService.findUserAccountByUserIdentifier(eq(EMAIL_IDENTIFIER))).thenReturn(userAccount)
        whenever(mockGraphHelper.containsPrivateGraphSchemas(any())).thenReturn(true)

        // User has an MSA ID claimed on the blockchain
        whenever(mockLookupService.getMsaIdByPublicKey(eq(ACCOUNT_KEY_PAIR.publicKeyBytes))).thenReturn(USER_MSA_ID)
        // Use has graph key registered to the blockchain
        whenever(mockLookupService.getGraphKeysRegisteredOnChainForUser(eq(USER_MSA_ID), any()))
          .thenReturn(listOf(GRAPH_KEY_PAIR.publicKeyBytes))

        // User has an account and graph keys stored in the custodial wallet
        whenever(
          mockLookupService.findUserKeyDataOrThrow(
            eq(USER_ACCOUNT_ID),
            eq(KeyUsageType.ACCOUNT),
            eq(io.amplica.custodial_wallet.util.key_creation.KeyPairType.SR25519),
          )
        )
          .thenReturn(ACCOUNT_USER_KEY_DATA)
        whenever(
          mockLookupService.findUserKeyData(
            eq(USER_ACCOUNT_ID),
            eq(KeyUsageType.GRAPH),
            eq(io.amplica.custodial_wallet.util.key_creation.KeyPairType.X25519)
          )
        )
          .thenReturn(GRAPH_USER_KEY_DATA)

        // User has not yet delegated to this provider
        whenever(mockLookupService.getGrantedSchemasByPublicKey(eq(ACCOUNT_KEY_PAIR.publicKeyBytes), eq(PROVIDER_MSA_ID)))
          .thenReturn(emptyList())

        whenever(mockKeyService.decryptUserAccountKeyData(eq(ACCOUNT_USER_KEY_DATA))).thenReturn(ACCOUNT_KEY_PAIR)
        whenever(mockKeyService.decryptUserGraphKeyData(eq(GRAPH_USER_KEY_DATA))).thenReturn(GRAPH_KEY_PAIR)

        whenever(mockCaip122MessageFactory.createMessage(eq("caip122"), any(), isNull(), same(LOCALE)))
          .thenReturn(CAIP_122_LOGIN_PAYLOAD_RESPONSE.message)
        whenever(mockSigningOrchestrationService.signMessage(ACCOUNT_KEY_PAIR, CAIP_122_LOGIN_PAYLOAD_RESPONSE.message))
          .thenReturn(LOGIN_SIGNATURE)

        // WHEN
        val response = orchestrationService.acceptAuthenticationCode(authenticationCode, sessionId)

        // THEN
        val viewResponse = when (response) {
          is ViewResponse -> response
          else -> Assertions.fail("Response must be of type `ViewResponseWithSessionId`")
        }

        // Correct response values are returned
        Assertions.assertThat(viewResponse.template).isEqualTo(DefaultSiwaOrchestrationService.PAYLOADS_TEMPLATE)
        Assertions.assertThat(viewResponse.model).isEqualTo(
          PayloadsProps(
            false, // User already has a handle
            EMAIL_IDENTIFIER.value,
            PROVIDER_DISPLAY_NAME,
            listOf("permission.one.two.three", "permission.four")
          )
        )
        Assertions.assertThat(viewResponse.sessionId).isNotEqualTo(sessionId)

        // Unauthenticated session was deleted from redis
        verify(mockRedisClient, times(1)).deleteSiwaSessionBySessionId(
          eq(sessionId)
        )

        val mockRedisClientOrderVerifier = inOrder(mockRedisClient)

        // First: New authenticated session was stored in redis with expected values
        mockRedisClientOrderVerifier.verify(mockRedisClient).saveSiwaSession(argThat { savedSiwaSession ->
          when (savedSiwaSession) {
            is UnauthenticatedSiwaSession -> false
            is AuthenticatedSiwaSession -> {
              savedSiwaSession.siwaRequest == SIWA_REQUEST && savedSiwaSession.userIdentifier == EMAIL_IDENTIFIER && savedSiwaSession.id == viewResponse.sessionId && savedSiwaSession.intent == null
            }
          }
        })

        // Second: An intent is stored that will perform the necessary 'migration'
        val expectedIntent = SiwaIntent.UpdateBlockchain(
          listOf(
            SiwaBlockchainOperation.DelegatePermissions(PROVIDER_MSA_ID, SIWA_REQUEST.signatureRequest.payload.permissions),
          ),
          true
        )
        mockRedisClientOrderVerifier.verify(mockRedisClient).saveSiwaSession(argThat { savedSiwaSession ->
          when (savedSiwaSession) {
            is UnauthenticatedSiwaSession -> false
            is AuthenticatedSiwaSession -> {
              Assertions.assertThat(savedSiwaSession.intent).usingRecursiveComparison().isEqualTo(expectedIntent)

              savedSiwaSession.siwaRequest == SIWA_REQUEST &&
                      savedSiwaSession.userIdentifier == EMAIL_IDENTIFIER &&
                      savedSiwaSession.id == viewResponse.sessionId &&
                      savedSiwaSession.intent != null
            }
          }
        })
      }
    }
  }

  @Nested
  @DisplayName("acceptAcceptanceAndData")
  inner class AcceptAcceptanceAndData {

    private val sessionId = generateUUID()
    private val testHandle = "ExampleHandle"
    private val body = UserPayloadsAcceptanceAndDataCommand(testHandle)

    private val userAccount = UserAccount(
      Instant.now().toEpochMilli().toBigInteger(), Instant.now().toEpochMilli().toBigInteger()
    ).apply { this.id = USER_ACCOUNT_ID }

    private fun assertSavedSiwaSessionCorrect(
      savedSession: AuthenticatedSiwaSession,
      expectedAuthorizationCode: String
    ): Boolean {
      Assertions.assertThat(savedSession.siwaRequest).isEqualTo(SIWA_REQUEST)
      Assertions.assertThat(savedSession.id).isEqualTo(sessionId)
      Assertions.assertThat(savedSession.userIdentifier).isEqualTo(EMAIL_IDENTIFIER)
      Assertions.assertThat(savedSession.userAccountId).isEqualTo(USER_ACCOUNT_ID)
      Assertions.assertThat(savedSession.authorizationCode).isEqualTo(expectedAuthorizationCode)
      Assertions.assertThat(savedSession.prefillUserHandle).isEqualTo(testHandle)
      return true
    }

    @ParameterizedTest
    @CsvSource(value = [
      "SOCIAL, true, true",
      "SOCIAL, true, false",
      "SOCIAL, false, true",
      "SOCIAL, false, false",
      "ICS, false, false",
      "ICS, true, false"
    ])
    fun succeeds(flowKind: SiwaFlowKind, userAccountExists: Boolean, shouldGenerateGraphKey: Boolean): Unit = runBlocking {
      // GIVEN
      val intent = when (flowKind) {
        SiwaFlowKind.SOCIAL -> SiwaIntent.UpdateBlockchain(
          listOf(
            SiwaBlockchainOperation.CreateAccountAndDelegatePermissions(
              PROVIDER_MSA_ID, SIGNATURE_REQUEST.payload.permissions
            )
          ),
          sendGraphKeyPair = shouldGenerateGraphKey
        )

        SiwaFlowKind.ICS -> {
          Assertions.assertThat(shouldGenerateGraphKey).isFalse() // Graph key unsupported in ICS flow
          SiwaIntent.CreateSponsoredAccountAndLogin(claimHandle = false, sendGraphKeyPair = false)
        }
      }
      val authenticatedSiwaSession = AuthenticatedSiwaSession(
        SIWA_REQUEST,
        sessionId,
        EMAIL_IDENTIFIER,
        CALLBACK_URL,
        USER_KEY_PAIR_TYPE,
        flowKind,
        intent,
        null,
        null,
        null,
        testHandle
      )

      val now = Instant.now().toEpochMilli().toBigInteger()
      val userKeyData = UserKeyData.create(
        userAccount.id!!,
        byteArrayOf(1),
        EncryptedKey(byteArrayOf(1), KmsDecryptionKey("1", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)),
        io.amplica.custodial_wallet.util.key_creation.KeyPairType.SR25519,
        KeyUsageType.ACCOUNT
      )
      userKeyData.id = BigInteger.ONE
      val providerExternalUser = ProviderExternalUser(
        BigInteger.ONE,
        "providerExternalId",
        userKeyData.id!!,
        now,
        now
      )
      providerExternalUser.id = BigInteger.ONE
      val userIdentifierType = UserDetailType.fromUserIdentifierType(EMAIL_IDENTIFIER.type)

      val userIdentifier = io.amplica.custodial_wallet.db.repository.UserIdentifier(EMAIL_IDENTIFIER.value, userIdentifierType, now, now, now)
      userIdentifier.id = BigInteger.ONE

      whenever(mockDatabaseService.saveProviderExternalUser(any())).thenReturn(providerExternalUser)
      whenever(
        mockLookupService.findUserKeyDataOrThrow(
          userAccount.id!!,
          KeyUsageType.ACCOUNT,
          io.amplica.custodial_wallet.util.key_creation.KeyPairType.SR25519,
        )
      ).thenReturn(userKeyData)
      whenever(
        mockDatabaseService.findUserIdentifier(
          UserDetail(
            EMAIL_IDENTIFIER.value,
            userIdentifierType
          )
        )
      ).thenReturn(userIdentifier)
      whenever(mockLookupService.findAuthenticatedSiwaSessionOrThrow(eq(sessionId))).thenReturn(authenticatedSiwaSession)
      whenever(mockLookupService.retrieveMsaId(eq(PROVIDER_PUBLIC_KEY))).thenReturn(PROVIDER_MSA_ID)
      whenever(
        mockLookupService.getProviderMetadataForApplication(
          eq(PROVIDER_MSA_ID),
          eq(URI(VERIFIED_CREDENTIAL_URL))
        )
      ).thenReturn(PROVIDER_METADATA)


      // This user does not yet have any permissions granted to the provider on chain
      whenever(mockLookupService.getGrantedSchemasByPublicKey(any(), eq(PROVIDER_MSA_ID))).thenReturn(emptyList())

      whenever(mockLookupService.findUserAccountByUserIdentifier(eq(EMAIL_IDENTIFIER))).thenReturn(if (userAccountExists) userAccount else null)
        .thenReturn(userAccount)

      // Called after the userAccount is saved to the database
      whenever(
        mockDatabaseService.findUserAccountByUserIdentifier(
          eq(
            UserDetail(
              EMAIL_IDENTIFIER.value,
              UserDetailType.EMAIL
            )
          )
        )
      ).thenReturn(userAccount)

      whenever(mockGraphHelper.containsPrivateGraphSchemas(any())).thenReturn(shouldGenerateGraphKey)
      whenever(
        mockKeyService.generateAccountAndGraphKeyPairs(
          same(io.amplica.custodial_wallet.util.key_creation.KeyPairType.SR25519),
          eq(true),
        )
      ).thenReturn(
        GeneratedKeyPairsBundle(
          ACCOUNT_KEY_PAIR_DATA, GRAPH_KEY_PAIR_DATA
        )
      )
      whenever(
        mockKeyService.generateAccountAndGraphKeyPairs(
          same(io.amplica.custodial_wallet.util.key_creation.KeyPairType.SR25519),
          eq(false),
        )
      ).thenReturn(
        GeneratedKeyPairsBundle(
          ACCOUNT_KEY_PAIR_DATA, null
        )
      )
      whenever(mockKeyService.generateGraphKeyPair()).thenReturn(GRAPH_KEY_PAIR_DATA)
      whenever(
        mockKeyService.decryptUserAccountKeyData(eq(userKeyData))
      ).thenReturn(ACCOUNT_KEY_PAIR_DATA.keyPair)

      whenever(mockPasskeyWalletService.retrieveCredentials(authenticatedSiwaSession.id)).thenReturn(
        CredentialResponsesDto(emptyList())
      )

      // WHEN
      val response = orchestrationService.acceptAcceptanceAndData(sessionId, body)

      // THEN
      when (response) {
        is CallbackResponse -> {
          Assertions.assertThat(response.callbackUrl).isEqualTo(CALLBACK_URL)
          Assertions.assertThat(response.sessionId).isEqualTo(sessionId)
        }

        is ViewResponse<*> -> {
          Assertions.assertThat(response.template).isEqualTo("siwa/submissionInProgress")
          Assertions.assertThat(response.sessionId).isEqualTo(sessionId)
        }

        else -> Assertions.fail("Unexpected response type")
      }

      if (!userAccountExists) {
        // If we did not mock a UserAccount for this test then we expect keys and user account to be created
        verify(
          mockKeyService,
          times(1)
        ).generateAccountAndGraphKeyPairs(
          same(io.amplica.custodial_wallet.util.key_creation.KeyPairType.SR25519),
          eq(shouldGenerateGraphKey),
        )
        verify(mockDatabaseService, times(1)).saveNewUserData(
          eq(PROVIDER_MSA_ID),
          eq(normalizeToHex(ACCOUNT_KEY_PAIR_DATA.publicKey)),
          eq(listOf(UserDetail(EMAIL_IDENTIFIER.value, UserDetailType.EMAIL))),
          eq(
            listOfNotNull(
              ACCOUNT_KEY_PAIR_DATA.encryptedKeyData,
              if (shouldGenerateGraphKey) GRAPH_KEY_PAIR_DATA.encryptedKeyData else null
            )
          ),
        )
      } else {
        // If we did mock a UserAccount then no new user should have been created
        verify(mockDatabaseService, times(0)).saveNewUserData(
          anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()
        )
        // However, if a graph key was requested, that should have been generated and stored for the user
        if (shouldGenerateGraphKey) {
          verify(mockKeyService, times(1)).generateGraphKeyPair()
          verify(mockDatabaseService, times(1)).saveUserKeyData(argThat { internalUserKeyData ->
            internalUserKeyData.userAccountId == USER_ACCOUNT_ID
                && internalUserKeyData.keyUsageType == KeyUsageType.GRAPH
                && internalUserKeyData.publicKeyHex == toHex(GRAPH_KEY_PAIR_DATA.keyPair.publicKeyBytes)
          })
        }
      }

      if (intent is SiwaIntent.CreateSponsoredAccountAndLogin) {
        verify(mockRedisClient, times(1)).saveAsyncSubmission<Any>(argThat { submission ->
          submission.status == SubmissionStatus.SUBMITTED
        })
      }

      val authorizationCode = when (response) {
        is CallbackResponse -> response.authorizationCode
        is ViewResponse<*> -> {
          when (response.model) {
            is SiwaSubmissionInProgressProps -> {
              val model = response.model as SiwaSubmissionInProgressProps
              UriComponentsBuilder.fromUriString(model.redirect)
                .build()
                .queryParams
                .getFirst(AUTHORIZATION_CODE_PARAMETER_NAME)!!
            }
            else -> Assertions.fail("Unexpected `response.model` type")
          }
        }
        else -> Assertions.fail("Unexpected `response` type")
      }
      verify(mockRedisClient, times(1)).saveSiwaSession(argThat { siwaSession ->
        assertSavedSiwaSessionCorrect(siwaSession as AuthenticatedSiwaSession, authorizationCode)
      })
    }
  }

  @Nested
  @DisplayName("retrieveSiwaPayload")
  inner class RetrieveSiwaPayload {

    private val sessionId = generateUUID()
    private val authorizationCode = "123456"
    private val testHandle = "ExampleHandle"
    private val expiration = 9000L
    private val authenticatedSiwaSession = AuthenticatedSiwaSession(
      SIWA_REQUEST,
      sessionId,
      EMAIL_IDENTIFIER,
      CALLBACK_URL,
      USER_KEY_PAIR_TYPE,
      SiwaFlowKind.SOCIAL,
      SiwaIntent.UpdateBlockchain(
        listOf(
          SiwaBlockchainOperation.ClaimHandle,
          SiwaBlockchainOperation.CreateAccountAndDelegatePermissions(
            PROVIDER_MSA_ID,
            SIGNATURE_REQUEST.payload.permissions
          ),
          SiwaBlockchainOperation.RegisterPrivateGraphKey,
        ),
        true
      ),
      USER_ACCOUNT_ID,
      SiwaPayloadsUserInput(true, "ExampleHandle"),
      authorizationCode,
    )
    private val emailUserDetail = UserDetail(
      EMAIL_IDENTIFIER.value,
      UserDetailType.EMAIL
    )

    private val developerTermsLink = URI.create("${HOST_NAME}/developer_terms.html")

    @Test
    fun retrieveSiwaPayloadSucceeds(): Unit = runBlocking {
      //GIVEN
      whenever(mockLookupService.retrieveMsaId(any())).thenReturn(PROVIDER_MSA_ID)
      whenever(mockLookupService.getProviderMetadataForApplication(eq(PROVIDER_MSA_ID), any()))
        .thenReturn(PROVIDER_METADATA)
      whenever(mockLookupService.retrieveCurrentBlockNumber())
        .thenReturn(CURRENT_BLOCK_NUMBER)
      whenever(mockRedisClient.findSiwaSessionByAuthorizationCode(authorizationCode))
        .thenReturn(authenticatedSiwaSession)

      whenever(mockKeyService.decryptUserAccountKeyData(eq(ACCOUNT_USER_KEY_DATA)))
        .thenReturn(ACCOUNT_KEY_PAIR)
      whenever(mockKeyService.decryptUserGraphKeyData(eq(GRAPH_USER_KEY_DATA)))
        .thenReturn(GRAPH_KEY_PAIR)

      whenever(mockDatabaseService.findUserIdentifier(emailUserDetail))
        .thenReturn(
          io.amplica.custodial_wallet.db.repository.UserIdentifier(
            86.toBigInteger(),
            EMAIL_IDENTIFIER.value,
            UserDetailType.EMAIL,
            Instant.now().toEpochMilli().toBigInteger(),
            Instant.now().minusSeconds(3600).toEpochMilli().toBigInteger(),
            Instant.now().minusSeconds(3600).toEpochMilli().toBigInteger()
          )
        )

      whenever(mockGraphHelper.getGraphKeySchemaId()).thenReturn(GRAPH_KEY_SCHEMA_ID)
      whenever(mockGraphHelper.getDefaultPageHash()).thenReturn(DEFAULT_PAGE_HASH)

      // Dispatch to actual GraphHelper implementation
      whenever(mockGraphHelper.convertToDsnpPublicKey(any())).then {
        realGraphHelper.convertToDsnpPublicKey(it.arguments.first() as ByteArray)
      }

      // User has account key and graph key stored in the custodial wallet
      whenever(
        mockLookupService.findUserKeyDataOrThrow(
          eq(USER_ACCOUNT_ID),
          eq(KeyUsageType.ACCOUNT),
          eq(io.amplica.custodial_wallet.util.key_creation.KeyPairType.SR25519),
        )
      ).thenReturn(ACCOUNT_USER_KEY_DATA)
      whenever(
        mockLookupService.findUserKeyData(
          eq(USER_ACCOUNT_ID),
          eq(KeyUsageType.GRAPH),
          eq(io.amplica.custodial_wallet.util.key_creation.KeyPairType.X25519)
        )
      )
        .thenReturn(GRAPH_USER_KEY_DATA)

      // Mock signing service functionality
      val payloadExpiration = expiration + PROPERTIES.signupBlockExpiration
      whenever(
        mockSigningOrchestrationService.signPayload(
          eq(ACCOUNT_KEY_PAIR),
          eq(
            CreateHandlePayload(
              testHandle,
              payloadExpiration,
            )
          ),
          eq(Encoding.HEX),
        )
      ).thenReturn(CLAIM_HANDLE_SIGNATURE)
      whenever(
        mockSigningOrchestrationService.signPayload(
          eq(ACCOUNT_KEY_PAIR),
          eq(
            AddProviderPayload(
              PROVIDER_MSA_ID,
              SIGNATURE_REQUEST.payload.permissions,
              payloadExpiration,
            )
          ),
          eq(Encoding.HEX),
        )
      ).thenReturn(ADD_PROVIDER_SIGNATURE)
      val encodedGraphPublicKey = realGraphHelper.convertToDsnpPublicKey(GRAPH_KEY_PAIR.publicKeyBytes)
      whenever(
        mockSigningOrchestrationService.signPayload(
          eq(ACCOUNT_KEY_PAIR),
          eq(AddGraphKeyPayload(encodedGraphPublicKey, GRAPH_KEY_SCHEMA_ID, DEFAULT_PAGE_HASH, payloadExpiration)),
          eq(Encoding.HEX),
        )
      ).thenReturn(ITEM_ACTIONS_SIGNATURE)

      // Mock verifiable credential service
      whenever(mockVerifiableCredentialService.createVerifiableCredential(eq(ACCOUNT_KEY_PAIR), eq(EMAIL_IDENTIFIER), any()))
        .thenReturn(EMAIL_VERIFIABLE_CREDENTIAL)
      whenever(mockVerifiableCredentialService.createVerifiableCredential(eq(ACCOUNT_KEY_PAIR), eq(GRAPH_KEY_PAIR)))
        .thenReturn(GRAPH_KEY_VERIFIABLE_CREDENTIAL)

      val expectedDnspFormattedX25519KeyPairPublicKey = X25519KeyPairCreator.createX25519PublicKeyDtoDsnpFormat(graphHelper, GRAPH_KEY_PAIR)

      // WHEN
      val response = orchestrationService.retrieveSiwaPayload(authorizationCode)

      // THEN
      response.userKeys.forEach { userKey ->
        Assertions.assertThat(userKey.encodedPublicKeyValue).isNotEqualTo(userKey.encodedPrivateKeyValue)
      }

      Assertions.assertThat(response.userPublicKey).isEqualTo(ACCOUNT_KEY_PAIR_DATA.publicKey)
      Assertions.assertThat(response.payloads).usingRecursiveComparison().isEqualTo(
        listOf(
          TypedPayloadResponseWithSignature(
            CLAIM_HANDLE_SIGNATURE,
            FrequencyEndpoint.Handles.claimHandle,
            null,
            PayloadType.CLAIM_HANDLE,
            HandlePayloadResponse(
              testHandle,
              expiration + PROPERTIES.signupBlockExpiration
            ),
          ),
          TypedPayloadResponseWithSignature(
            ADD_PROVIDER_SIGNATURE,
            FrequencyEndpoint.Msa.createSponsoredAccountWithDelegation,
            null,
            PayloadType.ADD_PROVIDER,
            AddProviderPayloadResponse(
              PROVIDER_MSA_ID,
              SIGNATURE_REQUEST.payload.permissions,
              expiration + PROPERTIES.signupBlockExpiration,
            )
          ),
          TypedPayloadResponseWithSignature(
            ITEM_ACTIONS_SIGNATURE,
            FrequencyEndpoint.StatefulStorage.applyItemActionsWithSignatureV2,
            null,
            PayloadType.ITEM_ACTIONS,
            ItemizedSignaturePayloadResponse(
              GRAPH_KEY_SCHEMA_ID,
              DEFAULT_PAGE_HASH,
              expiration + PROPERTIES.signupBlockExpiration,
              listOf(
                AddItemAction(
                  expectedDnspFormattedX25519KeyPairPublicKey.encodedValue
                )
              )
            )
          )
        )
      )

      val subjects = response.credentials.map { it.credentialSubject }
      Assertions.assertThat(subjects)
        .hasExactlyElementsOfTypes(
          CredentialSubject.Email::class.java,
          CredentialSubject.KeyPair::class.java
        )

      response.credentials.forEach { credential ->
        when (val subject = credential.credentialSubject) {
          is CredentialSubject.Email -> {
            Assertions.assertThat(subject.emailAddress).isEqualTo(EMAIL_IDENTIFIER.value)
            Assertions.assertThat(subject.lastVerified)
              .isStrictlyBetween(EMAIL_VERIFIABLE_CREDENTIAL.validFrom, ZonedDateTime.now())
          }

          is CredentialSubject.KeyPair -> {
            Assertions.assertThat(subject)
              .usingRecursiveComparison()
              .isEqualTo(
                CredentialSubject.KeyPair(
                  "did:key:z???",
                  toHex(GRAPH_KEY_PAIR.publicKeyBytes),
                  toHex(GRAPH_KEY_PAIR.privateKeyBytes),
                  KeyPairEncoding.BASE_16,
                  KeyPairFormat.BARE,
                  KeyPairType.X25519,
                  DsnpKeyType.PublicKeyKeyAgreement
                )
              )
          }

          else -> Assertions.fail<String>("CredentialSubject must be EMAIL")
        }
      }
      
      Assertions.assertThat(response.termsCopy == developerTermsCopy)
      Assertions.assertThat(response.developerTermsLink == developerTermsLink)
    }

    @Test
    fun retrieveSiwaPayloadErrorsIncorrectAuthorizationCode(): Unit = runBlocking {
      // GIVEN
      whenever(mockRedisClient.findSiwaSessionBySessionId(eq(sessionId))).thenReturn(authenticatedSiwaSession)

      // THEN
      Assertions.assertThatThrownBy {
        runBlocking {
          orchestrationService.retrieveSiwaPayload("000000")
        }
      }.isInstanceOf(ApiException::class.java)
        .hasFieldOrPropertyWithValue("apiError", ApiError.SIWA_SESSION_NOT_FOUND_FOR_TOKEN)
        .hasMessage("No SIWA session found for the given session ID and authorization code")
    }
  }

  @Nested
  @DisplayName("getAsyncSubmission")
  inner class GetAsyncSubmission {

    @Test
    fun returnsSubmittedCorrectly(): Unit = runBlocking {
      // GIVEN
      val submissionId = generateUUID()
      val submission = AsyncSubmission<Nothing>(
        submissionId,
        SubmissionStatus.SUBMITTED,
      )

      whenever(
        mockRedisClient.findAsyncSubmission<Nothing>(eq(submissionId))
      ).thenReturn(submission)

      // WHEN
      val response = orchestrationService.getAsyncSubmission(submissionId)

      // THEN
      Assertions.assertThat(response.id).isEqualTo(submissionId)
      Assertions.assertThat(response.status).isEqualTo(submission.status)
    }

    @Test
    fun returnsSuccessCorrectly(): Unit = runBlocking {
      // GIVEN
      val submissionId = generateUUID()
      val submission = AsyncSubmission<Nothing>(
        submissionId,
        SubmissionStatus.SUCCESS,
      )

      whenever(
        mockRedisClient.findAsyncSubmission<Nothing>(eq(submissionId))
      ).thenReturn(submission)

      // WHEN
      val response = orchestrationService.getAsyncSubmission(submissionId)

      // THEN
      Assertions.assertThat(response.id).isEqualTo(submissionId)
      Assertions.assertThat(response.status).isEqualTo(submission.status)
    }

    @Test
    fun returnsFailureCorrectly(): Unit = runBlocking {
      // GIVEN
      val submissionId = generateUUID()
      val submission = AsyncSubmission(
        submissionId,
        SubmissionStatus.FAILED,
        0.left()
      )

      whenever(
        mockRedisClient.findAsyncSubmission<Nothing>(eq(submissionId))
      ).thenReturn(submission)

      // WHEN
      val response = orchestrationService.getAsyncSubmission(submissionId)

      // THEN
      Assertions.assertThat(response.id).isEqualTo(submissionId)
      Assertions.assertThat(response.status).isEqualTo(submission.status)
      Assertions.assertThat(response.error).usingRecursiveComparison().isEqualTo(
        ApiErrorDto(0, "An Unknown Error has Occurred", null)
      )
    }
  }

  @Test
  fun getTokenForSiwaSessionId_whenSessionFoundWithToken(): Unit = runBlocking {
    //GIVEN
    val sessionId = "someSessionId"
    val token = "someToken"
    val unauthenticatedSiwaSession = UnauthenticatedSiwaSession(
      siwaRequest = mock(SiwaRequest::class.java),
      authentication = IdentifierVerification(token, Instant.now(), 0, 0),
      fullCallbackUrl = CALLBACK_URL,
      flowKind = SiwaFlowKind.SOCIAL,
      userKeyPairType = USER_KEY_PAIR_TYPE,
    )
    whenever(mockLookupService.findSiwaSessionOrThrow(sessionId)).thenReturn(
      unauthenticatedSiwaSession
    )

    //WHEN THEN
    Assertions.assertThat(orchestrationService.getTokenForSiwaSessionId(sessionId)).isEqualTo(TokenResponse(token))
  }

  @Test
  fun getTokenForSiwaSessionId_whenNoSessionFound(): Unit = runBlocking {
    //GIVEN
    val sessionId = "someSessionId"
    val apiException = ApiException(
      ApiError.SIWA_SESSION_NOT_FOUND,
      "No SIWA session found for the given session ID",
      mapOf("sessionId" to sessionId)
    )
    whenever(mockLookupService.findSiwaSessionOrThrow(sessionId)).thenThrow(apiException)

    //WHEN THEN
    Assertions.assertThatThrownBy {
      runBlocking {
        orchestrationService.getTokenForSiwaSessionId(sessionId)
      }
    }.isSameAs(apiException)
  }

  @Test
  fun createSponsoredAccountInBackgroundCoroutineFailure(): Unit = runBlocking {
    //GIVEN
    val asyncSubmissionId = generateUUID()
    val userAccountKeyPair = Sr25519CryptoProvider.createKeyPair()
    whenever(mockFrequencyService.createUserAccount(same(userAccountKeyPair))).thenReturn(Result.failure(Exception("This is a Failure")))
    val isDone = AtomicBoolean(false)

    //WHEN
    DefaultSiwaOrchestrationService.createSponsoredAccountInBackgroundCoroutine(
      mockFrequencyService,
      mockLookupService,
      mockRedisClient,
      userAccountKeyPair,
      existingUserMsaId = null,
      handleToClaim = null,
      sessionId = generateUUID(),
      asyncSubmissionId = asyncSubmissionId,
    ) { isDone.set(true) }

    //THEN
    await untilTrue isDone
    verify(mockFrequencyService).createUserAccount(same(userAccountKeyPair))
    verifyNoInteractions(mockLookupService)
    verify(mockRedisClient).saveAsyncSubmission<Any>(argThat { submission ->
      submission.id == asyncSubmissionId && submission.status == SubmissionStatus.FAILED
    })
  }

  @Test
  fun createSponsoredAccountInBackgroundCoroutineNoHandle(): Unit = runBlocking {
    //GIVEN
    val asyncSubmissionId = generateUUID()
    val userAccountKeyPair = Sr25519CryptoProvider.createKeyPair()
    whenever(mockFrequencyService.createUserAccount(same(userAccountKeyPair))).thenReturn(Result.success(USER_MSA_ID))
    val isDone = AtomicBoolean(false)

    //WHEN
    DefaultSiwaOrchestrationService.createSponsoredAccountInBackgroundCoroutine(
      mockFrequencyService,
      mockLookupService,
      mockRedisClient,
      userAccountKeyPair,
      existingUserMsaId = null,
      handleToClaim = null,
      sessionId = generateUUID(),
      asyncSubmissionId = asyncSubmissionId,
    ) { isDone.set(true) }

    //THEN
    await untilTrue isDone
    verify(mockFrequencyService).createUserAccount(same(userAccountKeyPair))
    verifyNoInteractions(mockLookupService)
    verify(mockRedisClient).saveAsyncSubmission<Any>(argThat { submission ->
      submission.id == asyncSubmissionId && submission.status == SubmissionStatus.SUCCESS
    })
  }

  @Test
  fun createSponsoredAccountInBackgroundCoroutineWithHandle(): Unit = runBlocking {
    //GIVEN
    val asyncSubmissionId = generateUUID()
    val userAccountKeyPair = Sr25519CryptoProvider.createKeyPair()
    whenever(mockFrequencyService.createUserAccount(same(userAccountKeyPair))).thenReturn(Result.success(USER_MSA_ID))
    whenever(mockLookupService.getHandle(eq(USER_MSA_ID))).thenReturn(GetHandleResponse("", "", 0))

    val isDone = AtomicBoolean(false)

    //WHEN
    DefaultSiwaOrchestrationService.createSponsoredAccountInBackgroundCoroutine(
      mockFrequencyService,
      mockLookupService,
      mockRedisClient,
      userAccountKeyPair,
      existingUserMsaId = null,
      handleToClaim = "example-handle",
      sessionId = generateUUID(),
      asyncSubmissionId = asyncSubmissionId,
    ) { isDone.set(true) }

    //THEN
    await untilTrue isDone
    verify(mockFrequencyService).createUserAccount(same(userAccountKeyPair))
    verify(mockLookupService).getHandle(USER_MSA_ID)
    verify(mockRedisClient).saveAsyncSubmission<Any>(argThat { submission ->
      submission.id == asyncSubmissionId && submission.status == SubmissionStatus.SUCCESS
    })
  }

  fun determineExpectedTemplate(siwaEmailHandling: SiwaEmailHandling, userIdentifier: UserIdentifier, providerMetadata: ProviderMetadata, expectedError: GlobalApiError ? =null): Pair<String, SiwaProps> {
    return when(siwaEmailHandling) {
      SiwaEmailHandling.OTP -> {
        val props = OtpVerificationSentProps(
          userIdentifier,
          providerMetadata.displayName,
          REDIS_EXPIRATION_MINUTES.toInt(),
          RESEND_DURATION.toMillis(),
          RESEND_LIMIT,
          USER_IDENTIFIER_ADMIN_URL,
          expectedError,
        )
        DefaultSiwaOrchestrationService.OTP_TEMPLATE to props
      }
      SiwaEmailHandling.MAGIC_LINK -> {
        val props = MagicLinkVerificationSentProps(
          userIdentifier.value,
          REDIS_EXPIRATION_MINUTES.toString(),
          providerMetadata.displayName,
          RESEND_DURATION.toMillis(),
          RESEND_LIMIT,
          USER_IDENTIFIER_ADMIN_URL,
          expectedError,
        )
        DefaultSiwaOrchestrationService.MAGIC_LINK_SENT_TEMPLATE to props
      }
    }
  }
}
