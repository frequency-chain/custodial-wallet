package io.amplica.custodial_wallet.orchestration

import arrow.core.Either
import com.strategyobject.substrateclient.crypto.ss58.SS58AddressFormat
import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.client.captcha.CaptchaClient
import io.amplica.custodial_wallet.client.notification.NotificationServiceClient
import io.amplica.custodial_wallet.client.notification.SendSmsRequest
import io.amplica.custodial_wallet.client.redis.CustodialWalletRedisClient
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.client.redis.generateUUID
import io.amplica.custodial_wallet.controller.util.ChangeExternalUserIdRequest
import io.amplica.custodial_wallet.controller.util.RetryHelper
import io.amplica.custodial_wallet.controller.util.getPermissionKeysForSchemaIds
import io.amplica.custodial_wallet.db.repository.KeyUsageType
import io.amplica.custodial_wallet.db.repository.ProviderExternalUser
import io.amplica.custodial_wallet.db.repository.UserData
import io.amplica.custodial_wallet.dto.*
import io.amplica.custodial_wallet.email.EmailService
import io.amplica.custodial_wallet.email.client.conf.AwsSesProperties
import io.amplica.custodial_wallet.exception.*
import io.amplica.custodial_wallet.internationalization.MessageFactory
import io.amplica.custodial_wallet.orchestration.passkey.PasskeyWalletService
import io.amplica.custodial_wallet.orchestration.payload.HandleRequest
import io.amplica.custodial_wallet.orchestration.signing.SigningOrchestrationService
import io.amplica.custodial_wallet.orchestration.util.mapUserDetailsToProviderName
import io.amplica.custodial_wallet.orchestration.util.mapUserIdentifierToUserDetail
import io.amplica.custodial_wallet.service.password.PasswordService
import io.amplica.custodial_wallet.util.fromHex
import io.amplica.custodial_wallet.util.key_creation.KeyPairSignatureAlgorithm
import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto
import io.amplica.custodial_wallet.util.key_creation.Sr25519KeyPairBytes
import io.amplica.custodial_wallet.util.key_creation.Sr25519KeyPairCreator
import io.amplica.custodial_wallet.util.normalizeToHex
import io.amplica.custodial_wallet.web.AUTHORIZATION_CODE_PARAMETER_NAME
import io.amplica.custodial_wallet.web.ContextLoggerHelper
import io.amplica.custodial_wallet.web.LoggingAttributes
import io.amplica.frequency.client.*
import io.amplica.frequency.crypto.provider.Secp256K1CryptoProvider
import io.amplica.frequency.crypto.provider.Sr25519CryptoProvider
import io.amplica.frequency.crypto.toUniversalAddress
import io.amplica.frequency.payload.CreateHandlePayload
import io.amplica.frequency.service.SigningService
import io.amplica.frequency.util.arrow.getOrThrow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import org.springframework.web.util.UriComponentsBuilder
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import java.util.*


/**
 * Api The intent of this function is to coerce the IllegalStateException to an ApiError rather than
 * have a non ApiException bubble out
 *
 * @param publicKeyDto
 * @return
 */
fun apiNormalizeToHex(publicKeyDto: PublicKeyDto): String {
  try{
    return normalizeToHex(publicKeyDto)
  }catch(x: IllegalArgumentException) {
    throw ApiException(ApiError.INVALID_PUBLIC_KEY_FORMAT, "For format=$publicKeyDto.format for publicKeyDto=$publicKeyDto", x)
  }
}

data class SmsProperties(
  val sourceNumber: String,
  val directLoginTemplateName: String,
  val addIdentifierTemplateName: String,
  val signUpTemplateName: String,
  val loginTemplateName: String,
)

data class DefaultCustodialWalletProperties(
  val signupBlockExpiration: Long,
  val timerExpiration: Duration,
  val otpExpiration: Duration,
  val resendLimit: Int,
  val hostName: String,
  val schemaIdPermissionsMap: Map<Set<Int>, String>,
  val outputSmsCodeEnabled: Boolean,
  val userActivityExpiration: Duration,
  val changeHandlePeriod: Duration,
  val awsSes: AwsSesProperties,
  val sms: SmsProperties,
)

