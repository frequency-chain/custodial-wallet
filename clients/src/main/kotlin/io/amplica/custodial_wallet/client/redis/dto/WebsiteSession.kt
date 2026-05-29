package io.amplica.custodial_wallet.client.redis.dto

import java.math.BigInteger

data class WebsiteSession(
  var id: String?,
  var callbackUrl: String?,
  var userIdentifier: UserIdentifier? = null,
  var userAccountIds: List<BigInteger>? = null,
  var authenticationCode: String? = null,
  var msaId: BigInteger? = null,
  var providerExternalUserId: BigInteger? = null,
  var userAccountId: BigInteger? = null, // what is this used for? Because it's never set, marking for PR
  var verificationCode: String? = null,
  var sessionId: String? = null,
  var addIdentifier: UserIdentifier? = null,
  var providerMsaId: BigInteger? = null,
  var publicKeyHex: String? = null,
  var loggedIn: UserState = UserState.LOGGED_OUT,
  var incorrectTokenRetries: Int = 0,
  var authorizationCode: String? = null,
) {
  fun addIdentifier(newIdentifier: UserIdentifier, userAccountId: BigInteger, verificationCode: String, sessionId: String) {
    addIdentifier = newIdentifier
    this.userAccountId = userAccountId
    this.verificationCode = verificationCode
    this.sessionId = sessionId
  }
}

/**
 * AuthorizationCode website session response, the intent of this class to be the response of an authorization code
 * redirect, basically a web friendly style oauth where the caller resolves this because they have the authorizationCode
 * and sessionId that we passed them to resolve an m
 * saId currently but we could presumably add non-private info here.
 *
 * @property msaId
 * @property msaIdAsString for JS clients
 * @constructor Create empty Authorization website session response
 */
data class AuthorizationWebsiteSessionResponse (
  val msaId: BigInteger,
  val msaIdAsString: String,
)
