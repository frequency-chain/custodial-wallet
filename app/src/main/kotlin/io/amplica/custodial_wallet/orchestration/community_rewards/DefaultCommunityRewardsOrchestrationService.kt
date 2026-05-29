package io.amplica.custodial_wallet.orchestration.community_rewards

import com.strategyobject.substrateclient.crypto.ss58.SS58AddressFormat
import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.client.claim.ClaimServiceClient
import io.amplica.custodial_wallet.client.claim.NonceResponse
import io.amplica.custodial_wallet.client.claim.UserAgreeRequest
import io.amplica.custodial_wallet.client.redis.dto.LoginPayloadRequest
import io.amplica.custodial_wallet.client.redis.dto.PayloadType
import io.amplica.custodial_wallet.client.redis.dto.TypedPayloadRequestWithSignature
import io.amplica.custodial_wallet.db.repository.CustodialWalletOptIn
import io.amplica.custodial_wallet.db.repository.KeyUsageType
import io.amplica.custodial_wallet.db.repository.OptInType
import io.amplica.custodial_wallet.db.spring.DelegatingTransactionalOperator
import io.amplica.custodial_wallet.internationalization.MessageFactory
import io.amplica.custodial_wallet.orchestration.LookupOrchestrationService
import io.amplica.custodial_wallet.orchestration.signing.SigningOrchestrationService
import io.amplica.custodial_wallet.service.key.KeyService
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import io.amplica.custodial_wallet.util.toHex
import io.amplica.custodial_wallet.util.toIso8601Format
import io.amplica.frequency.crypto.provider.Secp256K1CryptoProvider
import io.amplica.frequency.crypto.toPublicKeyBytes
import io.amplica.custodial_wallet.util.key_creation.KeyPairBytes
import io.amplica.custodial_wallet.util.key_creation.Sr25519KeyPairCreator
import java.math.BigInteger
import java.time.ZonedDateTime

class DefaultCommunityRewardsOrchestrationService(
  private val databaseService: CustodialWalletDatabaseService,
  private val claimServiceClient: ClaimServiceClient,
  private val caip122MessageFactory: MessageFactory,
  private val keyService: KeyService,
  private val lookupService: LookupOrchestrationService,
  private val signingOrchestrationService: SigningOrchestrationService,
  private val ss58AddressFormat: SS58AddressFormat,
  private val delegatingTransactionalOperator: DelegatingTransactionalOperator,
) : CommunityRewardsOrchestrationService {
  override suspend fun isOptedIn(userAccountId: BigInteger): Boolean {
    return databaseService.findOptInByUserAccountIdAndOptInType(
      userAccountId,
      OptInType.COMMUNITY_REWARDS
    )?.isOptedIn ?: false
  }

  override suspend fun optIn(userAccountId: BigInteger) {
    val userKeyPair = findAccountKeyPair(userAccountId)
    val nonceResponse = claimServiceClient.getNonce()
    val message = createMessage(nonceResponse, userKeyPair)
    val signature = signingOrchestrationService.signMessage(userKeyPair, message)
    val userPublicKey = userKeyPair.toPublicKeyDto(ss58AddressFormat)

    delegatingTransactionalOperator.executeReadWrite {
      //Save Community Rewards Opt In to DB
      databaseService.saveOptIn(
        CustodialWalletOptIn.create(userAccountId, OptInType.COMMUNITY_REWARDS, true)
      )
      //User Agree Using Claim Service
      claimServiceClient.userAgree(
        UserAgreeRequest(
          userPublicKey,
          listOf(
            TypedPayloadRequestWithSignature(
              signature,
              PayloadType.LOGIN,
              LoginPayloadRequest(message)
            )
          )
        )
      )
    }
  }

  override suspend fun optInAuthenticatedSiwaOrWebsiteSession(sessionId: String) {
    val userAccountId = lookupService
      .findWebsiteOrSiwaAuthenticatedUserDataOrThrow(sessionId)
      .userAccountId

    optIn(userAccountId)
  }

  private suspend fun findAccountKeyPair(userAccountId: BigInteger): KeyPairBytes {
    val userKeyData = lookupService.findUserKeyDataOrThrow(
      userAccountId,
      KeyUsageType.ACCOUNT,
      KeyPairType.SR25519,
    )

    return keyService.decryptUserAccountKeyData(userKeyData)
  }

  private fun createMessage(nonceResponse: NonceResponse, userKeyPair: KeyPairBytes): String {
    val version = 1
    val serializedUserAddress = when (userKeyPair.keyPairType) {
      KeyPairType.SR25519 -> {
        Sr25519KeyPairCreator.encodeSr25519PublicKey(
          userKeyPair.publicKeyBytes,
          ss58AddressFormat
        )
      }
      KeyPairType.SECP256K1 -> toHex(Secp256K1CryptoProvider.toUniversalAddress(userKeyPair.publicKeyBytes.toPublicKeyBytes()))
      else -> throw IllegalArgumentException("Unsupported KeyPairType ${userKeyPair.keyPairType}")
    }

    return caip122MessageFactory.createMessage(
      "caip122",
      mapOf(
        "domain" to nonceResponse.domain,
        "chainReference" to nonceResponse.chainId.split(":")[1],
        "userAddress" to serializedUserAddress,
        "uri" to nonceResponse.uri,
        "version" to version,
        "nonce" to nonceResponse.nonce,
        "issuedAt" to toIso8601Format(ZonedDateTime.now()),
      ),
      null,
      locale = null,
    )
  }

}