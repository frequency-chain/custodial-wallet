package io.amplica.custodial_wallet.service.frequency

import io.amplica.frequency.client.FrequencyClient
import io.amplica.frequency.client.FrequencyClientException
import io.amplica.frequency.client.SpRuntimeMultiSignatureType
import io.amplica.frequency.crypto.AccountKeyPair
import io.amplica.frequency.crypto.AccountPublicKey
import io.amplica.frequency.crypto.provider.Secp256K1CryptoProvider
import io.amplica.frequency.crypto.provider.Sr25519CryptoProvider
import io.amplica.frequency.crypto.toUniversalAddress
import io.amplica.frequency.payload.AddProviderPayload
import io.amplica.frequency.payload.CreateHandlePayload
import io.amplica.frequency.service.SigningService
import kotlinx.coroutines.future.await
import java.math.BigInteger

class DefaultFrequencyService(
  val accountPublicKey: AccountPublicKey,
  val signingService: SigningService,
  val frequencyClient: FrequencyClient,
  val properties: DefaultFrequencyServiceProperties,
) : FrequencyService {
  private suspend fun getExpiration(): Long {
    return frequencyClient.getLastBlockNumber().await().toLong() + properties.extrinsicExpirationBlocks
  }

  override suspend fun createUserAccount(userKeyPair: AccountKeyPair): Result<BigInteger> {
    val providerMsaId = frequencyClient.getMsaIdByAccountId(accountPublicKey.toUniversalAddress()).await()
      ?: return Result.failure(FrequencyClientException("Failed to get MSA ID for provider"))

    val payload = AddProviderPayload(providerMsaId, emptyList(), getExpiration())
    val signature = signingService.signPayload(userKeyPair, payload)
    val signatureType = when (userKeyPair.cryptoProvider) {
      is Sr25519CryptoProvider -> SpRuntimeMultiSignatureType.SR25519
      is Secp256K1CryptoProvider -> SpRuntimeMultiSignatureType.ECDSA
    }

    return frequencyClient.createSponsoredAccountWithDelegationWithCapacity(
      userKeyPair.toUniversalAddress(),
      signatureType,
      signature.bytes,
      payload.toScaleObject()
    ).await().fold(
      { Result.failure(it) },
      { delegationGranted ->
        val delegatorMsaId = delegationGranted.delegator
        if (delegatorMsaId != null) {
          Result.success(delegatorMsaId.value)
        } else {
          Result.failure(FrequencyClientException("Delegation granted response is missing the delegator MSA ID"))
        }
      }
    )
  }

  override suspend fun claimHandle(userKeyPair: AccountKeyPair, baseHandle: String): Result<String> {
    val expiration = frequencyClient.getLastBlockNumber().await().toLong() + properties.extrinsicExpirationBlocks
    val payload = CreateHandlePayload(baseHandle, expiration)
    val signature = signingService.signPayload(userKeyPair, payload)
    val signatureType = when (userKeyPair.cryptoProvider) {
      is Sr25519CryptoProvider -> SpRuntimeMultiSignatureType.SR25519
      is Secp256K1CryptoProvider -> SpRuntimeMultiSignatureType.ECDSA
    }

    return frequencyClient.claimHandleWithCapacity(
      userKeyPair.toUniversalAddress(),
      signatureType,
      signature.bytes,
      payload.toScaleObject()
    ).await().fold(
      { Result.failure(it) },
      { Result.success(it.handle) }
    )
  }

}