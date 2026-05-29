package io.amplica.custodial_wallet.orchestration.community_rewards

import java.math.BigInteger

interface CommunityRewardsOrchestrationService {
  suspend fun isOptedIn(userAccountId: BigInteger): Boolean
  suspend fun optIn(userAccountId: BigInteger)
  suspend fun optInAuthenticatedSiwaOrWebsiteSession(sessionId: String)
}