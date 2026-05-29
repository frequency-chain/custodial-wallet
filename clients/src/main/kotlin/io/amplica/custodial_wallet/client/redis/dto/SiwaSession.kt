package io.amplica.custodial_wallet.client.redis.dto

import io.amplica.custodial_wallet.client.redis.generateUUID
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import java.math.BigInteger


sealed interface SiwaSession {
  val siwaRequest: SiwaRequest
  val id: String
  val userIdentifier: UserIdentifier?
  val fullCallbackUrl: String
  val userKeyPairType: KeyPairType
  val flowKind: SiwaFlowKind

  suspend fun <RETURN_TYPE> fold(
    ifUnauthenticatedSession: suspend (UnauthenticatedSiwaSession) -> RETURN_TYPE,
    ifAuthenticatedSiwaSession: suspend (AuthenticatedSiwaSession) -> RETURN_TYPE
  ) = when (this) {
    is UnauthenticatedSiwaSession -> ifUnauthenticatedSession(this)
    is AuthenticatedSiwaSession -> ifAuthenticatedSiwaSession(this)
  }
}

/** Represents a 'pre-authentication' or 'anonymous' session */
data class UnauthenticatedSiwaSession(
  override val siwaRequest: SiwaRequest,
  override val id: String = generateUUID(), // Session ID
  override val fullCallbackUrl: String,
  override val userKeyPairType: KeyPairType,
  override val flowKind: SiwaFlowKind,
  override val userIdentifier: UserIdentifier? = null,
  val authentication: IdentifierVerification? = null,
  val prefillUserHandle: String? = null,
): SiwaSession

/**
 * Represents the specific 'use-case' of SIWA (historically it was only social).
 */
enum class SiwaFlowKind {
  SOCIAL,
  ICS,
}

/**
 * A description of the payload response needed to satisfy the provider's SIWA request (keys, payloads, etc.)
 * given the state of the user in the custodial wallet and on the chain.
 */
sealed interface SiwaIntent {

  val sendGraphKeyPair: Boolean

  /**
   * Applies to both existing and new custodial wallet users that need to have some payloads generated
   * and submitted to the blockchain in order to satisfy the provider's request.
   *
   * E.g.
   * - New user "sign up" flow
   * - The "self-healing" case (when a provider fails to establish the user on the blockchain)
   * - "Migration" to a new provider
   */
  data class UpdateBlockchain(
    val operations: List<SiwaBlockchainOperation>,
    override val sendGraphKeyPair: Boolean,
  ) : SiwaIntent

  /**
   * Applies to existing custodial wallet user_accounts where the blockchain state already matches the request from the
   * provider. Nothing is submitted to the blockchain.
   */
  data class Login(
    val userAccountId: BigInteger,
    override val sendGraphKeyPair: Boolean,
  ) : SiwaIntent

  /**
   * Applies to new custodial wallet users targeting the ICS use-case
   */
  data class CreateSponsoredAccountAndLogin(
    val claimHandle: Boolean,
    override val sendGraphKeyPair: Boolean,
  ) : SiwaIntent
}

sealed interface SiwaBlockchainOperation {
  data class CreateAccountAndDelegatePermissions(
    val providerMsaId: BigInteger,
    val schemaIds: List<Int>
  ) : SiwaBlockchainOperation

  data class DelegatePermissions(
    val providerMsaId: BigInteger,
    val schemaIds: List<Int>
  ) : SiwaBlockchainOperation

  data object ClaimHandle : SiwaBlockchainOperation

  data object RegisterPrivateGraphKey : SiwaBlockchainOperation
}


data class SiwaPayloadsUserInput(
  val acceptsPermissions: Boolean?,
  val handle: String?
)

/** Represents a session corresponding to an FA user who has authenticated themselves to us */
data class AuthenticatedSiwaSession(
  override val siwaRequest: SiwaRequest,
  override val id: String = generateUUID(), // Session ID
  override val userIdentifier: UserIdentifier,
  override val fullCallbackUrl: String,
  override val userKeyPairType: KeyPairType,
  override val flowKind: SiwaFlowKind,
  val intent: SiwaIntent? = null,
  val userAccountId: BigInteger? = null,
  val userInput: SiwaPayloadsUserInput? = null,
  val authorizationCode: String? = null,
  val prefillUserHandle: String? = null,
): SiwaSession {
  fun downgradeSiwaSession(): UnauthenticatedSiwaSession {
    return UnauthenticatedSiwaSession(
      siwaRequest,
      fullCallbackUrl = fullCallbackUrl,
      userKeyPairType = userKeyPairType,
      flowKind = flowKind,
    )
  }
}

data class AuthenticatedUserData(
  val userAccountId: BigInteger
)
