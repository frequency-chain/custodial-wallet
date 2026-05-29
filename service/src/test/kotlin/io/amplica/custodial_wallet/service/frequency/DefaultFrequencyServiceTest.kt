package io.amplica.custodial_wallet.service.frequency

import arrow.core.Either
import io.amplica.custodial_wallet.util.fromHex
import io.amplica.frequency.client.*
import io.amplica.frequency.crypto.AccountKeyPair
import io.amplica.frequency.crypto.provider.Secp256K1CryptoProvider
import io.amplica.frequency.crypto.provider.Sr25519CryptoProvider
import io.amplica.frequency.crypto.toSignatureBytes
import io.amplica.frequency.crypto.toUniversalAddress
import io.amplica.frequency.payload.AddProviderPayload
import io.amplica.frequency.payload.CreateHandlePayload
import io.amplica.frequency.service.SigningService
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture

enum class UserKeyPairType {
  SR25519,
  SECP256K1,
}

class DefaultFrequencyServiceTest {

  private val providerPublicKey = Sr25519CryptoProvider.createKeyPair().toPublicKey()
  private val mockSigningService: SigningService = mock()
  private val mockFrequencyClient: FrequencyClient = mock()
  private val properties = DefaultFrequencyServiceProperties(10)

  private val service = DefaultFrequencyService(
    providerPublicKey,
    mockSigningService,
    mockFrequencyClient,
    properties,
  )

  private fun createKeyPair(userKeyPairType: UserKeyPairType): AccountKeyPair = when (userKeyPairType) {
    UserKeyPairType.SR25519 -> Sr25519CryptoProvider.createKeyPair()
    UserKeyPairType.SECP256K1 -> Secp256K1CryptoProvider.createKeyPair()
  }

  private fun getExpectedSignatureType(userKeyPairType: UserKeyPairType): SpRuntimeMultiSignatureType = when (userKeyPairType) {
    UserKeyPairType.SR25519 -> SpRuntimeMultiSignatureType.SR25519
    UserKeyPairType.SECP256K1 -> SpRuntimeMultiSignatureType.ECDSA
  }

  @ParameterizedTest
  @CsvSource(value = [
    "SR25519",
    "SECP256K1",
  ])
  fun createUserAccountSucceeds(userKeyPairType: UserKeyPairType): Unit = runBlocking {
    // GIVEN
    val userKeyPair = createKeyPair(userKeyPairType)

    val latestBlockNumber = 80
    val signature = fromHex("8a5ef4d7c42569f32ab08d9ff4ece32008bdc96bbe92b9fdc956459f5b0aa0a0")
    val providerMsaId = 25.toBigInteger()
    val userMsaId = 26.toBigInteger()

    whenever(
      mockFrequencyClient.getLastBlockNumber()
    ).thenReturn(CompletableFuture.completedFuture(latestBlockNumber.toBigInteger()))

    whenever(
      mockFrequencyClient.getMsaIdByAccountId(eq(providerPublicKey.publicKeyBytes.bytes))
    ).thenReturn(CompletableFuture.completedFuture(providerMsaId))

    val expectedPayload = AddProviderPayload(providerMsaId, emptyList(), latestBlockNumber + properties.extrinsicExpirationBlocks)
    whenever(
      mockSigningService.signPayload(
        eq(userKeyPair),
        eq(expectedPayload)
      )
    ).thenReturn(signature.toSignatureBytes())

    val expectedSignatureType = getExpectedSignatureType(userKeyPairType)
    val mockResponse = Either.Right(
      DelegationGranted(
        MessageSourceId(providerMsaId),
        MessageSourceId(userMsaId),
      )
    )

    whenever(mockFrequencyClient.createSponsoredAccountWithDelegationWithCapacity(
      eq(userKeyPair.toUniversalAddress()),
      eq(expectedSignatureType),
      eq(signature),
      any(),
    )).thenReturn(CompletableFuture.completedFuture(mockResponse))

    // WHEN
    val result = service.createUserAccount(userKeyPair)

    // THEN
    Assertions.assertThat(result.getOrThrow()).isEqualTo(userMsaId)
  }

  @ParameterizedTest
  @CsvSource(value = [
    "SR25519",
    "SECP256K1",
  ])
  fun claimHandleSucceeds(userKeyPairType: UserKeyPairType): Unit = runBlocking {
    // GIVEN
    val userKeyPair = createKeyPair(userKeyPairType)

    val latestBlockNumber = 80
    val signature = fromHex("8a5ef4d7c42569f32ab08d9ff4ece32008bdc96bbe92b9fdc956459f5b0aa0a0")
    val userMsaId = 26.toBigInteger()
    val handle = "handle"

    whenever(
      mockFrequencyClient.getLastBlockNumber()
    ).thenReturn(CompletableFuture.completedFuture(latestBlockNumber.toBigInteger()))

    val expectedPayload = CreateHandlePayload(handle, latestBlockNumber + properties.extrinsicExpirationBlocks)
    whenever(
      mockSigningService.signPayload(
        eq(userKeyPair),
        eq(expectedPayload)
      )
    ).thenReturn(signature.toSignatureBytes())

    val expectedSignatureType = getExpectedSignatureType(userKeyPairType)
    val mockResponse = Either.Right(
      HandleClaimed(
        MessageSourceId(userMsaId),
        handle
      )
    )
    whenever(mockFrequencyClient.claimHandleWithCapacity(
      eq(userKeyPair.toUniversalAddress()),
      eq(expectedSignatureType),
      eq(signature),
      any(),
    )).thenReturn(CompletableFuture.completedFuture(mockResponse))


    // WHEN
    val result = service.claimHandle(userKeyPair, handle)

    // THEN
    Assertions.assertThat(result.getOrThrow()).isEqualTo(handle)
  }
}