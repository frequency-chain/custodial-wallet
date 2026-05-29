package io.amplica.custodial_wallet.orchestration

import arrow.core.Either
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.controller.util.ChangeExternalUserIdRequest
import io.amplica.custodial_wallet.controller.util.RebuildSignupPayloadRequest
import io.amplica.custodial_wallet.controller.util.RebuildSignupPayloadRequestByPublicKey
import io.amplica.custodial_wallet.db.repository.ProviderExternalUser
import io.amplica.custodial_wallet.db.repository.UserData
import io.amplica.custodial_wallet.dto.*
import java.math.BigInteger
import java.security.SecureRandom
import java.util.*

interface CustodialWalletOrchestrationService {
  suspend fun deleteUserByUserIdentifier(userIdentifier: UserIdentifier): DeleteUserResponse
  suspend fun deleteUserByDeleteUserByExternalIdRequest(deleteUserByExternalIdRequest: DeleteUserByExternalIdRequest): DeleteUserResponse
  suspend fun revokeDelegationAndHandle(providerUserIdentifier: ProviderUserIdentifier)
  suspend fun deleteUserAndRetireMsaByUserIdentifier(providerUserIdentifier: ProviderUserIdentifier): DeleteUserResponse
  suspend fun createSignedChangeHandlePayload(changeHandleRequest: ChangeHandleRequest): ChangeHandleResponse
  suspend fun sendLoginUrl(directLoginRequest: DirectLoginRequest, locale: Locale, userIp: String?, xCaptchaHeaderValue: String?): String
  suspend fun changeExternalUserId(changeExternalUserIdRequest: ChangeExternalUserIdRequest): ProviderExternalUser


  /**
   * Handle authentication, if a callbackUrl is detected in the session then this will result in a redirect, if not
   * it will resolve to a page handled by us with the context information needed for that page
   *
   * @param sessionId
   * @param authenticationCode
   * @param locale
   * @return
   */
  suspend fun authenticateLogin(sessionId: String, authenticationCode: String, locale: Locale): WebsiteSession

  suspend fun authenticateLoggedIn(sessionId: String, locale: Locale): WebsiteSession
  suspend fun loginValidateAuthorizationCode(authorizationCodeRequest: AuthorizationCodeRequest): AuthorizationWebsiteSessionResponse
  suspend fun handleAddNewIdentifierVerification(sessionId: String, verificationCode: String): AddIdentifierVerificationResponse

  suspend fun revokeDelegation(providerMsaId: BigInteger, userPublicKeyHex: String, sessionId: String): Boolean
  suspend fun sendNewIdentifierVerificationEmail(addIdentifierRequest: AddIdentifierRequest, sessionId: String, locale: Locale)
  suspend fun sendNewIdentifierVerificationSms(addIdentifierRequest: AddIdentifierRequest, sessionId: String, locale: Locale)
  suspend fun changeHandle(sessionId: String, newHandle: String): String

  suspend fun amplicaAccessLogout(sessionId: String)

  suspend fun createLoggedInSession(websiteSession: WebsiteSession): WebsiteSession
  suspend fun checkLoggedInState(sessionId: String?): Boolean
  suspend fun retrieveAccountInfoOrCallback(websiteSession: WebsiteSession): Either<AccountInfo, String>
  suspend fun createRedirectForCallback(websiteSession: WebsiteSession, callbackUrl: String): String
  suspend fun changePassword(sessionId: String, changePasswordRequest: ChangePasswordRequest)
  suspend fun authenticateUserWithPassword(passwordDirectLoginRequest: PasswordDirectLoginRequest, contactMethodType: UserIdentifierType): String
  suspend fun mapUserDataToProviderUserInfo(userDataList: List<UserData>): List<ProviderUserInfo>
}

internal fun generateToken(): String {
  val random: Random = SecureRandom()
  return (random.nextInt(900000) + 100000).toString()
}