open class DefaultCustodialWalletOrchestrationService(
  private val properties: DefaultCustodialWalletProperties,
  private val redisClient: CustodialWalletRedisClient,
  private val frequencyClient: FrequencyClient,
  private val signingService: SigningService,
  private val signingOrchestrationService: SigningOrchestrationService,
  private val databaseService: CustodialWalletDatabaseService,
  private val ss58AddressFormat: SS58AddressFormat,
  private val messageFactory: MessageFactory,
  private val retryHelper: RetryHelper,
  private val transactionalOperator: TransactionalOperator,
  private val lookupService: LookupOrchestrationService,
  private val notificationServiceClient: NotificationServiceClient,
  private val emailService: EmailService,
  private val passwordService: PasswordService,
  private val passkeyWalletService: PasskeyWalletService,
  private val captchaClient: CaptchaClient,
) : CustodialWalletOrchestrationService {

  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(DefaultCustodialWalletOrchestrationService::class.java)
  }

  override suspend fun deleteUserByUserIdentifier(userIdentifier: UserIdentifier): DeleteUserResponse = transactionalOperator.executeAndAwait {
    val userDetail = mapUserIdentifierToUserDetail(userIdentifier)
    DeleteUserResponse(databaseService.deleteAllUserAccountsByUserDetailCascading(userDetail))
  }

  override suspend fun deleteUserByDeleteUserByExternalIdRequest(deleteUserByExternalIdRequest: DeleteUserByExternalIdRequest): DeleteUserResponse = transactionalOperator.executeAndAwait {
    val publicKeyDto = deleteUserByExternalIdRequest.publicKey
    val providerMsaId = lookupService.retrieveMsaId(publicKeyDto.format, publicKeyDto.encodedValue)
    val retVal = databaseService.deleteAllUserAccountsByProviderMsaIdAndExternalIdCascading(providerMsaId, deleteUserByExternalIdRequest.externalUserId)
    DeleteUserResponse(retVal)
  }

  override suspend fun revokeDelegationAndHandle(providerUserIdentifier: ProviderUserIdentifier): Unit = transactionalOperator.executeAndAwait {
    val providerMsaId = providerUserIdentifier.providerMsaId
    lookupService.verifyWhitelistedProviderMsaId(providerMsaId)
    val userDetail = mapUserIdentifierToUserDetail(providerUserIdentifier.userIdentifier)
    val userKeyData = lookupService.findAccountUserKeyDataByProviderMsaIdAndUserDetail(providerMsaId, userDetail)
    val userKeyPair = lookupService.getDecryptedAccountKeyPair(userKeyData)
    val msaId = lookupService.getMsaIdByPublicKeyHex(userKeyData.publicKeyHex)
    val handleResponse = lookupService.getHandle(msaId)
    val hasHandle = handleResponse.baseHandle.isNotEmpty()
    var retireHandle: Either<Exception, HandleRetired>

    if (hasHandle) {
      LOG.info("frequencyClient.retireHandleByUser for {}", providerUserIdentifier.userIdentifier)
      retireHandle = frequencyClient.retireHandleByUser(userKeyPair).await()

      when (retireHandle) {
        is Either.Left<Exception> -> {
          LOG.error("error retire handle ${retireHandle.value.message} and identifier: $providerUserIdentifier")
          throw ApiException(
            ApiError.RETIRE_HANDLE_FAILED,
            retireHandle.value.message
          )
        }

        is Either.Right<HandleRetired> -> LOG.info("Successful revoke handle for ${retireHandle.value.handle} and identifier: $providerUserIdentifier")
      }
    }

    LOG.info("frequencyClient.revokeDelegationByDelegator for {}", providerUserIdentifier.userIdentifier)
    val revokeDelegation =
      frequencyClient.revokeDelegationByDelegator(userKeyPair, providerMsaId).await()

    when (revokeDelegation) {
      is Either.Left<Exception> -> {
        LOG.error("error revoke delegation ${revokeDelegation.value.message} and identifier: $providerUserIdentifier")
        throw ApiException(
          ApiError.REVOKE_DELEGATION_FAILED,
          revokeDelegation.value.message,
          revokeDelegation.value
        )
      }

      is Either.Right<DelegationRevoked> -> LOG.info("Successful revoke delegation for delegator:${revokeDelegation.value.delegatorMsaId} identifier: $providerUserIdentifier")
    }
  }

  override suspend fun deleteUserAndRetireMsaByUserIdentifier(
    providerUserIdentifier: ProviderUserIdentifier
  ): DeleteUserResponse = transactionalOperator.executeAndAwait {
    val providerMsaId = providerUserIdentifier.providerMsaId
    lookupService.verifyWhitelistedProviderMsaId(providerMsaId)
    val userDetail = mapUserIdentifierToUserDetail(providerUserIdentifier.userIdentifier)
    val userKeyData = lookupService.findAccountUserKeyDataByProviderMsaIdAndUserDetail(providerMsaId, userDetail)
    val publicKey = fromHex(userKeyData.publicKeyHex)
    val privateKey = lookupService.getDecryptedPrivateKey(userKeyData)

    LOG.info("frequencyClient.retireMsaByUser for {}", providerUserIdentifier.userIdentifier)
    when (val retireMsaResponse = frequencyClient.retireMsaByUser(publicKey, privateKey).await()) {
      is Either.Left<Exception> -> {
        LOG.error("error retire msa ${retireMsaResponse.value.message} for identifier: $providerUserIdentifier")
        throw ApiException(ApiError.RETIRE_MSA_FAILED, retireMsaResponse.value.message)
      }

      is Either.Right<MsaRetired> -> LOG.info("Successful retiring of Msa for ${retireMsaResponse.value.msaId} and identifier: $providerUserIdentifier")
    }

    DeleteUserResponse(databaseService.deleteAllUserAccountsByUserDetailCascading(userDetail))
  }

  override suspend fun sendLoginUrl(
    directLoginRequest: DirectLoginRequest,
    locale: Locale,
    userIp: String?,
    xCaptchaHeaderValue: String?,
  ): String = transactionalOperator.executeAndAwait {
    val contactMethod = directLoginRequest.contactMethod
    val contactMethodType = directLoginRequest.contactMethodType
    captchaClient.verifyCaptchaStatus(directLoginRequest.captchaToken, userIp, xCaptchaHeaderValue)
    val accountId = lookupService.getExistingAccountIdFromContactMethod(contactMethod, contactMethodType)
      ?: throw ApiException(
        ApiError.NO_USER_FOUND_ERROR,
        "No User Accounts found for given contact method provided: $contactMethod"
      )

    directLoginRequest.callbackUrl?.let { lookupService.verifyWhitelistedApplicationUrl(it) }

    val authenticationCode = generateToken()
    val userIdentifier = UserIdentifier(contactMethod, contactMethodType)
    val websiteSession = WebsiteSession(null, directLoginRequest.callbackUrl, userIdentifier, listOf(accountId), authenticationCode, null)

    val sessionId = redisClient.saveWebsiteSessionByAuthenticationCode(authenticationCode, websiteSession)

    if (contactMethodType == UserIdentifierType.EMAIL) {
      emailService.sendDirectLoginEmail(contactMethod, authenticationCode, sessionId, locale)
    } else {
      val smsMessage = messageFactory.createMessage(properties.sms.directLoginTemplateName, mapOf(Pair("authenticationCode", authenticationCode)), null, locale) // add template name to conf
      val verifiedMillis = lookupService.getVerifiedMillisForPhone(userIdentifier)
      ContextLoggerHelper.withMdcContext { localSessionId, xSessionId, xTraceId ->
        notificationServiceClient.sendSms(
          SendSmsRequest(
            contactMethod,
            smsMessage,
            verifiedMillis,
            userIp
          ),
          localSessionId,
          xSessionId,
          xTraceId,
        )
      }
    }
    sessionId
  }

  override suspend fun createSignedChangeHandlePayload(changeHandleRequest: ChangeHandleRequest): ChangeHandleResponse =
    transactionalOperator.executeAndAwait {
      val providerMsaId = lookupService.retrieveMsaId(
        changeHandleRequest.providerPublicKey.format,
        changeHandleRequest.providerPublicKey.encodedValue
      )
      lookupService.verifyWhitelistedProviderMsaId(providerMsaId)

      val payload = HandleRequest(changeHandleRequest.handle.payload.baseHandle)
      val signatureIsValid = signingOrchestrationService.verifySignedPayload(
        changeHandleRequest.providerPublicKey,
        payload,
        changeHandleRequest.handle.signature,
      )
      if (!signatureIsValid) {
        throw ApiException(
          ApiError.INVALID_SIGNATURE,
          "The changeHandleRequest contains an invalid signature, handle=${changeHandleRequest.handle}"
        )
      }

      val userPublicKeyHex = apiNormalizeToHex(changeHandleRequest.userPublicKey)
      val userKeyData = lookupService.findOneUserKeyDataWithPublicKeyOrThrow(
        providerMsaId,
        changeHandleRequest.userPublicKey.type,
        userPublicKeyHex,
        KeyUsageType.ACCOUNT
      )
      val privateKey = lookupService.getDecryptedPrivateKey(userKeyData)
      val algo = KeyPairSignatureAlgorithm.fromAlgorithm(userKeyData.encryptedPrivateKeyType.type)
      val keyPair = Sr25519KeyPairBytes(fromHex(userPublicKeyHex), privateKey, algo)
      val expirationBlockNumber = lookupService.retrieveCurrentBlockNumber() + properties.signupBlockExpiration

      val handlePayload = CreateHandlePayload(changeHandleRequest.handle.payload.baseHandle, expirationBlockNumber)
      val handlePayloadSignature: Signature = signingOrchestrationService.signPayload(keyPair, handlePayload)
      val handlePayloadResponse = HandlePayloadResponse(changeHandleRequest.handle.payload.baseHandle, expirationBlockNumber)

      ChangeHandleResponse(
        Sr25519KeyPairCreator.createSr25519PublicKeyDto(keyPair, ss58AddressFormat),
        HandleResponse(
          handlePayloadSignature,
          handlePayloadResponse
        )
      )
    }

  override suspend fun authenticateLogin(sessionId: String, authenticationCode: String, locale: Locale): WebsiteSession = transactionalOperator.executeAndAwait {
    val session = lookupService.findWebsiteSessionBySessionId(sessionId)
    val websiteSession = redisClient.findWebsiteSessionBySessionIdAndAuthenticationCode(sessionId, authenticationCode)
      ?: coroutineScope {
        retryHelper.checkIncorrectTokenRetriesViolation(session)
        throw ApiException(
          ApiError.NO_WEBSITE_SESSION_FOR_TOKEN_ERROR,
          "No Website Session found for this session ID $sessionId and/or authentication code $authenticationCode"
        )
      }

    val userIdentifier = websiteSession.userIdentifier ?:
      throw ApiException(ApiError.NO_USER_IDENTIFIER_FOUND, "No user identifier found for website session sessionId=$sessionId")

    // Update the `verifiedDate` for the `UserIdentifier` to the current time
    databaseService.updateUserIdentifierVerifiedDate(mapUserIdentifierToUserDetail(userIdentifier))

    websiteSession
  }

  override suspend fun authenticateLoggedIn(sessionId: String, locale: Locale): WebsiteSession = transactionalOperator.executeAndAwait {
    val websiteSession = lookupService.findWebsiteSessionBySessionId(sessionId)
    if(websiteSession.loggedIn == UserState.LOGGED_IN)
      websiteSession
    else throw ApiException(
      ApiError.NO_LOGGED_IN_SESSION_FOUND,
      "UserState is not LOGGED_IN. Found State: ${websiteSession.loggedIn.name} for sessionId: $sessionId"
    )
  }

  /* In the event we were called with a callback url this will result in a redirect that provides an AuthorizationToken that can be cashed in at `LoginApiController.validateAuthorizationCode`*/
  override suspend fun retrieveAccountInfoOrCallback(
    websiteSession: WebsiteSession
  ): Either<AccountInfo, String> = transactionalOperator.executeAndAwait {
    val userData = lookupService.getUserDataFromWebsiteSession(websiteSession)
    val userInfoList = mapUserDataToProviderUserInfo(userData)
    websiteSession.msaId = userInfoList[0].userMsaId
    val userAccountIds = websiteSession.userAccountIds ?: throw ApiException(ApiError.NO_USER_ACCOUNT_ID_FOUND, "No User Account Id found when retrieving account info for log in. With session: ${websiteSession.sessionId}")
    val accountUserDetails = lookupService.findUserDetailsFromUserAccountId(userAccountIds.first())
    val passkeyWalletCreated = passkeyWalletService.walletExistsForAccount(userAccountIds.first())
    val accountInfo = AccountInfo(accountUserDetails, userInfoList, passkeyWalletCreated)
    val callbackUrl = websiteSession.callbackUrl
    if (callbackUrl != null) {
      val redirected = createRedirectForCallback(websiteSession, callbackUrl)
      Either.Right(redirected)
    } else {
      Either.Left(accountInfo)
    }
  }

  override suspend fun createRedirectForCallback(websiteSession: WebsiteSession, callbackUrl: String): String {
    // Verify that there is a valid callback URL (else throw)
    lookupService.verifyWhitelistedApplicationUrl(callbackUrl)

    val authorizationCode = generateUUID()
    val authorizationSessionId = redisClient.saveWebsiteSessionByAuthorizationCode(authorizationCode, websiteSession)
    return "redirect:${
      UriComponentsBuilder.fromUriString(callbackUrl).queryParam("sessionId", authorizationSessionId)
        .queryParam(AUTHORIZATION_CODE_PARAMETER_NAME, authorizationCode).encode().build().toUriString()
    }"
  }

  override suspend fun loginValidateAuthorizationCode(authorizationCodeRequest: AuthorizationCodeRequest): AuthorizationWebsiteSessionResponse = transactionalOperator.executeAndAwait {
    val sessionId = authorizationCodeRequest.sessionId
    val authorizationCode = authorizationCodeRequest.authorizationCode
    val websiteSession = lookupService.findWebsiteSessionBySessionIdAndAuthorizationCode(sessionId, authorizationCode)
    val msaId = websiteSession.msaId ?: throw ApiException(ApiError.NO_MSA_ID_FOUND_ERROR, "No msaId found in sessionId=$sessionId")
    AuthorizationWebsiteSessionResponse(msaId, msaId.toString(10))
  }

  override suspend fun changeExternalUserId(changeExternalUserIdRequest: ChangeExternalUserIdRequest): ProviderExternalUser = transactionalOperator.executeAndAwait {
    val providerMsaId = lookupService.retrieveMsaId(changeExternalUserIdRequest.providerPublicKey.format, changeExternalUserIdRequest.providerPublicKey.encodedValue)
    val userPublicKey = changeExternalUserIdRequest.userPublicKey
    val userPublicKeyHex = apiNormalizeToHex(changeExternalUserIdRequest.userPublicKey)
    val providerExternalUser = lookupService.findProviderExternalUserByProviderMsaIdAndKeyPairTypeAndPublicKeyHexAndKeyUsageType(providerMsaId, userPublicKey.type, userPublicKeyHex, KeyUsageType.ACCOUNT)
    val desiredProviderExternalUser = ProviderExternalUser(providerExternalUser.id,
      providerExternalUser.providerMsaId,
      changeExternalUserIdRequest.desiredExternalUserId,
      providerExternalUser.userKeyDataId,
      providerExternalUser.createdAt,
      Instant.now().toEpochMilli().toBigInteger(),
      providerExternalUser.version)
    databaseService.saveProviderExternalUser(desiredProviderExternalUser)
  }

  override suspend fun handleAddNewIdentifierVerification(
    sessionId: String,
    verificationCode: String
  ): AddIdentifierVerificationResponse = transactionalOperator.executeAndAwait {
    val websiteSession = try {
      lookupService.findWebsiteSessionBySessionIdAndVerificationCode(sessionId, verificationCode)
    } catch (_: ApiException) {
      val websiteSessionFoundBySessionId = lookupService.findWebsiteSessionBySessionId(sessionId)
      updateWebsiteSessionAfterNewIdentifierAdded(websiteSessionFoundBySessionId)
      throw ApiException(
        ApiError.NO_WEBSITE_SESSION_FOR_TOKEN_ERROR,
        "No Website Session found for this verification code $verificationCode with session ID $sessionId"
      )
    }

    val existingUserIdentifier = websiteSession.userIdentifier ?: throw ApiException(
      ApiError.NO_USER_IDENTIFIER_FOUND,
      "No existing User Identifier found for current web session with session ID $sessionId"
    )
    val userAccountId = getUserAccountIdFromWebsiteSession(websiteSession)
    val userIdentifierToAdd = websiteSession.addIdentifier ?: throw ApiException(
      ApiError.NO_USER_IDENTIFIER_FOUND,
      "No User Identifier found for current web session with session ID $sessionId for user ${existingUserIdentifier.value}"
    )

    databaseService.saveNewUserIdentifierForUserAccount(
      mapUserIdentifierToUserDetail(userIdentifierToAdd),
      userAccountId
    )

    val userAccountIds = websiteSession.userAccountIds ?: throw ApiException(
      ApiError.NO_USER_ACCOUNT_ID_FOUND,
      "No User Account ID found for current web session with session ID $sessionId for user ${existingUserIdentifier.value}"
    )
    val accountUserDetails = lookupService.findUserDetailsFromUserAccountId(userAccountId)
    val passkeyWalletCreated = passkeyWalletService.walletExistsForAccount(userAccountId)
    val userData = databaseService.findUserDataByUserAccountIds(userAccountIds)
    val providerUserInfoList = mapUserDataToProviderUserInfo(userData)
    val accountInfo = AccountInfo(accountUserDetails, providerUserInfoList, passkeyWalletCreated)

    // Update the `verifiedDate` to the current time since we just verified the current user identifier
    val userDetail = mapUserIdentifierToUserDetail(existingUserIdentifier)
    databaseService.updateUserIdentifierVerifiedDate(userDetail)

    updateWebsiteSessionAfterNewIdentifierAdded(websiteSession)
    AddIdentifierVerificationResponse(true, websiteSession.callbackUrl, accountInfo)
  }

  override suspend fun sendNewIdentifierVerificationEmail(
    addIdentifierRequest: AddIdentifierRequest,
    sessionId: String,
    locale: Locale
  ): Unit = transactionalOperator.executeAndAwait {
    if(!checkLoggedInState(sessionId)) {
      throw ApiException(ApiError.NO_LOGGED_IN_SESSION_FOUND,
        "Tried to add new email identifier without having a logged in session: $sessionId with addIdentifierRequest: $addIdentifierRequest")
    }
    val verificationCode = updateWebsiteSessionWithNewIdentifier(sessionId, addIdentifierRequest, UserIdentifierType.EMAIL)
    emailService.sendNewIdentifierVerificationEmail(addIdentifierRequest.newIdentifier, verificationCode, sessionId, locale)
  }

  override suspend fun sendNewIdentifierVerificationSms(
    addIdentifierRequest: AddIdentifierRequest,
    sessionId: String,
    locale: Locale
  ): Unit = transactionalOperator.executeAndAwait {
    if(!checkLoggedInState(sessionId)) throw ApiException(ApiError.NO_LOGGED_IN_SESSION_FOUND,
      "Tried to add new email identifier without having a logged in session: $sessionId with addIdentifierRequest: $addIdentifierRequest")
    val verificationCode = updateWebsiteSessionWithNewIdentifier(sessionId, addIdentifierRequest, UserIdentifierType.PHONE_NUMBER)
    val smsMessage = messageFactory.createMessage(properties.sms.addIdentifierTemplateName, mapOf(Pair("verificationCode", verificationCode)), null, locale)
    val verifiedMillis = lookupService.getVerifiedMillisForPhone(UserIdentifier(addIdentifierRequest.newIdentifier, UserIdentifierType.PHONE_NUMBER))
    ContextLoggerHelper.withMdcContext { sessionId, xSessionId, xTraceId ->
      notificationServiceClient.sendSms(
        SendSmsRequest(
          addIdentifierRequest.newIdentifier,
          smsMessage,
          verifiedMillis,
          null,
        ),
        sessionId,
        xSessionId,
        xTraceId,
      )
    }
  }

  override suspend fun changeHandle(sessionId: String, newHandle: String): String {
    val websiteSession = lookupService.findWebsiteSessionBySessionId(sessionId)
    if (!checkLoggedInState(websiteSession)) throw ApiException(
      ApiError.NO_LOGGED_IN_SESSION_FOUND,
      "Tried to change handle without having a logged in session. sessionId=$sessionId"
    )

    val userAccountId = getSingleUserAccountIdFromWebsiteSession(websiteSession)

    val userActivityRecord = redisClient.findUserActivityRecord(userAccountId)
    val handleLastChanged = userActivityRecord?.handleLastChanged
    val changeHandleRequestedTooSoon = handleLastChanged?.let { lastChanged ->
      Instant.now() < lastChanged.plus(properties.changeHandlePeriod)
    } ?: false

    if (changeHandleRequestedTooSoon) {
      throw ApiException(
        ApiError.CHANGE_HANDLE_TOO_SOON,
        "Change handle requested too soon, last changed: $handleLastChanged"
      )
    }

    // NOTE(Julian, 2025-08-28): We default to the most recently-created key pair if the user has multiple, but
    // we don't know (without asking the chain) if that key pair has been registered or not.
    val userKeyData = lookupService.findAllUserKeyDataByUserAccountIdAndKeyUsageType(
      userAccountId,
      KeyUsageType.ACCOUNT
    ).maxByOrNull { it.createdAt } ?: throw ApiException(
      ApiError.NO_USER_KEY_DATA_FOUND,
      "Unable to find user key data for user accountId=$userAccountId sessionId=$sessionId"
    )
    val keyPair = lookupService.getDecryptedAccountKeyPair(userKeyData)

    // Create and sign a change handle payload on behalf of user
    val expirationBlockNumber = lookupService.retrieveCurrentBlockNumber() + properties.signupBlockExpiration
    val handlePayload = CreateHandlePayload(newHandle, expirationBlockNumber)
    val handlePayloadSignature = signingService.signPayload(keyPair, handlePayload)
    val signatureType = when (keyPair.cryptoProvider) {
      Sr25519CryptoProvider -> SpRuntimeMultiSignatureType.SR25519
      Secp256K1CryptoProvider -> SpRuntimeMultiSignatureType.ECDSA
    }

    val result = frequencyClient.changeHandleWithCapacity(
      keyPair.toUniversalAddress(),
      signatureType,
      handlePayloadSignature.bytes,
      handlePayload.toScaleObject()
    ).await()

    val handleClaimed = result.mapLeft { error ->
      ApiException(
        ApiError.CHANGE_HANDLE_FAILED,
        "Submitting change handle with capacity extrinsic failed: ${error.message}",
        cause = error
      )
    }.getOrThrow()

    val updatedActivityRecord = redisClient.findUserActivityRecord(userAccountId)?.copy(
      expiration = properties.userActivityExpiration,
      handleLastChanged = Instant.now()
    )
    redisClient.saveUserActivityRecord(
      updatedActivityRecord ?: UserActivityRecord(
        userAccountId,
        properties.userActivityExpiration,
        Instant.now()
      )
    )

    return handleClaimed.handle
  }

  private suspend fun updateWebsiteSessionWithNewIdentifier(sessionId: String, addIdentifierRequest: AddIdentifierRequest, userIdentifierType: UserIdentifierType): String{
    val newIdentifier = UserIdentifier(addIdentifierRequest.newIdentifier, userIdentifierType)
    val userAccountId = addIdentifierRequest.userAccountId
    val websiteSession = lookupService.findWebsiteSessionBySessionId(sessionId)
    if(websiteSession.loggedIn == UserState.LOGGED_IN) {
      val verificationCode = generateToken()
      websiteSession.addIdentifier(newIdentifier, userAccountId, verificationCode, sessionId)
      redisClient.saveWebsiteSessionByVerificationCode(verificationCode, websiteSession)
      return verificationCode
    } else {
      throw ApiException(
        ApiError.NO_LOGGED_IN_SESSION_FOUND,
        "No Logged In Website Session found for this session ID $sessionId"
      )
    }
  }

  override suspend fun amplicaAccessLogout(sessionId: String) = transactionalOperator.executeAndAwait {
    lookupService.findWebsiteSessionBySessionId(sessionId)
    redisClient.deleteWebsiteSessionBySessionId(sessionId)
  }

  private fun checkLoggedInState(websiteSession: WebsiteSession): Boolean = websiteSession.loggedIn == UserState.LOGGED_IN


  override suspend fun checkLoggedInState(sessionId: String?): Boolean = transactionalOperator.executeAndAwait {
    when (sessionId) {
      null -> false
      else -> try {
        val websiteSession = lookupService.findWebsiteSessionBySessionId(sessionId)
        checkLoggedInState(websiteSession)
      } catch (_: ApiException){
        false
      }
    }
  }

  override suspend fun createLoggedInSession(websiteSession: WebsiteSession): WebsiteSession {
    val authenticationCode = websiteSession.authenticationCode ?: throw ApiException(
      ApiError.MISSING_AUTHENTICATION_CODE,
      "Can't create Logged in session. Auth Code missing for sessionId: ${websiteSession.id}"
    )
    val loginSessionId = websiteSession.id ?: throw ApiException(
      ApiError.MISSING_SESSION_ID,
      "Can't create Logged in session. Session Id is missing for websiteSession"
    )
    val deletedWebsiteSession =
      redisClient.getAndDeleteWebsiteSessionByAuthenticationCode(loginSessionId, authenticationCode)
    if (deletedWebsiteSession?.id != websiteSession.id)
      throw ApiException(
        ApiError.LOGIN_SESSION_DELETE_ERROR,
        "login Session: ${websiteSession.id} does not match deleted Session: ${deletedWebsiteSession?.id}"
      )
    websiteSession.id = null
    websiteSession.authenticationCode = null
    websiteSession.loggedIn = UserState.LOGGED_IN
    val newSessionId = redisClient.saveWebsiteSession(websiteSession)
    return lookupService.findWebsiteSessionBySessionId(newSessionId)
  }

  override suspend fun revokeDelegation(
    providerMsaId: BigInteger,
    userPublicKeyHex: String,
    sessionId: String
  ): Boolean = transactionalOperator.executeAndAwait {
    if(!checkLoggedInState(sessionId)) {
      throw ApiException(ApiError.NO_LOGGED_IN_SESSION_FOUND,
        "Tried to revoke delegations without having a logged in session: $sessionId")
    }

    val delegatorMsaId = lookupService.getMsaIdByPublicKeyHex(userPublicKeyHex)
    if(frequencyClient.getGrantedSchemasByMsaId(delegatorMsaId, providerMsaId).await().isEmpty()){
      throw ApiException(
        ApiError.NO_DELEGATIONS_EXIST,
        "No Delegations found for providerMsaId=$providerMsaId and delegatorMsaId=$delegatorMsaId"
      )
    }

    val websiteSession = lookupService.findWebsiteSessionBySessionId(sessionId)
    val userIdentifier = websiteSession.userIdentifier ?: throw ApiException(ApiError.NO_USER_IDENTIFIER_FOUND, "No existing User Identifier found for current web session with session ID $sessionId")

    val userKeyData = lookupService.findAccountUserKeyDataByProviderMsaIdAndUserDetail(
      providerMsaId,mapUserIdentifierToUserDetail(userIdentifier)
    )
    val publicKey = fromHex(userPublicKeyHex)
    val privateKey = lookupService.getDecryptedPrivateKey(userKeyData)
    when (val revokeDelegationResponse = frequencyClient.revokeDelegationByDelegator(privateKey, publicKey, providerMsaId).await()) {
      is Either.Left<Exception> -> throw ApiException(ApiError.REVOKE_DELEGATION_FAILED, revokeDelegationResponse.value.message)
      is Either.Right<DelegationRevoked> -> true
    }
  }

  override suspend fun changePassword(sessionId: String, changePasswordRequest: ChangePasswordRequest) {
    lookupService.findWebsiteSessionBySessionId(sessionId)
    passwordService.updatePasswordByUserAccountId(
      changePasswordRequest.userAccountId,
      changePasswordRequest.newRawPassword
    )
  }

  private suspend fun createPasswordLoginUserSession(passwordDirectLoginRequest: PasswordDirectLoginRequest, contactMethodType: UserIdentifierType): String {
    val contactMethod = passwordDirectLoginRequest.username
    val accountId = lookupService.getExistingAccountIdFromContactMethod(contactMethod, contactMethodType)
      ?: throw ApiException(
        ApiError.NO_USER_FOUND_ERROR,
        "No User Accounts found for given contact method provided: $contactMethod"
      )

    passwordDirectLoginRequest.callbackUrl?.let { lookupService.verifyWhitelistedApplicationUrl(it) }
    //NOTE: Issue #575 will work on changing priorities to not be hardcoded
    val userIdentifier = UserIdentifier(contactMethod, contactMethodType)
    val websiteSession = WebsiteSession(null, passwordDirectLoginRequest.callbackUrl, userIdentifier, listOf(accountId))
    websiteSession.loggedIn = UserState.LOGGED_IN
    val sessionId = redisClient.saveWebsiteSession(websiteSession)
    return sessionId
  }

  override suspend fun authenticateUserWithPassword(passwordDirectLoginRequest: PasswordDirectLoginRequest, contactMethodType: UserIdentifierType): String {
    val username = passwordDirectLoginRequest.username
    val password = passwordDirectLoginRequest.password
    if(!passwordService.authenticateByContactMethod(username, contactMethodType, password)) {
      throw ApiException(ApiError.INCORRECT_PASSWORD, "Failed authentication for user $username")
    } else {
      return createPasswordLoginUserSession(passwordDirectLoginRequest, contactMethodType)
    }
  }

  private suspend fun updateWebsiteSessionAfterNewIdentifierAdded(websiteSession: WebsiteSession) {
    val updatedWebsiteSession = WebsiteSession(
      websiteSession.id,
      websiteSession.callbackUrl,
      websiteSession.userIdentifier,
      websiteSession.userAccountIds,
      websiteSession.authenticationCode,
      websiteSession.msaId,
      websiteSession.providerExternalUserId,
      websiteSession.userAccountId,
      null,
      websiteSession.sessionId,
      null,
      websiteSession.providerMsaId,
      loggedIn = websiteSession.loggedIn
    )
    redisClient.saveWebsiteSession(updatedWebsiteSession)
  }

  override suspend fun mapUserDataToProviderUserInfo(userDataList: List<UserData>): List<ProviderUserInfo> {
    val userDetailMultimap = mapUserDetailsToProviderName(userDataList)
    val providerUserInfo: List<ProviderUserInfo> =
      userDataList.map { userData ->
        val userMsaId = lookupService.getMsaIdByPublicKeyHex(userData.publicKeyHex)
        val handleResponse = lookupService.getHandle(userMsaId)
        val handle = displayHandle(handleResponse)

        val providerMsaId = userData.providerMsaId
        val providerMetadata = lookupService.getProviderMetaData(providerMsaId)
          ?: throw ApiException(ApiError.PROVIDER_NOT_FOUND, "No provider name found for this msa Id")
        val providerName = providerMetadata.displayName

        val schemaIdList = lookupService.getGrantedSchemasByMsaId(userMsaId, providerMsaId)
        val permissionKeys = getPermissionKeysForSchemaIds(schemaIdList, properties.schemaIdPermissionsMap, null)
        val userDetailList = userDetailMultimap[providerMsaId].map { it.second }
        val hasPassword = passwordService.checkPasswordExistsByUserAccountId(userData.userAccountId)

        ProviderUserInfo(userData.userAccountId,
          userData.publicKeyHex,
          providerMsaId,
          userData.providerExternalId,
          userDetailList,
          providerName,
          handle,
          userMsaId,
          permissionKeys,
          userData.providerExternalUserId,
          hasPassword,
        )
      }
    return providerUserInfo.distinct()
  }

  private fun getSingleUserAccountIdFromWebsiteSession(websiteSession: WebsiteSession): BigInteger {
    val userAccountIdList = websiteSession.userAccountIds
    val sessionId = websiteSession.id ?: ApiException(ApiError.MISSING_SESSION_ID, "Session ID is unexpectedly null")
    return when {
      userAccountIdList.isNullOrEmpty() -> throw ApiException(
        ApiError.NO_USER_ACCOUNT_ID_FOUND,
        "No User Account Id was found in this website session",
        mapOf(LoggingAttributes.SESSION_ID to sessionId)
      )

      userAccountIdList.size > 1 -> throw ApiException(
        ApiError.MULTIPLE_USER_ACCOUNT_IDS_IN_WEBSITE_SESSION_FOUND,
        "Multiple user account ids were found in this website session",
        mapOf(LoggingAttributes.SESSION_ID to sessionId)
      )

      else -> userAccountIdList.first()
    }
  }

  private fun getUserAccountIdFromWebsiteSession(websiteSession: WebsiteSession): BigInteger {
    return websiteSession.userAccountId ?: throw ApiException(ApiError.NO_USER_ACCOUNT_ID_FOUND, "No User Account ID found for current web session with session ID ${websiteSession.sessionId} for user ${websiteSession.userIdentifier!!.value}")
  }

  private fun displayHandle(getHandleResponse: GetHandleResponse): String {
    return if(getHandleResponse.baseHandle == "" && getHandleResponse.handleSuffix == 0) "NO HANDLE FOUND"
    else "${getHandleResponse.baseHandle}.${getHandleResponse.handleSuffix}"
  }
}
