package io.amplica.custodial_wallet.orchestration.community_rewards

import com.strategyobject.substrateclient.crypto.ss58.SS58AddressFormat
import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.client.claim.ClaimServiceClient
import io.amplica.custodial_wallet.client.claim.NonceResponse
import io.amplica.custodial_wallet.client.redis.dto.AuthenticatedUserData
import io.amplica.custodial_wallet.client.redis.dto.PayloadType
import io.amplica.custodial_wallet.db.repository.CustodialWalletOptIn
import io.amplica.custodial_wallet.db.repository.KeyUsageType
import io.amplica.custodial_wallet.db.repository.OptInType
import io.amplica.custodial_wallet.db.spring.DelegatingTransactionalOperator
import io.amplica.custodial_wallet.internationalization.MessageFactory
import io.amplica.custodial_wallet.orchestration.LookupOrchestrationService
import io.amplica.custodial_wallet.orchestration.signing.SigningOrchestrationService
import io.amplica.custodial_wallet.orchestration.siwa.ACCOUNT_KEY_PAIR
import io.amplica.custodial_wallet.orchestration.siwa.ACCOUNT_USER_KEY_DATA
import io.amplica.custodial_wallet.orchestration.siwa.LOGIN_SIGNATURE
import io.amplica.custodial_wallet.orchestration.siwa.USER_ACCOUNT_ID
import io.amplica.custodial_wallet.service.key.KeyService
import io.amplica.custodial_wallet.util.createTransactionalOperatorDouble
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.*
import java.time.Instant

class DefaultCommunityRewardsOrchestrationServiceTest {
  private val mockDatabaseService: CustodialWalletDatabaseService = mock()
  private val mockLookupService: LookupOrchestrationService = mock()
  private val mockClaimServiceClient: ClaimServiceClient = mock()
  private val mockCaip122MessageFactory: MessageFactory = mock()
  private val mockKeyService: KeyService = mock()
  private val mockSigningOrchestrationService: SigningOrchestrationService = mock()

  private val transactionalOperatorTestDouble = createTransactionalOperatorDouble()
  private val delegatingTransactionalOperator = DelegatingTransactionalOperator(transactionalOperatorTestDouble, transactionalOperatorTestDouble)
  private val service = DefaultCommunityRewardsOrchestrationService(
    mockDatabaseService,
    mockClaimServiceClient,
    mockCaip122MessageFactory,
    mockKeyService,
    mockLookupService,
    mockSigningOrchestrationService,
    SS58AddressFormat.SUBSTRATE_ACCOUNT,
    delegatingTransactionalOperator
  )

  private fun createMocksForSuccessfulOptIn() = runBlocking {
    whenever(
      mockLookupService.findAllUserKeyDataByUserAccountIdAndKeyUsageType(
        USER_ACCOUNT_ID,
        KeyUsageType.ACCOUNT
      )
    ).thenReturn(listOf(ACCOUNT_USER_KEY_DATA))
    whenever(
      mockLookupService.findUserKeyDataOrThrow(
        USER_ACCOUNT_ID,
        KeyUsageType.ACCOUNT,
        KeyPairType.SR25519,
      )
    ).thenReturn(ACCOUNT_USER_KEY_DATA)
    whenever(mockKeyService.decryptUserAccountKeyData(ACCOUNT_USER_KEY_DATA)).thenReturn(ACCOUNT_KEY_PAIR)
    whenever(mockClaimServiceClient.getNonce()).thenReturn(
      NonceResponse(
        "nonce",
        Instant.now().toEpochMilli(),
        Instant.now().toEpochMilli(),
        "domain",
        "uri",
        "frequency:chainReference",
        "statement"
      )
    )
    val message = "someMessage"
    whenever(mockCaip122MessageFactory.createMessage(eq("caip122"), any(), isNull(), isNull())).thenReturn(
      message
    )
    whenever(mockSigningOrchestrationService.signMessage(ACCOUNT_KEY_PAIR, message)).thenReturn(
      LOGIN_SIGNATURE
    )
  }

  @Test
  fun isOptedInCorrectlyReturnsTrue(): Unit = runBlocking {
    //GIVEN
    whenever(mockDatabaseService.findOptInByUserAccountIdAndOptInType(USER_ACCOUNT_ID, OptInType.COMMUNITY_REWARDS)).thenReturn(
      CustodialWalletOptIn.create(USER_ACCOUNT_ID, OptInType.COMMUNITY_REWARDS, true))
    //WHEN
    val result = service.isOptedIn(USER_ACCOUNT_ID)
    //THEN
    Assertions.assertThat(result).isTrue()
  }

  @Test
  fun isOptedInCorrectlyReturnsFalse(): Unit = runBlocking {
    //GIVEN
    whenever(mockDatabaseService.findOptInByUserAccountIdAndOptInType(USER_ACCOUNT_ID, OptInType.COMMUNITY_REWARDS)).thenReturn(
      CustodialWalletOptIn.create(USER_ACCOUNT_ID, OptInType.COMMUNITY_REWARDS, false))
    //WHEN
    val result = service.isOptedIn(USER_ACCOUNT_ID)
    //THEN
    Assertions.assertThat(result).isFalse()
  }

  @Test
  fun isOptedInCorrectlyReturnsFalseWhenNoDbEntryFound(): Unit = runBlocking {
    //GIVEN
    whenever(mockDatabaseService.findOptInByUserAccountIdAndOptInType(USER_ACCOUNT_ID, OptInType.COMMUNITY_REWARDS)).thenReturn(null)
    //WHEN
    val result = service.isOptedIn(USER_ACCOUNT_ID)
    //THEN
    Assertions.assertThat(result).isFalse()
  }

  @Test
  fun successfullyOptIn(): Unit = runBlocking {
    //GIVEN
    createMocksForSuccessfulOptIn()

    //WHEN
    service.optIn(USER_ACCOUNT_ID)

    //THEN
    verify(mockDatabaseService, times(1)).saveOptIn(argThat { optIn ->
      optIn.isOptedIn && optIn.optInType == OptInType.COMMUNITY_REWARDS && optIn.userAccountId == USER_ACCOUNT_ID
    })
    verify(mockClaimServiceClient, times(1)).userAgree(argThat { userAgreeRequest ->
      userAgreeRequest.payloads[0].type == PayloadType.LOGIN
    })
  }

  @Test
  fun successfullyOptInWithSession(): Unit = runBlocking {
    // GIVEN
    val sessionId = "a1b2-c3d4"

    createMocksForSuccessfulOptIn()
    whenever(mockLookupService.findWebsiteOrSiwaAuthenticatedUserDataOrThrow(eq(sessionId))).thenReturn(
      AuthenticatedUserData(USER_ACCOUNT_ID)
    )

    // WHEN
    service.optInAuthenticatedSiwaOrWebsiteSession(sessionId)

    // THEN
    verify(mockDatabaseService, times(1)).saveOptIn(argThat { optIn ->
      optIn.isOptedIn && optIn.optInType == OptInType.COMMUNITY_REWARDS && optIn.userAccountId == USER_ACCOUNT_ID
    })
    verify(mockClaimServiceClient, times(1)).userAgree(argThat { userAgreeRequest ->
      userAgreeRequest.payloads[0].type == PayloadType.LOGIN
    })
  }
}