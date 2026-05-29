package io.amplica.custodial_wallet.orchestration.deliverability

import io.amplica.custodial_wallet.client.redis.dto.UserIdentifier

interface DeliverabilityOrchestrationService {
  suspend fun getDeliverability(userIdentifier: UserIdentifier): Boolean
}