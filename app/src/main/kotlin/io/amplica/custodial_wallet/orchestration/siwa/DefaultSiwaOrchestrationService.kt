package io.amplica.custodial_wallet.orchestration.siwa

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.blocking.BlockingStrategy
import io.amplica.custodial_wallet.client.captcha.CaptchaClient
import io.amplica.custodial_wallet.client.notification.NotificationServiceClient
import io.amplica.custodial_wallet.client.notification.SendSmsRequest
import io.amplica.custodial_wallet.client.redis.CustodialWalletRedisClient
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifier
import io.amplica.custodial_wallet.client.redis.generateUUID
import io.amplica.custodial_wallet.controller.util.NormalizationUtil
import io.amplica.custodial_wallet.controller.util.getPermissionKeysForSchemaIds
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
import io.amplica.custodial_wallet.orchestration.util.mapUserIdentifierToUserDetail
import io.amplica.custodial_wallet.service.frequency.FrequencyService
import io.amplica.custodial_wallet.service.ics_whitelist.IcsWhitelistService
import io.amplica.custodial_wallet.service.key.KeyService
import io.amplica.custodial_wallet.service.provider_metadata.ProviderMetadata
import io.amplica.custodial_wallet.service.verifiable_credential.VerifiableCredentialService
import io.amplica.custodial_wallet.service.whitelist_checker.WhitelistChecker
import io.amplica.custodial_wallet.util.keyPairBytesToAccountKeyPair
import io.amplica.custodial_wallet.util.keyPairBytesToUniversalAddress
import io.amplica.custodial_wallet.util.key_creation.*
import io.amplica.custodial_wallet.util.normalizeToHex
import io.amplica.custodial_wallet.util.toIso8601Format
import io.amplica.custodial_wallet.validator.EmailValidator
import io.amplica.custodial_wallet.validator.MEWE_TEST_PHONE_PREFIX
import io.amplica.custodial_wallet.validator.PhoneNumberValidator
import io.amplica.custodial_wallet.web.*
import io.amplica.frequency.crypto.AccountKeyPair
import io.amplica.frequency.crypto.provider.Secp256K1CryptoProvider
import io.amplica.frequency.crypto.toPublicKeyBytes
import io.amplica.frequency.crypto.toUniversalAddress
import io.amplica.frequency.payload.AddGraphKeyPayload
import io.amplica.frequency.payload.AddProviderPayload
import io.amplica.frequency.payload.CreateHandlePayload
import io.amplica.frequency.util.GraphHelper
import io.amplica.frequency.util.toHex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.MultiValueMap
import org.springframework.web.util.UriComponents
import org.springframework.web.util.UriComponentsBuilder
import java.math.BigInteger
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

typealias WithProviderMetadataCallback<T> = suspend (providerMetadata: ProviderMetadata) -> T

