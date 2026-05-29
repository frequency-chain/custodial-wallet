package io.amplica.custodial_wallet.orchestration.passkey

import io.amplica.custodial_wallet.client.redis.dto.AcceptRegistrationRequest
import io.amplica.custodial_wallet.client.redis.dto.CredentialResponseDto
import io.amplica.custodial_wallet.client.redis.dto.CredentialResponsesDto
import java.math.BigInteger


interface PasskeyWalletService {
  val passkeyWalletPageIsEnabled: Boolean

  suspend fun acceptRegistrationRequest(
    sessionId: String,
    acceptRegistrationRequest: AcceptRegistrationRequest
  ): Boolean

  suspend fun retrieveCredentialAccount(
    sessionId: String,
    credentialId: String
  ): CredentialResponseDto

  suspend fun retrieveCredentials(
    sessionId: String,
  ): CredentialResponsesDto

  suspend fun getCallbackUrl(sessionId: String): String

  suspend fun walletExistsForAccount(userAccountId: BigInteger): Boolean

  suspend fun getAccountPublicKeyHexOrThrow(sessionId: String): String
}