class DefaultSiwaOrchestrationService(
  private val properties: DefaultSiwaOrchestrationProperties,
  private val databaseService: CustodialWalletDatabaseService,
  private val siwaEmailHandlingToEmailService: Map<SiwaEmailHandling, EmailService>,
  private val keyService: KeyService,
  private val lookupService: LookupOrchestrationService,
  private val signingOrchestrationService: SigningOrchestrationService,
  private val verifiableCredentialService: VerifiableCredentialService,
  private val notificationServiceClient: NotificationServiceClient,
  private val redisClient: CustodialWalletRedisClient,
  private val graphHelper: GraphHelper,
  private val smsMessageFactory: MessageFactory,
  private val phoneNumberValidator: PhoneNumberValidator,
  private val blockingStrategy: BlockingStrategy,
  private val hostName: String,
  private val chainReference: String,
  private val caip122MessageFactory: MessageFactory,
  private val captchaClient: CaptchaClient,
  private val providerAdminSharedSecret: String,
  private val passkeyWalletService: PasskeyWalletService,
  private val normalizationUtil: NormalizationUtil,
  private val whitelistChecker: WhitelistChecker,
  private val delegatingTransactionalOperator: DelegatingTransactionalOperator,
  private val developerTermsCopy: String,
  private val icsWhitelistService: IcsWhitelistService,
  private val frequencyService: FrequencyService
) : SiwaOrchestrationService {

  companion object {
    const val START_TEMPLATE = "siwa/start"
    const val MAGIC_LINK_SENT_TEMPLATE = "siwa/emailSent"
    const val OTP_TEMPLATE = "siwa/smsSent"
    const val EMAIL_LOGIN_TEMPLATE = "siwa/emailLogin"
    const val PAYLOADS_TEMPLATE = "siwa/payloads"
    const val SUBMISSION_IN_PROGRESS_TEMPLATE = "siwa/submissionInProgress"
    const val CREATE_WALLET_TEMPLATE = "siwa/createWallet"

    private val LOG: Logger = LoggerFactory.getLogger(DefaultSiwaOrchestrationService::class.java)

    private fun checkPermissionAreValid(
      schemaIds: Collection<Int>,
      validSchemaIdSets: Iterable<Set<Int>>,
    ): Boolean {
      val schemaIdsAsSet = schemaIds.toSet()

      validSchemaIdSets.forEach { validSet ->
        val intersection = validSet.intersect(schemaIdsAsSet)
        if (intersection.isNotEmpty() && intersection != validSet) {
          // When some--but not all--of a valid set are found in the given schema IDs, those schema IDs are invalid
          return false
        }
      }
      return true
    }

    fun createSponsoredAccountInBackgroundCoroutine(
      frequencyService: FrequencyService,
      lookupService: LookupOrchestrationService,
      redisClient: CustodialWalletRedisClient,
      userAccountKeyPair: AccountKeyPair,
      existingUserMsaId: BigInteger?,
      handleToClaim: String?,
      sessionId: String,
      asyncSubmissionId: String,
      onFinished: () -> Unit = {},
    ) {
      //Using fresh job to sever the relationship to the parent https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html#children-of-a-coroutine
      CoroutineScope(Job()).launch(Dispatchers.IO) {
        try {
          LOG.debug("####################### START TALKING TO CHAIN ####################")
          val userMsaId = when (existingUserMsaId) {
            null -> {
              frequencyService.createUserAccount(userAccountKeyPair).getOrElse {
                throw ApiException(ApiError.BLOCKCHAIN_EXTRINSIC_ERROR, "Failed to create user account")
              }
            }

            else -> existingUserMsaId
          }

          if (handleToClaim != null) {
            val userHasHandle = lookupService.getHandle(userMsaId).baseHandle.isNotEmpty()
            if (!userHasHandle) {
              frequencyService.claimHandle(userAccountKeyPair, handleToClaim).getOrElse {
                throw ApiException(ApiError.BLOCKCHAIN_EXTRINSIC_ERROR, "Failed to claim handle")
              }
            }
          }
          LOG.debug("####################### END TALKING TO CHAIN ####################")

          val submission = AsyncSubmission<Unit>(asyncSubmissionId, SubmissionStatus.SUCCESS)
          redisClient.saveAsyncSubmission(submission)
        } catch (x: Exception) {
          LOG.error(
            "When asynchronously dispatching to chain an exception was received for userMsaId=$existingUserMsaId and sessionId=$sessionId",
            x
          )

          val submission = AsyncSubmission<Unit>(asyncSubmissionId, SubmissionStatus.FAILED)
          redisClient.saveAsyncSubmission(submission)
        }

        onFinished()
      }
    }
  }

  /**
   * This method is used to aggregate the callback uri data and the incoming query params into one UriComponents object
   * which can be interrogated rather than having to do things on strings directly
   */
  private fun concatenateAndParseCallbackUrlParams(
    callback: String,
    additionalUrlParams: MultiValueMap<String, String>?
  ): UriComponents {
    val callbackUriComponents = UriComponentsBuilder.fromUriString(callback).build()
    val baseCallbackUrl = callbackUriComponents.toUriString()

    val filteredQueryParams = MultiValueMap.fromMultiValue(
      (additionalUrlParams ?: emptyMap()).filterKeys { it != SIGNED_REQUEST_PARAMETER_NAME }
    )

    return UriComponentsBuilder.fromUriString(baseCallbackUrl).queryParams(filteredQueryParams).build()
  }

  private suspend fun createSiwaSessionForRequest(
    request: SiwaRequest,
    queryParams: MultiValueMap<String, String>?,
    sessionId: String?,
    providerMetadata: ProviderMetadata
  ): SiwaSession {
    val signatureRequest = request.signatureRequest
    val payload = signatureRequest.payload

    /*
     * Get the canonical version of the callback and make sure AUTHORIZATION_CODE_PARAMETER_NAME wasn't in the original
     * payload or added as echoed parameter in the HTTP request
     */
    val fullCallbackUriComponents = concatenateAndParseCallbackUrlParams(signatureRequest.payload.callback, queryParams)
    val authorizationCodes = fullCallbackUriComponents.queryParams[AUTHORIZATION_CODE_PARAMETER_NAME]
    if (!authorizationCodes.isNullOrEmpty()) {
      throw ApiException(
        ApiError.RESERVED_PARAMETER_VIOLATION,
        "The SignedSiwaRequest contains the parameter $AUTHORIZATION_CODE_PARAMETER_NAME which is illegal"
      )
    }

    val signedOverPayload = signatureRequest.payload
    val siwaSignatureRequestFrequencyPayload =
      io.amplica.custodial_wallet.orchestration.payload.SiwaSignatureRequest(
        signedOverPayload.callback,
        signedOverPayload.permissions,
        signedOverPayload.userIdentifierAdminUrl
      )
    if (!signingOrchestrationService.verifySignedPayload(
        signatureRequest.publicKey,
        siwaSignatureRequestFrequencyPayload,
        signatureRequest.signature
      )
    ) {
      throw ApiException(
        ApiError.INVALID_SIGNATURE,
        "The SignedSiwaRequest contains an invalid signature"
      )
    }

    val flowVariantParam = queryParams?.getFirst(SIWA_FLOW_VARIANT_PARAMETER_NAME)
    val flow = when (flowVariantParam?.lowercase()) {
      null, "social" -> SiwaFlowKind.SOCIAL

      "ics" -> {
        // Validate that the provider is whitelisted for the ICS use-case
        val providerMsaId = lookupService.retrieveMsaId(signatureRequest.publicKey)
        if (!icsWhitelistService.providerIsWhitelisted(providerMsaId)) {
          throw ApiException(
            ApiError.MSA_ID_NOT_ON_WHITELIST_ERROR,
            "The provider (msaID=$providerMsaId) is not authorized to use the ICS flow"
          )
        }

        SiwaFlowKind.ICS
      }

      else -> throw ApiException(
        ApiError.SIWA_INVALID_REQUEST,
        "Unrecognized flow variant: '$flowVariantParam'"
      )
    }

    val permissionsAreInvalid = when (flow) {
      SiwaFlowKind.SOCIAL -> {
        val socialPermissionsAreValid = checkPermissionAreValid(
          payload.permissions,
          properties.schemaIdsToPermissionMessageKeys.keys
        )

        payload.permissions.isEmpty() || !socialPermissionsAreValid
      }

      // Providers may not request any permission delegation for ICS users
      SiwaFlowKind.ICS -> !payload.permissions.isEmpty()
    }

    if (permissionsAreInvalid) {
      throw ApiException(
        ApiError.SIWA_INVALID_REQUEST,
        "The provided permissions are not valid"
      )
    }

    // Ensure the provider is whitelisted and any URLs are whitelisted by the provider (or provider application)
    lookupService.verifyUrlWhitelistedByProviderMetadata(URI(payload.callback), providerMetadata)
    payload.userIdentifierAdminUrl?.let { url ->
      lookupService.verifyUrlWhitelistedByProviderMetadata(URI(url), providerMetadata)
    }

    val existingSiwaSession = sessionId?.let { sessionId -> lookupService.findSiwaSession(sessionId) }

    val prefillEmail = queryParams?.getFirst(PREFILL_EMAIL_PARAMETER_NAME)
    var prefillPhoneNumber = queryParams?.getFirst(PREFILL_PHONE_NUMBER_PARAMETER_NAME)
    val userIdentifier = if (prefillEmail != null) {
      UserIdentifier(prefillEmail, UserIdentifierType.EMAIL)
    } else if (prefillPhoneNumber != null) {
      prefillPhoneNumber = prefillPhoneNumber.removePrefix(" ")
      if (!prefillPhoneNumber.startsWith("+")) {
        prefillPhoneNumber = "+$prefillPhoneNumber"
      }
      UserIdentifier(prefillPhoneNumber, UserIdentifierType.PHONE_NUMBER)
    } else {
      null
    }
    val prefillUserHandle = queryParams?.getFirst(PREFILL_USER_HANDLE_PARAMETER_NAME)
    val createNewUnauthenticatedSiwaSession =
      {
        UnauthenticatedSiwaSession(
          request,
          fullCallbackUrl = fullCallbackUriComponents.toUriString(),
          userIdentifier = userIdentifier,
          prefillUserHandle = prefillUserHandle,
          flowKind = flow,
          userKeyPairType = determineUserKeyPairType(
            signatureRequest.publicKey.type,
            queryParams?.getFirst(USER_KEY_TYPE)
          )
        )
      }

    // Update any authenticated session, otherwise create a new unauthenticated session
    val siwaSession = existingSiwaSession?.fold(
      { createNewUnauthenticatedSiwaSession.invoke() },
      {
        it.copy(
          siwaRequest = request,
          fullCallbackUrl = fullCallbackUriComponents.toUriString(),
          prefillUserHandle = prefillUserHandle,
          flowKind = flow,
        )
      }
    )
      ?: createNewUnauthenticatedSiwaSession.invoke()
    redisClient.saveSiwaSession(siwaSession)

    return siwaSession
  }

  private suspend fun <T> withProviderMetadata(
    providerPublicKey: PublicKeyDto,
    applicationContext: ApplicationContext?,
    withProviderMetadataCallback: WithProviderMetadataCallback<T>
  ): T {
    val providerMetadata = getSiwaProviderMetadataOrThrow(providerPublicKey, applicationContext)
    return ContextLoggerHelper.putContext(mapOf(LoggingAttributes.PROVIDER_NAME to providerMetadata.displayName)) {
      withProviderMetadataCallback(providerMetadata)
    }
  }

  // Helper method that throws a generic `SIWA_SESSION_NOT_FOUND` exception
  private suspend fun authenticateSiwaSession(
    sessionId: String,
    authenticationCode: String?,
  ): AuthenticatedSiwaSession {
    val mutableLoggingContext = mutableMapOf("sessionId" to sessionId)
    if (authenticationCode != null) {
      mutableLoggingContext["authenticationCode"] = authenticationCode
    }

    val siwaSession = lookupService.findSiwaSession(sessionId) ?: throw ApiException(
      ApiError.SIWA_SESSION_NOT_FOUND,
      "No SIWA session found for the given session ID and authentication code",
      mutableLoggingContext
    )

    // Ensure the session includes the properties needed for authentication
    val userIdentifier = siwaSession.userIdentifier ?: throw ApiException(
      ApiError.SIWA_INVALID_STATE, "The given session does not have a user identifier defined", mutableLoggingContext
    )
    val authenticatedSiwaSession: AuthenticatedSiwaSession = siwaSession.fold({ unauthenticatedSiwaSession ->
      val authentication = unauthenticatedSiwaSession.authentication ?: throw ApiException(
        ApiError.SIWA_INVALID_STATE,
        "The given session has not yet generated a verification code for authentication",
        mutableLoggingContext
      )

      if (authentication.incorrectAttemptCount >= properties.authentication.incorrectAttemptLimit) {
        throw ApiException(
          ApiError.INCORRECT_TOKEN_RETRY_LIMIT_EXCEEDED,
          "The wrong authentication code has been entered too many times",
          mutableLoggingContext
        )
      }

      if (authenticationCode != authentication.currentCode) {
        // Increment the incorrect attempt count in redis
        val updatedAuthentication =
          authentication.copy(incorrectAttemptCount = authentication.incorrectAttemptCount + 1)
        redisClient.saveSiwaSession(unauthenticatedSiwaSession.copy(authentication = updatedAuthentication))

        throw ApiException(
          ApiError.SIWA_SESSION_NOT_FOUND_FOR_TOKEN,
          "No SIWA session found for the given session ID and authentication code",
          mutableLoggingContext
        )
      }

      // Destroy the unauthenticated session
      redisClient.deleteSiwaSessionBySessionId(sessionId)

      // Create a new authenticated session
      val authenticatedSession = AuthenticatedSiwaSession(
        unauthenticatedSiwaSession.siwaRequest,
        userIdentifier = userIdentifier,
        fullCallbackUrl = unauthenticatedSiwaSession.fullCallbackUrl,
        prefillUserHandle = unauthenticatedSiwaSession.prefillUserHandle,
        flowKind = unauthenticatedSiwaSession.flowKind,
        userKeyPairType = unauthenticatedSiwaSession.userKeyPairType,
      )

      LOG.info(
        "unauthenticatedSession.id={} is now authenticatedSession.id={}",
        unauthenticatedSiwaSession.id,
        authenticatedSession.id
      )

      authenticatedSession
    }, {
      LOG.info("continuing on with existing authenticatedSession.id={}", it.id)
      it
    })

    redisClient.saveSiwaSession(authenticatedSiwaSession)

    return authenticatedSiwaSession
  }

  private suspend fun sendEmail(
    userIdentifierValue: String,
    uri: URI,
    token: String,
    sessionId: String,
    locale: Locale,
    providerMsaId: BigInteger,
    providerMetadata: ProviderMetadata,
    overrideBlockingSecret: String?,
    shouldUseLoginEmailTemplate: Boolean,
    siwaEmailHandling: SiwaEmailHandling
  ) {
    if (overrideBlockingSecret != providerAdminSharedSecret) {
      blockingStrategy.checkOrThrow(providerMsaId, sessionId)
    }

    val emailService = siwaEmailHandlingToEmailService[siwaEmailHandling]
      ?: throw IllegalArgumentException("SiwaEmailHandling=${siwaEmailHandling} is not recognized and has no EmailService handler")

    LOG.info("Attempting to send email to {}", userIdentifierValue)
    if (shouldUseLoginEmailTemplate) {
      emailService.sendLoginEmail(
        userIdentifierValue,
        uri,
        token,
        sessionId,
        locale,
        providerMetadata
      )
    } else {
      emailService.sendSignUpEmail(
        userIdentifierValue,
        uri,
        token,
        sessionId,
        locale,
        providerMetadata
      )
    }
    LOG.info("Sent email to {}", userIdentifierValue)
  }

  private suspend fun sendSms(
    userIdentifier: UserIdentifier,
    token: String,
    sessionId: String,
    locale: Locale,
    providerMsaId: BigInteger,
    providerMetadata: ProviderMetadata,
    overrideBlockingSecret: String?,
    shouldUseLoginTemplate: Boolean,
    userIp: String?
  ) {
    if (overrideBlockingSecret != providerAdminSharedSecret) {
      blockingStrategy.checkOrThrow(providerMsaId, sessionId)
    }
    val template = when (shouldUseLoginTemplate) {
      true -> properties.sms.loginTemplateName
      false -> properties.sms.signUpTemplateName
    }
    val smsMessage = smsMessageFactory.createMessage(
      template,
      mapOf(Pair("token", token)),
      providerMetadata.shortcode,
      locale,
    )
    // NOTE(Teddy, 02-20-25): The following sets verifiedMillis to the current time if a user account exists, insuring
    // that notification service blocking logic is ignored for existing user accounts
    val verifiedMillis = if (!shouldUseLoginTemplate) {
      Instant.now().toEpochMilli().toBigInteger()
    } else {
      lookupService.getVerifiedMillisForPhone(userIdentifier)
    }
    val sendSmsRequest = SendSmsRequest(userIdentifier.value, smsMessage, verifiedMillis, userIp)

    val destinationPhoneNumber = sendSmsRequest.destinationPhoneNumber
    LOG.info("Attempting to call the NotificationService for {}", destinationPhoneNumber)
    ContextLoggerHelper.withMdcContext { mdcContextSessionId, xSessionId, xTraceId ->
      notificationServiceClient.sendSms(sendSmsRequest, mdcContextSessionId, xSessionId, xTraceId)
    }
    LOG.info("NotificationService called for {}", destinationPhoneNumber)
  }

  private suspend fun getLastVerified(userIdentifier: UserIdentifier): ZonedDateTime {
    val userIdentifierRecord = databaseService.findUserIdentifier(
      mapUserIdentifierToUserDetail(userIdentifier)
    ) ?: throw NullPointerException("'findUserIdentifier' returned null for $userIdentifier")

    val lastVerifiedEpochMillis = userIdentifierRecord.verifiedDate ?: userIdentifierRecord.createdAt
    return ZonedDateTime.ofInstant(
      Instant.ofEpochMilli(lastVerifiedEpochMillis.toLong()),
      ZoneId.of("UTC"),
    )
  }

  /**
   * Finds the most appropriate `UserKeyData` (see [findAccountUserKeyData] for details) and then decrypts the keypair.
   *
   * If there is no [KeyUsageType.ACCOUNT] [UserKeyData] associated with the given user account ID,
   * an [ApiException] is thrown.
   */
  private suspend fun findAccountKeyPair(
    userAccountId: BigInteger,
    preferredKeyPairType: KeyPairType,
  ): KeyPairBytes {
    val userKeyData = lookupService.findUserKeyDataOrThrow(userAccountId, KeyUsageType.ACCOUNT, preferredKeyPairType)

    return keyService.decryptUserAccountKeyData(userKeyData)
  }

  private suspend fun findGraphKeyPair(userAccountId: BigInteger): X25519KeyPair? {
    return lookupService.findUserKeyData(userAccountId, KeyUsageType.GRAPH, KeyPairType.X25519)?.let {
      keyService.decryptUserGraphKeyData(it)
    }
  }

  private suspend fun determineSiwaIntentForSocialUser(
    userIdentifier: UserIdentifier,
    signatureRequest: SignedSiwaSignatureRequest,
    userKeyPairType: KeyPairType,
  ): SiwaIntent {
    val providerMsaId = lookupService.retrieveMsaId(signatureRequest.publicKey)
    val requestedPermissions = signatureRequest.payload.permissions
    val graphKeyIsRequired = graphHelper.containsPrivateGraphSchemas(requestedPermissions)

    val userAccount = lookupService.findUserAccountByUserIdentifier(userIdentifier)
    val accountKeyPair =
      userAccount?.let { existingUserAccount -> findAccountKeyPair(existingUserAccount.id!!, userKeyPairType) }

    val userMsaId = accountKeyPair?.let {
      lookupService.getMsaIdByPublicKey(keyPairBytesToUniversalAddress(it))
    }
    val userExistsOnChain = userMsaId != null

    if (userAccount == null || accountKeyPair == null || !userExistsOnChain) {
      return SiwaIntent.UpdateBlockchain(
        listOfNotNull(
          SiwaBlockchainOperation.CreateAccountAndDelegatePermissions(providerMsaId, requestedPermissions),
          SiwaBlockchainOperation.ClaimHandle,
          if (graphKeyIsRequired) SiwaBlockchainOperation.RegisterPrivateGraphKey else null
        ),
        graphKeyIsRequired,
      )
    }

    val graphKeyExistsOnChain = findGraphKeyPair(userAccount.id!!)?.let { graphKeyPair ->
      userMsaId?.let { msaId ->
        val graphKeysOnChain = lookupService.getGraphKeysRegisteredOnChainForUser(
          msaId,
          mapOf(
            "userIdentifier" to userIdentifier.value,
            "userAccountId" to userAccount.id!!,
            "userMsaId" to userMsaId,
            "providerMsaId" to providerMsaId,
          )
        )
        graphKeysOnChain.any { keyOnChain ->
          keyOnChain.contentEquals(graphKeyPair.publicKeyBytes)
        }
      }
    } ?: false

    val universalAddress = keyPairBytesToUniversalAddress(accountKeyPair)
    val existingPermissions = lookupService.getGrantedSchemasByPublicKey(universalAddress, providerMsaId)
    val missingPermissions = requestedPermissions - existingPermissions.toSet()

    return when {
      missingPermissions.isEmpty() -> {
        // Sanity check that the state of the chain is coherent before allowing login
        // (e.g., if private graph permissions are delegated, then the graph key registered)
        if (graphKeyIsRequired && !graphKeyExistsOnChain) {
          throw IllegalStateException(
            "User has delegated graph permissions without registering a graph key "
                    + "(userAccountId=${userAccount.id}, providerMsaId=$providerMsaId)"
          )
        }

        SiwaIntent.Login(userAccount.id!!, graphKeyIsRequired)
      }

      else -> SiwaIntent.UpdateBlockchain(
        listOfNotNull(
          // NOTE(Julian, 2024-08-12): Existing users (even those without a handle) are never prompted to claim a handle.
          SiwaBlockchainOperation.DelegatePermissions(providerMsaId, missingPermissions),
          if (graphKeyIsRequired && !graphKeyExistsOnChain) SiwaBlockchainOperation.RegisterPrivateGraphKey else null
        ),
        graphKeyIsRequired,
      )
    }
  }

  private suspend fun determineSiwaIntentForIcsUser(
    userIdentifier: UserIdentifier,
    userKeyPairType: KeyPairType,
  ): SiwaIntent {
    val userAccount = lookupService.findUserAccountByUserIdentifier(userIdentifier)
    val accountKeyPair = userAccount?.let { existingUserAccount ->
      findAccountKeyPair(existingUserAccount.id!!, userKeyPairType)
    }

    val userMsaId = accountKeyPair?.let {
      lookupService.getMsaIdByPublicKey(keyPairBytesToUniversalAddress(it))
    }
    val userExistsOnChain = userMsaId != null

    // NOTE(Julian, 2026-01-22): If we want to create handles for ICS users in the future we can add that
    // logic here and the rest of the flow should respond accordingly.
    val userNeedsToClaimHandle = false

    if (userAccount == null || !userExistsOnChain || userNeedsToClaimHandle) {
      return SiwaIntent.CreateSponsoredAccountAndLogin(userNeedsToClaimHandle, false)
    }

    return SiwaIntent.Login(userAccount.id!!, false)
  }

  private fun createCaip122LoginPayloadResponse(
    siwaSignatureRequest: SiwaSignatureRequest,
    userKeyPair: KeyPairBytes,
    providerMetadata: ProviderMetadata,
  ): TypedPayloadResponseWithSignature<Caip122LoginPayloadResponse> {
    val uri = URI.create(siwaSignatureRequest.callback)
    val domain = uri.host
    val version = 1
    val serializedUserAddress = when (userKeyPair.keyPairType) {
      KeyPairType.SR25519 -> {
        Sr25519KeyPairCreator.encodeSr25519PublicKey(
          userKeyPair.publicKeyBytes,
          properties.ss58AddressFormat
        )
      }

      KeyPairType.SECP256K1 -> toHex(Secp256K1CryptoProvider.toUniversalAddress(userKeyPair.publicKeyBytes.toPublicKeyBytes()))
      else -> throw IllegalArgumentException("Unsupported KeyPairType ${userKeyPair.keyPairType}")
    }
    val message = caip122MessageFactory.createMessage(
      "caip122",
      mapOf(
        "domain" to domain,
        "chainReference" to chainReference,
        "userAddress" to serializedUserAddress,
        "uri" to uri.toString(),
        "version" to version,
        "nonce" to generateUUID(),
        "issuedAt" to toIso8601Format(ZonedDateTime.now()),
      ),
      providerMetadata.shortcode,
      locale = null, // NOTE(Julian, 2024-09-03): We only have an english version of the template at the moment
    )
    val caip122LoginPayload = Caip122LoginPayloadResponse(message)
    val signature = signingOrchestrationService.signMessage(userKeyPair, caip122LoginPayload.message)
    return TypedPayloadResponseWithSignature(signature, null, null, PayloadType.LOGIN, caip122LoginPayload)
  }

  override suspend fun createNewUserAccountAndKeyPairs(
    userIdentifier: UserIdentifier,
    providerMsaId: BigInteger,
    shouldGenerateGraphKey: Boolean,
    userKeyPairType: KeyPairType,
  ): BigInteger {
    val generatedKeys = keyService.generateAccountAndGraphKeyPairs(userKeyPairType, shouldGenerateGraphKey)
    val encryptedKeyData = listOfNotNull(
      generatedKeys.account.encryptedKeyData,
      generatedKeys.graph?.encryptedKeyData
    )

    val userDetail = mapUserIdentifierToUserDetail(userIdentifier)

    // NOTE(Julian, 2024-08-06): `providerExternalId` is not defined for SIWA flows (and will be deprecated
    // eventually) so we use the user's public key hex as a stand in.
    // NOTE(Julian, 2025-07-15): DB column is only 128 chars wide
    val userPublicKeyHex = StringUtils.left(
      normalizeToHex(generatedKeys.account.publicKey),
      128,
    )
    databaseService.saveNewUserData(providerMsaId, userPublicKeyHex, listOf(userDetail), encryptedKeyData)

    val userAccount = databaseService.findUserAccountByUserIdentifier(userDetail)!!

    return userAccount.id!!
  }

  /**
   * Creates new `provider_external_user` and `-_detail` rows linking the existing `user_account` to the new provider
   * MSA ID.
   *
   * TODO this needs to be refactored to hide these gory details in teh DB Service, further this is using methods that
   * I created to just get around shortcomings of what is there, ReactiveCustodialWalletDatabaseService.saveUserIdentifierAndProviderExternalUserDetail
   * needs to not assume it's creating a new ProviderExternalUserDetail every time. it should see if one exists like
   * it does for the UserIdentifier
   *
   */
  private suspend fun linkExistingUserAccountToNewProvider(
    userAccountId: BigInteger,
    userIdentifier: UserIdentifier,
    providerMsaId: BigInteger,
    preferredUserKeyPairType: KeyPairType,
  ) {
    val userAccountKeyData = lookupService.findUserKeyDataOrThrow(
      userAccountId,
      KeyUsageType.ACCOUNT,
      preferredUserKeyPairType,
    )
    var providerExternalUser =
      databaseService.findProviderExternalUserByProviderMsaIdAndKeyPairTypeAndPublicKeyHexAndKeyUsageType(
        providerMsaId,
        userAccountKeyData.encryptedPrivateKeyType,
        userAccountKeyData.publicKeyHex,
        KeyUsageType.ACCOUNT
      )
    if (providerExternalUser == null) {
      providerExternalUser = databaseService.saveProviderExternalUser(
        ProviderExternalUser.create(
          providerMsaId,
          // NOTE(Julian, 2024-08-06): `providerExternalId` is not defined for SIWA flows (and will be deprecated
          // eventually) so we use the user's public key hex as a stand in.
          // NOTE(Julian, 2025-07-15): DB column is only 128 chars wide
          providerExternalId = StringUtils.left(userAccountKeyData.publicKeyHex, 128),
          userAccountKeyData.id!!,
        )
      )
    } else {
      providerExternalUser.lastModified = Instant.now().toEpochMilli().toBigInteger()
      providerExternalUser = databaseService.saveProviderExternalUser(providerExternalUser)
    }

    val value = userIdentifier.value
    val userDetailType = UserDetailType.fromUserIdentifierType(userIdentifier.type)
    val userDetail = UserDetail(value, userDetailType)
    var providerExternalUserDetail =
      databaseService.findProviderExternalUserDetailByUserDetailValueAndUserDetailTypeAndProviderExternalIdAndProviderMsaId(
        value,
        userDetail.type,
        providerExternalUser.providerExternalId,
        providerMsaId
      )
    val dbUserIdentifier = databaseService.findUserIdentifier(userDetail)
      ?: throw ApiException(
        ApiError.NO_DB_USER_IDENTIFIER_FOUND,
        "Could not find the database UserIdentifier for $userDetail"
      )
    if (providerExternalUserDetail == null) {
      providerExternalUserDetail = ProviderExternalUserDetail.create(
        providerExternalUser.id!!, userAccountId, userDetail,
        dbUserIdentifier.id!!
      )
    } else {
      providerExternalUserDetail.lastModified = Instant.now().toEpochMilli().toBigInteger()
    }

    databaseService.saveProviderExternalUserDetail(providerExternalUserDetail)
  }

  private suspend fun createSignedClaimHandlePayload(
    handle: String,
    userKeyPair: KeyPairBytes
  ): TypedPayloadResponseWithSignature<HandlePayloadResponse> {
    val expirationBlockNumber = lookupService.retrieveCurrentBlockNumber() + properties.signupBlockExpiration
    val payload = CreateHandlePayload(handle, expirationBlockNumber)
    val signature = signingOrchestrationService.signPayload(userKeyPair, payload)

    return TypedPayloadResponseWithSignature(
      signature,
      FrequencyEndpoint.Handles.claimHandle,
      null,
      PayloadType.CLAIM_HANDLE,
      HandlePayloadResponse(handle, expirationBlockNumber),
    )
  }

  private suspend fun createSignedAddGraphKeyPayload(
    userKeyPair: KeyPairBytes,
    graphKeyPair: X25519KeyPair,
  ): TypedPayloadResponseWithSignature<ItemizedSignaturePayloadResponse> {
    val expirationBlockNumber = lookupService.retrieveCurrentBlockNumber() + properties.signupBlockExpiration
    val encodedGraphPublicKey = graphHelper.convertToDsnpPublicKey(graphKeyPair.publicKeyBytes)

    val payload = AddGraphKeyPayload(
      encodedGraphPublicKey,
      graphHelper.getGraphKeySchemaId(),
      graphHelper.getDefaultPageHash(),
      expirationBlockNumber,
    )
    val signature = signingOrchestrationService.signPayload(userKeyPair, payload)

    return TypedPayloadResponseWithSignature(
      signature,
      FrequencyEndpoint.StatefulStorage.applyItemActionsWithSignatureV2,
      null,
      PayloadType.ITEM_ACTIONS,
      ItemizedSignaturePayloadResponse(
        graphHelper.getGraphKeySchemaId(),
        graphHelper.getDefaultPageHash(),
        expirationBlockNumber,
        listOf(AddItemAction(toHex(encodedGraphPublicKey))),
      )
    )
  }

  private suspend fun createSignedAddProviderPayload(
    userKeyPair: KeyPairBytes,
    providerMsaId: BigInteger,
    permissions: List<Int>,
    createAccount: Boolean
  ): TypedPayloadResponseWithSignature<AddProviderPayloadResponse> {
    val expirationBlockNumber = lookupService.retrieveCurrentBlockNumber() + properties.signupBlockExpiration

    val payload = AddProviderPayload(providerMsaId, permissions, expirationBlockNumber)
    val signature = signingOrchestrationService.signPayload(userKeyPair, payload)

    // NOTE(Julian, 2024-08-27): Creating a sponsored account with delegation has the exact same payload as purely
    // granting delegations, so we need to specify the endpoint explicitly for providers.
    val endpoint = when (createAccount) {
      true -> FrequencyEndpoint.Msa.createSponsoredAccountWithDelegation
      false -> FrequencyEndpoint.Msa.grantDelegation
    }

    return TypedPayloadResponseWithSignature(
      signature,
      endpoint,
      null,
      PayloadType.ADD_PROVIDER,
      AddProviderPayloadResponse(providerMsaId, permissions, expirationBlockNumber),
    )
  }

  private suspend fun createSignedPayloads(
    userKeyPair: KeyPairBytes,
    graphKeyPair: X25519KeyPair?,
    operations: List<SiwaBlockchainOperation>,
    userInput: SiwaPayloadsUserInput,
    loggingContext: Map<String, Any>
  ): List<TypedPayloadResponseWithSignature<out PayloadResponse>> {
    return operations.map { op ->
      when (op) {
        is SiwaBlockchainOperation.CreateAccountAndDelegatePermissions -> {
          createSignedAddProviderPayload(
            userKeyPair,
            op.providerMsaId,
            op.schemaIds,
            createAccount = true
          )
        }

        is SiwaBlockchainOperation.DelegatePermissions -> {
          createSignedAddProviderPayload(
            userKeyPair,
            op.providerMsaId,
            op.schemaIds,
            createAccount = false
          )
        }

        SiwaBlockchainOperation.ClaimHandle -> {
          val handle = userInput.handle ?: throw ApiException(
            ApiError.SIWA_INVALID_REQUEST,
            "UserPayloadsAcceptanceAndDataCommand is missing the 'handle' property, which is required for new users",
            loggingContext
          )

          createSignedClaimHandlePayload(handle, userKeyPair)
        }

        SiwaBlockchainOperation.RegisterPrivateGraphKey -> {
          if (graphKeyPair == null) {
            throw IllegalArgumentException(
              "'RegisterPrivateGraphKey' operation requires that 'graphKeyPair' not be null"
            )
          }

          createSignedAddGraphKeyPayload(userKeyPair, graphKeyPair)
        }
      }
    }
  }

  private suspend fun createSiwaPayloadResponse(
    session: AuthenticatedSiwaSession,
    userKeyPair: KeyPairBytes,
    userMsaId: BigInteger?,
    graphKeyPair: X25519KeyPair?,
    intent: SiwaIntent,
    payloads: List<TypedPayloadResponseWithSignature<out PayloadResponse>>,
    developerTermsCopy: String,
    developerTermsLink: URI
  ): SiwaPayloadResponse {
    val userPublicKey = userKeyPair.toPublicKeyDto(properties.ss58AddressFormat)

    val userKeys = graphKeyPair?.let {
      listOf(createFrequencyKeyPairDto(it))
    } ?: emptyList()

    val userIdentifierLastVerified = getLastVerified(session.userIdentifier)
    val userIdentifierCredential = verifiableCredentialService.createVerifiableCredential(
      userKeyPair,
      session.userIdentifier,
      userIdentifierLastVerified
    )

    val frequencyGraphKeyCredential = if (intent.sendGraphKeyPair) {
      if (graphKeyPair == null) {
        throw IllegalStateException(
          "Graph key pair is not defined while processing a SIWA request that requires the graph key pair"
        )
      }
      verifiableCredentialService.createVerifiableCredential(userKeyPair, graphKeyPair)
    } else null

    val credentials = listOfNotNull(userIdentifierCredential, frequencyGraphKeyCredential)

    return SiwaPayloadResponse(
      userPublicKey,
      userMsaId,
      userKeys,
      payloads,
      credentials,
      developerTermsCopy,
      developerTermsLink,
    )
  }

  private suspend fun createPayloadsPropsForSocial(
    operations: List<SiwaBlockchainOperation>,
    session: AuthenticatedSiwaSession,
    globalApiError: GlobalApiError?
  ): PayloadsProps {
    val shouldPromptUserForHandle = operations.any { it is SiwaBlockchainOperation.ClaimHandle }

    val identifier = session.userIdentifier.value

    val providerMetadata = getSiwaProviderMetadataOrThrow(
      session.siwaRequest.signatureRequest.publicKey,
      session.siwaRequest.applicationContext
    )
    val providerName = providerMetadata.displayName

    val schemaIds = operations.flatMap { op ->
      when (op) {
        is SiwaBlockchainOperation.CreateAccountAndDelegatePermissions -> op.schemaIds
        is SiwaBlockchainOperation.DelegatePermissions -> op.schemaIds
        else -> emptyList()
      }
    }

    // Gather the front-end message keys necessary to communicate to the user the permissions requested by the provider.
    // NOTE: This function will explode if an invalid combination of schema IDs are present.
    val permissionMessageKeys = getPermissionKeysForSchemaIds(
      schemaIds,
      properties.schemaIdsToPermissionMessageKeys,
      null,
    )

    val contextMap: Map<String, String> = mapOf(Pair(LoggingAttributes.PROVIDER_NAME, providerMetadata.displayName))

    return ContextLoggerHelper.putContext(contextMap) {
      PayloadsProps(
        shouldPromptUserForHandle,
        identifier,
        providerName,
        permissionMessageKeys,
        session.prefillUserHandle,
        globalApiError,
        false
      )
    }
  }

  private suspend fun createPayloadsPropsForIcs(
    icsIntent: SiwaIntent.CreateSponsoredAccountAndLogin,
    session: AuthenticatedSiwaSession,
    globalApiError: GlobalApiError?
  ): PayloadsProps {
    val providerMetadata = getSiwaProviderMetadataOrThrow(
      session.siwaRequest.signatureRequest.publicKey,
      session.siwaRequest.applicationContext
    )
    val providerName = providerMetadata.displayName

    return PayloadsProps(
      icsIntent.claimHandle,
      session.userIdentifier.value,
      providerName,
      emptyList(),
      session.prefillUserHandle,
      globalApiError,
      // NOTE(Julian, 2026-01-21): Corbin / legal do not have any ICS-specific copy for us yet
      false
    )
  }

  private suspend fun saveAuthenticatedSiwaSession(session: AuthenticatedSiwaSession): AuthenticatedSiwaSession {
    val authorizationCode = generateUUID()
    val authenticatedSession = session.copy(authorizationCode = authorizationCode)

    // Update session to include the authorization code and create a secondary index to lookup the session with it
    redisClient.saveSiwaSession(authenticatedSession)
    redisClient.saveSiwaSessionIdByAuthorizationCode(authenticatedSession.id, authorizationCode)
    return authenticatedSession
  }

  private fun createCallbackResponse(session: AuthenticatedSiwaSession): CallbackResponse {
    val authCode = session.authorizationCode ?: throw ApiException(
      ApiError.AUTHENTICATED_SIWA_SESSION_MISSING_AUTHORIZATION_CODE,
      "Tried to use authorization code for callback response but no code was found for sessionid: ${session.id}"
    )
    return CallbackResponse(session.fullCallbackUrl, session.id, authCode)
  }

  private fun createPasskeyWalletResponse(
    session: AuthenticatedSiwaSession,
    response: CallbackResponse
  ): ViewResponse<SiwaProps> {
    val callbackWithAuthorizationAppended = UriComponentsBuilder.fromUriString(response.callbackUrl)
      .queryParam(AUTHORIZATION_CODE_PARAMETER_NAME, response.authorizationCode).encode().build().toUriString()
    val matomoData = createMatomoData(SiwaMatomoDimensions.create(), MatomoPageName.SIWA_CREATE_WALLET)
    return ViewResponse(
      CREATE_WALLET_TEMPLATE,
      CreateWalletProps(callbackWithAuthorizationAppended, null),
      session.id,
      matomoData
    )
  }

  private suspend fun createEmailLoginResponse(
    session: AuthenticatedSiwaSession,
    response: CallbackResponse
  ): ViewResponse<SiwaProps> {
    val callbackWithAuthorizationAppended = UriComponentsBuilder.fromUriString(response.callbackUrl)
      .queryParam(AUTHORIZATION_CODE_PARAMETER_NAME, response.authorizationCode).encode().build().toUriString()
    val providerMetadata = getSiwaProviderMetadataOrThrow(
      session.siwaRequest.signatureRequest.publicKey,
      session.siwaRequest.applicationContext
    )
    val matomoData = createMatomoData(SiwaMatomoDimensions.create(), MatomoPageName.SIWA_EMAIL_LOGIN)
    val contextMap: Map<String, String> = mapOf(Pair(LoggingAttributes.PROVIDER_NAME, providerMetadata.displayName))
    return ContextLoggerHelper.putContext(contextMap) {
      ViewResponse(
        EMAIL_LOGIN_TEMPLATE,
        EmailLoginProps(callbackWithAuthorizationAppended, providerMetadata.displayName),
        session.id,
        matomoData,
      )
    }
  }

  private fun isPhoneNumberEnabled(flowKind: SiwaFlowKind): Boolean {
    return when (flowKind) {
      SiwaFlowKind.ICS -> false
      SiwaFlowKind.SOCIAL -> true
    }
  }

  private suspend fun createStartViewResponse(siwaSession: SiwaSession): ViewResponse<SiwaProps> {
    val providerMetadata = getSiwaProviderMetadataOrThrow(
      siwaSession.siwaRequest.signatureRequest.publicKey,
      siwaSession.siwaRequest.applicationContext
    )
    val matomoDimensions = SiwaMatomoDimensions.create(provider = providerMetadata.displayName)
    val matomoData = createMatomoData(matomoDimensions, MatomoPageName.SIWA_START)
    val contextMap: Map<String, String> = mapOf(Pair(LoggingAttributes.PROVIDER_NAME, providerMetadata.displayName))
    return ContextLoggerHelper.putContext(contextMap) {
      val userIdentifier = siwaSession.userIdentifier
      var prefilledEmail: String? = null
      var prefilledPhoneNumber: String? = null
      if (userIdentifier != null) {
        when (userIdentifier.type) {
          UserIdentifierType.EMAIL -> {
            prefilledEmail = userIdentifier.value
          }

          UserIdentifierType.PHONE_NUMBER -> {
            prefilledPhoneNumber = userIdentifier.value
          }
        }
      }
      ViewResponse(
        START_TEMPLATE,
        StartProps(
          captchaClient.siteKey,
          captchaClient.enabled,
          providerMetadata.displayName,
          isPhoneNumberEnabled(siwaSession.flowKind),
          prefilledEmail,
          prefilledPhoneNumber
        ),
        siwaSession.id,
        matomoData,
      )
    }
  }

  private suspend fun getSiwaProviderMetadata(
    providerMsaId: BigInteger,
    applicationContext: ApplicationContext?
  ): ProviderMetadata? {
    return when (val url = applicationContext?.url) {
      null -> {
        // NOTE(Julian, 2025-01-22): This is the out of date approach that does not account for a provider having
        // multiple applications, but is retained for now to preserve backwards compatibility.
        lookupService.getProviderMetaData(providerMsaId)
      }

      else -> {
        lookupService.getProviderMetadataForApplication(providerMsaId, url)
      }
    }
  }

  private suspend fun getSiwaProviderMetadata(
    providerPublicKey: PublicKeyDto,
    applicationContext: ApplicationContext?
  ): ProviderMetadata? {
    val providerMsaId = lookupService.retrieveMsaId(providerPublicKey)

    return getSiwaProviderMetadata(providerMsaId, applicationContext)
  }

  private suspend fun getSiwaProviderMetadataOrThrow(
    providerMsaId: BigInteger,
    applicationContext: ApplicationContext?
  ): ProviderMetadata {
    return getSiwaProviderMetadata(providerMsaId, applicationContext)
      ?: throw ApiException(
        ApiError.MSA_ID_NOT_ON_WHITELIST_ERROR,
        "providerMsaId=${providerMsaId} is not recognized"
      )
  }

  private suspend fun getSiwaProviderMetadataOrThrow(
    publicKey: PublicKeyDto,
    applicationContext: ApplicationContext?
  ): ProviderMetadata {
    return getSiwaProviderMetadata(publicKey, applicationContext)
      ?: throw ApiException(
        ApiError.MSA_ID_NOT_ON_WHITELIST_ERROR,
        "providerPublicKey=${publicKey} is not recognized"
      )
  }

  private fun createMagicLinkProps(
    userIdentifier: UserIdentifier,
    siwaSession: SiwaSession,
    providerMetadata: ProviderMetadata,
    globalApiError: GlobalApiError? = null
  ): MagicLinkVerificationSentProps {
    return MagicLinkVerificationSentProps(
      userIdentifier.value,
      properties.redisExpiration.toMinutes().toString(),
      providerMetadata.displayName,
      properties.authentication.resendInterval.toMillis(),
      properties.authentication.resendLimit,
      siwaSession.siwaRequest.signatureRequest.payload.userIdentifierAdminUrl,
      globalApiError
    )
  }

  private fun createOtpProps(
    userIdentifier: UserIdentifier,
    siwaSession: SiwaSession,
    providerMetadata: ProviderMetadata,
    globalApiError: GlobalApiError? = null
  ): OtpVerificationSentProps {
    return OtpVerificationSentProps(
      userIdentifier,
      providerMetadata.displayName,
      properties.redisExpiration.toMinutes().toInt(),
      properties.authentication.resendInterval.toMillis(),
      properties.authentication.resendLimit,
      siwaSession.siwaRequest.signatureRequest.payload.userIdentifierAdminUrl,
      globalApiError
    )
  }

  override suspend fun saveSiwaRequest(request: SiwaRequest, sessionId: String?): String {
    return withProviderMetadata(request.signatureRequest.publicKey, request.applicationContext) {
      createSiwaSessionForRequest(request, null, sessionId, it).id
    }
  }

  override suspend fun acceptSiwaRequest(
    request: SiwaRequest,
    queryParams: MultiValueMap<String, String>?,
    sessionId: String?
  ): ViewResponse<SiwaProps> {
    return withProviderMetadata(request.signatureRequest.publicKey, request.applicationContext) {
      val siwaSession = createSiwaSessionForRequest(request, queryParams, sessionId, it)
      createStartViewResponse(siwaSession)
    }
  }

  override suspend fun acceptSavedSiwaRequestBySessionId(sessionId: String): ViewResponse<SiwaProps> {
    val siwaSession = lookupService.findSiwaSessionOrThrow(sessionId)
    return withProviderMetadata(
      siwaSession.siwaRequest.signatureRequest.publicKey,
      siwaSession.siwaRequest.applicationContext
    ) {
      createStartViewResponse(siwaSession)
    }
  }

  private fun returnViewResponseForSentPage(
    userIdentifier: UserIdentifier,
    siwaSession: SiwaSession,
    providerMetadata: ProviderMetadata,
    globalApiError: GlobalApiError?
  ): ViewResponse<SiwaProps> {
    return when (userIdentifier.type) {
      UserIdentifierType.EMAIL -> {
        val emailTemplateNameAndProps =
          when (getSiwaEmailHandlingOrDefault(siwaSession.siwaRequest.siwaEmailHandling)) {
            SiwaEmailHandling.MAGIC_LINK -> {
              val magicLinkProps = createMagicLinkProps(userIdentifier, siwaSession, providerMetadata, globalApiError)
              MAGIC_LINK_SENT_TEMPLATE to magicLinkProps
            }

            SiwaEmailHandling.OTP -> {
              val otpSentProps = createOtpProps(userIdentifier, siwaSession, providerMetadata, globalApiError)
              OTP_TEMPLATE to otpSentProps
            }
          }

        ViewResponse(
          emailTemplateNameAndProps.first,
          emailTemplateNameAndProps.second,
          null
        )
      }

      UserIdentifierType.PHONE_NUMBER -> {
        val otpProps = createOtpProps(userIdentifier, siwaSession, providerMetadata, globalApiError)
        ViewResponse(OTP_TEMPLATE, otpProps, null)
      }
    }
  }

  /**
   * Resolve <code>SiwaSession</code> by the id and UserIdentiier. If the userIdentigier is not the same as in the session it will
   * return a fresh <code>UnauthenticatedSession</code> and clear up the old <code>AuthenticatedSession</code> otheriwse
   * this just returns the session associated with the id
   *
   * @param sessionId
   * @param userIdentifier
   * @return
   */
  private suspend fun resolveSiwaSession(sessionId: String, userIdentifier: UserIdentifier): SiwaSession {
    var siwaSession = lookupService.findSiwaSessionOrThrow(sessionId)
    if (siwaSession is AuthenticatedSiwaSession && siwaSession.userIdentifier != userIdentifier) {
      siwaSession = siwaSession.downgradeSiwaSession()
      redisClient.deleteSiwaSessionBySessionId(sessionId) //new session clean up the old AuthenticatedSession
    }

    return siwaSession
  }

  override suspend fun acceptUserIdentifier(
    sessionId: String,
    siwaIdentifierAndCaptchaToken: SiwaIdentifierAndCaptchaToken,
    userIp: String?,
    overrideBlockingSecret: String?,
    locale: Locale,
    xCaptchaHeaderValue: String?,
  ): SiwaResponse<SiwaProps> {
    val captchaToken = siwaIdentifierAndCaptchaToken.captchaToken
    var userIdentifier = UserIdentifier(siwaIdentifierAndCaptchaToken.value, siwaIdentifierAndCaptchaToken.type)

    val siwaSession = resolveSiwaSession(sessionId, userIdentifier)
    return withProviderMetadata(
      siwaSession.siwaRequest.signatureRequest.publicKey,
      siwaSession.siwaRequest.applicationContext
    ) {
      //For readability, it = provider metadata
      val providerMetadata = it
      val newSessionId: String? = if (sessionId != siwaSession.id) {
        siwaSession.id
      } else {
        null //this is to not reset the cookie TTL in the event it's not a re-auth case because of different identifiers
      }

      val loggingContext = mapOf("sessionId" to siwaSession.id)

      val siwaEmailHandling = getSiwaEmailHandlingOrDefault(siwaSession.siwaRequest.siwaEmailHandling)

      // Validate the identifier value
      when (userIdentifier.type) {
        UserIdentifierType.EMAIL -> {
          if (!EmailValidator.isValid(userIdentifier.value)) {
            return@withProviderMetadata getSiwaErrorStartPage(
              ApiError.INVALID_EMAIL,
              newSessionId,
              providerMetadata.displayName,
              siwaSession.flowKind
            )
          }
          // If we have a plus addressed email, strip it if plus addressing is not permitted
          if (normalizationUtil.isPlusAddressed(userIdentifier.value)) {
            if (!whitelistChecker.plusAddressingPermitted(userIdentifier.value)) {
              val strippedEmail = normalizationUtil.stripPlusAddressing(userIdentifier.value)
              userIdentifier = UserIdentifier(strippedEmail, userIdentifier.type)
            }
          }

        }

        UserIdentifierType.PHONE_NUMBER -> {
          if (!phoneNumberValidator.isValid(userIdentifier.value)) {
            return@withProviderMetadata getSiwaErrorStartPage(
              ApiError.NOT_A_PHONE_NUMBER,
              newSessionId,
              providerMetadata.displayName,
              siwaSession.flowKind
            )
          }
        }
      }

      val emailUri = URI("$hostName/$PAYLOADS_TEMPLATE")
      return@withProviderMetadata siwaSession.fold({ unauthenticatedSiwaSession ->
        val existingVerification = unauthenticatedSiwaSession.authentication
        if (existingVerification != null) {
          val totalSendCount = existingVerification.totalSendCount
          val duration = Duration.between(existingVerification.lastSentAt, Instant.now())
          val resendFreebeesUsed = totalSendCount > properties.authentication.resendFreebee
          val resendRequestedToEarly = duration < properties.authentication.resendInterval
          if (resendFreebeesUsed && resendRequestedToEarly) {
            val globalApiError = GlobalApiError(listOf(ApiError.RESEND_REQUEST_INVALID))
            return@fold returnViewResponseForSentPage(
              userIdentifier,
              unauthenticatedSiwaSession,
              providerMetadata,
              globalApiError
            )
          }

          if (totalSendCount > properties.authentication.resendLimit) {
            throw ApiException(
              ApiError.RESEND_LIMIT_EXCEEDED,
              "Too many resend requests have been made",
              loggingContext
            )
          }
        } else {
          // Check CaptchaStatus. We only do this when there is no existing verification.
          // After we have verified once, we now trust this session and no longer challenge hcaptcha.
          // This is important for resending
          captchaClient.verifyCaptchaStatus(captchaToken, userIp, xCaptchaHeaderValue)
        }

        val authentication = createNewIdentifierVerification(unauthenticatedSiwaSession.authentication?.totalSendCount)

        // Update session to with user identifier and identifier verification data
        redisClient.saveSiwaSession(
          unauthenticatedSiwaSession.copy(
            userIdentifier = userIdentifier,
            authentication = authentication
          )
        )

        val userAccountExists = lookupService.findUserAccountByUserIdentifier(userIdentifier) != null
        val matomoIntent = when (userAccountExists) {
          true -> SiwaMatomoDimensions.IntentType.LOGIN
          false -> SiwaMatomoDimensions.IntentType.SIGNUP
        }
        val matomoDimensions =
          SiwaMatomoDimensions.create(providerMetadata.displayName, matomoIntent, userIdentifier.type)

        // Look up the provider's MSA ID for rate-limiting
        val providerMsaId = lookupService.retrieveMsaId(siwaSession.siwaRequest.signatureRequest.publicKey)

        when (userIdentifier.type) {
          UserIdentifierType.EMAIL -> {
            if (!whitelistChecker.isNoSendEmailAddress(userIdentifier.value)) {
              sendEmail(
                userIdentifier.value,
                emailUri,
                authentication.currentCode,
                siwaSession.id,
                locale,
                providerMsaId,
                providerMetadata,
                overrideBlockingSecret,
                // NOTE(Julian, 2024-07-25): Approved by Corbin but may change in the future
                // (see https://unfinished-team.slack.com/archives/C076X7NKNM9/p1721926432185049)
                shouldUseLoginEmailTemplate = userAccountExists,
                siwaEmailHandling
              )
            }

            val matomoData: MatomoData

            val emailTemplateNameAndProps = when (siwaEmailHandling) {
              SiwaEmailHandling.MAGIC_LINK -> {
                matomoData = createMatomoData(matomoDimensions, MatomoPageName.SIWA_EMAIL_SENT)
                val emailSentProps = createMagicLinkProps(userIdentifier, unauthenticatedSiwaSession, providerMetadata)
                MAGIC_LINK_SENT_TEMPLATE to emailSentProps
              }

              SiwaEmailHandling.OTP -> {
                matomoData = createMatomoData(matomoDimensions, MatomoPageName.SIWA_OTP_ENTRY)
                val otpSentProps = createOtpProps(userIdentifier, unauthenticatedSiwaSession, providerMetadata)
                OTP_TEMPLATE to otpSentProps

              }
            }

            ViewResponse(
              emailTemplateNameAndProps.first,
              emailTemplateNameAndProps.second,
              newSessionId,
              matomoData,
            )
          }

          UserIdentifierType.PHONE_NUMBER -> {
            if (!isPhoneNumberEnabled(siwaSession.flowKind)) {
              // NOTE: We never anticipate our frontend sending such a request but need to guard against it
              throw ApiException(
                ApiError.SIWA_INVALID_REQUEST,
                "Phone numbers cannot be used in this flow=${siwaSession.flowKind}"
              )
            }

            if (!userAccountExists && notificationServiceClient.lookupPhoneNumber(userIdentifier.value).blockStatus.isBlocked) {
              return@fold getSiwaErrorStartPage(
                ApiError.BLOCKED_PHONE_NUMBER,
                newSessionId,
                providerMetadata.displayName,
                siwaSession.flowKind
              )
            }

            if (!userIdentifier.value.startsWith(MEWE_TEST_PHONE_PREFIX)) {
              sendSms(
                userIdentifier,
                authentication.currentCode,
                siwaSession.id,
                locale,
                providerMsaId,
                providerMetadata,
                overrideBlockingSecret,
                shouldUseLoginTemplate = userAccountExists,
                userIp,
              )
            }

            val otpProps = OtpVerificationSentProps(
              userIdentifier,
              providerMetadata.displayName,
              properties.redisExpiration.toMinutes().toInt(),
              properties.authentication.resendInterval.toMillis(),
              properties.authentication.resendLimit,
              unauthenticatedSiwaSession.siwaRequest.signatureRequest.payload.userIdentifierAdminUrl,
            )

            val matomoData = createMatomoData(matomoDimensions, MatomoPageName.SIWA_OTP_ENTRY)
            ViewResponse(OTP_TEMPLATE, otpProps, newSessionId, matomoData)
          }
        }
      }, {
        RedirectResponse(emailUri, siwaSession.id)
      })
    }
  }

  private suspend fun validationErrorReturnToCodePage(sessionId: String, apiError: ApiError): ViewResponse<SiwaProps> {
    val unauthenticatedSiwaSession = lookupService.findSiwaSessionOrThrow(sessionId)
    val userIdentifier = unauthenticatedSiwaSession.userIdentifier ?: throw ApiException(
      ApiError.NO_USER_IDENTIFIER_FOUND,
      "No user found after incorrect code entry"
    )
    val providerMetadata = getSiwaProviderMetadataOrThrow(
      unauthenticatedSiwaSession.siwaRequest.signatureRequest.publicKey,
      unauthenticatedSiwaSession.siwaRequest.applicationContext
    )
    val globalApiError = GlobalApiError(listOf(apiError))
    return returnViewResponseForSentPage(userIdentifier, unauthenticatedSiwaSession, providerMetadata, globalApiError)
  }

  override suspend fun acceptAuthenticationCode(
    authenticationCode: String?,
    sessionId: String,
  ): SiwaResponse<SiwaProps> {
    data class SiwaResponseAndSession(
      val siwaResponse: SiwaResponse<SiwaProps>,
      val session: AuthenticatedSiwaSession?
    )

    val retVal: SiwaResponseAndSession = delegatingTransactionalOperator.executeReadOnly {
      val session = try {
        authenticateSiwaSession(sessionId, authenticationCode)
      } catch (e: ApiException) {
        when (e.apiError) {
          ApiError.SIWA_SESSION_NOT_FOUND_FOR_TOKEN -> {
            return@executeReadOnly SiwaResponseAndSession(
              validationErrorReturnToCodePage(sessionId, e.apiError),
              null
            )
          }

          else -> throw e
        }
      }

      val intent = when (session.flowKind) {
        SiwaFlowKind.SOCIAL -> {
          determineSiwaIntentForSocialUser(
            session.userIdentifier,
            session.siwaRequest.signatureRequest,
            session.userKeyPairType,
          )
        }

        SiwaFlowKind.ICS -> determineSiwaIntentForIcsUser(
          session.userIdentifier,
          session.userKeyPairType,
        )
      }

      val siwaResponse = when (intent) {
        is SiwaIntent.UpdateBlockchain, is SiwaIntent.CreateSponsoredAccountAndLogin -> {
          val updatedSession = session.copy(intent = intent)
          redisClient.saveSiwaSession(updatedSession)
          val matomoData = createMatomoData(SiwaMatomoDimensions.create(), MatomoPageName.SIWA_PAYLOADS)

          val payloadProps = when (intent) {
            is SiwaIntent.UpdateBlockchain -> {
              createPayloadsPropsForSocial(intent.operations, session, null)
            }
            is SiwaIntent.CreateSponsoredAccountAndLogin -> {
              createPayloadsPropsForIcs(intent, session, null)
            }
            else -> throw IllegalStateException("Unexpected intent type ${intent::class.simpleName}")
          }

          ViewResponse(
            PAYLOADS_TEMPLATE,
            payloadProps,
            session.id,
            matomoData,
          )
        }

        is SiwaIntent.Login -> {
          //save the authenticated session
          val updatedSession = session.copy(intent = intent, userAccountId = intent.userAccountId)
          val authenticatedSession = saveAuthenticatedSiwaSession(updatedSession)

          //create a callback response
          val response = createCallbackResponse(authenticatedSession)

          // Check if the user needs to create a wallet
          if (shouldDisplayCreateWalletPage(authenticatedSession)) {
            val siwaResponse = createPasskeyWalletResponse(authenticatedSession, response)
            SiwaResponseAndSession(siwaResponse, session)
          }

          // redirect the user using the proper method.
          val isEmailMagicLinkLogin =
            updatedSession.userIdentifier.type == UserIdentifierType.EMAIL && getSiwaEmailHandlingOrDefault(
              updatedSession.siwaRequest.siwaEmailHandling
            ) == SiwaEmailHandling.MAGIC_LINK
          if (isEmailMagicLinkLogin) {
            createEmailLoginResponse(updatedSession, response)
          } else {
            response
          }
        }
      }

      SiwaResponseAndSession(siwaResponse, session)
    }

    try {
      val session = retVal.session
      if (session != null) {
        withProviderMetadata(session.siwaRequest.signatureRequest.publicKey, session.siwaRequest.applicationContext) {
          delegatingTransactionalOperator.executeReadWrite {

            // Update the 'last verified' timestamp for the user identifier
            // TODO ideally this could be dispatched asynchronously somehwere as this code does not give a shit if this happens or not
            databaseService.updateUserIdentifierVerifiedDate(mapUserIdentifierToUserDetail(session.userIdentifier))
          }
        }
      }
    } catch (buried: Exception) {
      LOG.error(
        "Exception occurred while doing a best effort to update the verifiedMillis for ${retVal.session}",
        buried
      )
    }
    return retVal.siwaResponse
  }

  override suspend fun acceptAcceptanceAndData(
    sessionId: String,
    userPayloadsAcceptanceAndData: UserPayloadsAcceptanceAndDataCommand,
  ): SiwaResponse<SiwaProps> {
    data class ReadOnlyResult(
      val session: AuthenticatedSiwaSession,
      val providerMsaId: BigInteger,
      val intent: SiwaIntent,
      val shouldGenerateGraphKey: Boolean,
      val existingUserAccount: UserAccount?,
      val validatedHandle: String?,
    )

    val validatedDataOrErrorViewResponse: Either<ViewResponse<PayloadsProps>, ReadOnlyResult> = delegatingTransactionalOperator.executeReadOnly {
      val session = lookupService.findAuthenticatedSiwaSessionOrThrow(sessionId)
      val providerMsaId = lookupService.retrieveMsaId(session.siwaRequest.signatureRequest.publicKey)

      val intent = session.intent ?: throw ApiException(
        ApiError.SIWA_INVALID_STATE,
        "Acceptance endpoint invoked for a SIWA session where 'intent' is unexpectedly null",
        mapOf("sessionId" to sessionId)
      )

      val handle = userPayloadsAcceptanceAndData.handle
      if (handle != null) {
        try {
          lookupService.validateHandle(handle)
        } catch (e: ApiException) {
          return@executeReadOnly when (e.apiError) {
            ApiError.HANDLE_UNAVAILABLE, ApiError.INVALID_HANDLE -> {
              val err = GlobalApiError(listOf(e.apiError))
              val payloadProps = when (intent) {
                is SiwaIntent.UpdateBlockchain -> createPayloadsPropsForSocial(intent.operations, session, err)
                is SiwaIntent.CreateSponsoredAccountAndLogin -> createPayloadsPropsForIcs(intent, session, err)
                else -> throw IllegalStateException("Unexpected intent type ${intent::class.simpleName}")
              }

              ViewResponse(
                PAYLOADS_TEMPLATE,
                payloadProps,
                session.id,
              ).left()
            }

            else -> throw e
          }
        }
      }

      val shouldGenerateGraphKey = intent.sendGraphKeyPair
      val existingUserAccount = lookupService.findUserAccountByUserIdentifier(session.userIdentifier)

      return@executeReadOnly ReadOnlyResult(
        session,
        providerMsaId,
        intent,
        shouldGenerateGraphKey,
        existingUserAccount,
        handle
      ).right()
    }

    return when (validatedDataOrErrorViewResponse) {
      is Either.Left -> {
        validatedDataOrErrorViewResponse.value
      }

      is Either.Right -> {
        val validatedData = validatedDataOrErrorViewResponse.value
        val authenticatedSession = delegatingTransactionalOperator.executeReadWrite {
          val userAccountId = if (validatedData.existingUserAccount == null) {
            createNewUserAccountAndKeyPairs(
              validatedData.session.userIdentifier,
              validatedData.providerMsaId,
              validatedData.shouldGenerateGraphKey,
              validatedData.session.userKeyPairType,
            )
          } else {
            linkExistingUserAccountToNewProvider(
              validatedData.existingUserAccount.id!!,
              validatedData.session.userIdentifier,
              validatedData.providerMsaId,
              validatedData.session.userKeyPairType
            )

            validatedData.existingUserAccount.id!!
          }

          // If a graph key is required to satisfy the provider request, but the user does not yet have a graph key,
          // generate and save a graph key pair.
          val userHasGraphKey = findGraphKeyPair(userAccountId) != null
          if (!userHasGraphKey && validatedData.shouldGenerateGraphKey) {
            val newGraphKeyPair = keyService.generateGraphKeyPair()
            val userKeyData = UserKeyData.create(
              userAccountId,
              newGraphKeyPair.encryptedKeyData.publicKey,
              newGraphKeyPair.encryptedKeyData.encryptedPrivateKey,
              newGraphKeyPair.encryptedKeyData.encryptedPrivateKeyType,
              newGraphKeyPair.encryptedKeyData.keyUsageType
            )

            databaseService.saveUserKeyData(userKeyData)
          }

          val updatedSession = validatedData.session.copy(
            userAccountId = userAccountId,
            userInput = SiwaPayloadsUserInput(
              true,
              validatedData.validatedHandle,
            )
          )

          saveAuthenticatedSiwaSession(updatedSession)
        } // end read write

        val userAccountId = authenticatedSession.userAccountId ?: throw AssertionError("`userAccountId` is not defined")
        val callbackResponse = createCallbackResponse(authenticatedSession)
        val intent = authenticatedSession.intent

        return when {
          intent is SiwaIntent.CreateSponsoredAccountAndLogin -> {
            val submissionId = generateUUID()
            val userKeyPairBytes = findAccountKeyPair(userAccountId, authenticatedSession.userKeyPairType)
            val userAccountKeyPair = keyPairBytesToAccountKeyPair(userKeyPairBytes)
            val existingUserMsaId = lookupService.getMsaIdByPublicKey(userAccountKeyPair.toUniversalAddress())
            val handleToClaim = when {
              intent.claimHandle -> authenticatedSession.userInput?.handle
                ?: throw IllegalStateException("'userInput.handle' is unexpectedly null")
              else -> null
            }

            val submission = AsyncSubmission<Unit>(submissionId, SubmissionStatus.SUBMITTED)
            redisClient.saveAsyncSubmission(submission)

            LOG.debug("####################### Main Coroutine of execution about to launch ####################")
            //Spin off Coroutine to submit to the chain (without blocking this thread of execution)
            createSponsoredAccountInBackgroundCoroutine(
              frequencyService,
              lookupService,
              redisClient,
              userAccountKeyPair,
              existingUserMsaId,
              handleToClaim,
              authenticatedSession.id,
              submissionId,
            )

            val callbackUrl = UriComponentsBuilder.fromUriString(callbackResponse.callbackUrl)
              .queryParam(AUTHORIZATION_CODE_PARAMETER_NAME, callbackResponse.authorizationCode).encode().build().toUriString()
            val providerName = getSiwaProviderMetadataOrThrow(validatedData.providerMsaId, validatedData.session.siwaRequest.applicationContext).displayName

            ViewResponse(
              SUBMISSION_IN_PROGRESS_TEMPLATE,
              SiwaSubmissionInProgressProps(
                submissionId,
                callbackUrl,
                providerName
              ),
              validatedData.session.id,
            )
          }

          // NOTE: The create passkey wallet page will never be shown for intent `CreateSponsoredAccountAndLogin` (i.e., ICS flow)
          shouldDisplayCreateWalletPage(authenticatedSession) -> createPasskeyWalletResponse(authenticatedSession, callbackResponse)
          else -> callbackResponse
        }
      }
    }
  }

  override suspend fun retrieveSiwaPayload(authorizationCode: String): SiwaPayloadResponse =
    delegatingTransactionalOperator.executeReadOnly {
      val loggingContext = mapOf(AUTHORIZATION_CODE_PARAMETER_NAME to authorizationCode)

      val foundSession = redisClient.findSiwaSessionByAuthorizationCode(authorizationCode)
        ?: throw ApiException(
          ApiError.SIWA_SESSION_NOT_FOUND_FOR_TOKEN,
          "No SIWA session found for the given session ID and authorization code",
          loggingContext
        )

      return@executeReadOnly withProviderMetadata(
        foundSession.siwaRequest.signatureRequest.publicKey,
        foundSession.siwaRequest.applicationContext
      ) { providerMetadata ->
        val session = when {
          foundSession is AuthenticatedSiwaSession && foundSession.authorizationCode == authorizationCode -> foundSession
          else -> throw ApiException(
            ApiError.SIWA_SESSION_NOT_FOUND_FOR_TOKEN,
            "No SIWA session found for the given session ID and authorization code",
            loggingContext
          )
        }

        // Make sure we have all the data we need from the session / user
        val userAccountId = session.userAccountId ?: throw NullPointerException("'userAccountId' is unexpectedly null")
        val accountKeyPair = findAccountKeyPair(userAccountId, session.userKeyPairType)

        var userMsaId: BigInteger? = null
        if (SiwaFlowKind.ICS == session.flowKind) {
          userMsaId = lookupService.getMsaIdByPublicKey(keyPairBytesToUniversalAddress(accountKeyPair))
        }

        val graphKeyPair = findGraphKeyPair(userAccountId)

        val intent = session.intent ?: throw NullPointerException("'intent' is unexpectedly null")

        val payloads = when (intent) {
          is SiwaIntent.Login, is SiwaIntent.CreateSponsoredAccountAndLogin -> {
            listOf(
              createCaip122LoginPayloadResponse(
                session.siwaRequest.signatureRequest.payload,
                accountKeyPair,
                providerMetadata,
              )
            )
          }

          is SiwaIntent.UpdateBlockchain -> {
            val userInput = session.userInput ?: throw NullPointerException("'userInput' is unexpectedly null")

            createSignedPayloads(
              accountKeyPair,
              graphKeyPair,
              intent.operations,
              userInput,
              mapOf(AUTHORIZATION_CODE_PARAMETER_NAME to authorizationCode, "sessionId" to session.id)
            )
          }
        }

        val developerTermsUri = URI.create("${hostName}/developer_terms.html")

        createSiwaPayloadResponse(
          session,
          accountKeyPair,
          userMsaId,
          graphKeyPair,
          intent,
          payloads,
          developerTermsCopy,
          developerTermsUri
        )
      }
    }

  override suspend fun getAsyncSubmission(submissionId: String): AsyncSubmissionResponse {
    val submission = redisClient.findAsyncSubmission<Any>(submissionId) ?: throw ApiException(
      ApiError.NO_ASYNC_SUBMISSION_FOUND,
      "No async submission found for the given submissionId=$submissionId"
    )

    val errorDto = when (val result = submission.result) {
      is Either.Left -> {
        val err = ApiError.fromId(result.value)
        ApiErrorDto(
          err.id,
          err.description,
          null
        )
      }
      else -> null
    }

    return AsyncSubmissionResponse(submission.id, submission.status, errorDto)
  }

  override suspend fun getTokenForSiwaSessionId(siwaSessionId: String): TokenResponse {
    val siwaSession = lookupService.findSiwaSessionOrThrow(siwaSessionId)
    return siwaSession.fold({ unauthenticatedSiwaSession ->
      unauthenticatedSiwaSession.authentication?.let { TokenResponse(it.currentCode) } ?: throw ApiException(
        ApiError.NO_TOKEN_FOUND_FOR_SESSION,
        "No token found for unauthenticated SiwaSession with sessionId={}",
        mapOf("sessionId" to siwaSessionId)
      )
    }, {
      throw ApiException(
        ApiError.NO_TOKEN_FOUND_FOR_SESSION,
        "No token found for authenticated SiwaSession with sessionId={}",
        mapOf("sessionId" to siwaSessionId)
      )
    })
  }

  private fun getSiwaErrorStartPage(
    apiError: ApiError,
    newSessionId: String?,
    providerName: String,
    flowKind: SiwaFlowKind,
  ): ViewResponse<StartProps> {
    return ViewResponse(
      START_TEMPLATE,
      StartProps(
        captchaClient.siteKey,
        captchaClient.enabled,
        providerName,
        isPhoneNumberEnabled(flowKind),
        prefilledEmail = null,
        prefilledPhoneNumber = null,
        error = GlobalApiError(listOf(apiError))
      ),
      newSessionId
    )
  }

  override suspend fun getSiwaErrorStartPage(siwaSession: SiwaSession, apiError: ApiError): ViewResponse<StartProps> {
    val providerName = getSiwaProviderMetadataOrThrow(
      siwaSession.siwaRequest.signatureRequest.publicKey,
      siwaSession.siwaRequest.applicationContext
    ).displayName
    val contextMap: Map<String, String> = mapOf(Pair(LoggingAttributes.PROVIDER_NAME, providerName))
    return ContextLoggerHelper.putContext(contextMap) {
      getSiwaErrorStartPage(apiError, null, providerName, siwaSession.flowKind)
    }
  }

  private fun getSiwaEmailHandlingOrDefault(siwaEmailHandling: SiwaEmailHandling?): SiwaEmailHandling {
    return siwaEmailHandling ?: properties.defaultSiwaEmailHandling
  }

  private suspend fun shouldDisplayCreateWalletPage(session: SiwaSession): Boolean {
    return properties.passkeyActive && passkeyWalletService.retrieveCredentials(session.id).credentials.isEmpty()
  }

  private fun createMatomoData(matomoDimensions: SiwaMatomoDimensions, matomoPageName: MatomoPageName): MatomoData {
    val pageTitle = matomoPageName.pageName
    val matomoEvents = MatomoEvent(MatomoEvent.Category.SIWA, matomoPageName.noPrefix())
    matomoDimensions.addEnv(properties.environment)
    return MatomoData(pageTitle, matomoDimensions, matomoEvents)
  }

  private fun determineUserKeyPairType(providerKeyPairType: KeyPairType, overrideUserKeyType: String?): KeyPairType {
    return when {
      overrideUserKeyType != null -> KeyPairType.fromType(overrideUserKeyType)
      else -> providerKeyPairType
    }
  }
}
