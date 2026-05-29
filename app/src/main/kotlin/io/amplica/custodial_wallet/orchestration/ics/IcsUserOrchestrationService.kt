package io.amplica.custodial_wallet.orchestration.ics

import io.amplica.custodial_wallet.client.redis.dto.*

interface IcsUserOrchestrationService {

  /**
   * The implication of this call is will get the "bootstrapping" payloads to set up the chain state in order to execute
   * on the HCP for KeyManagement and also for the user administration side of the ContextGroups and Items
   */
  suspend fun retrieveUserPayloads(request: IcsRetrievePayloadsSignedRequest): IcsRetrievePayloadsResponse

  /**
   * Retrieves the ContextGroupKey driven by the payloads submitted to the chain that were obtained in `HcpUserOrchestrationService.retrieveHcpUserPayloads`
   */
  suspend fun retrieveContextGroupKey(request: IcsContextGroupKeySignedRequest): IcsContextGroupKeyResponse

  /**
   * retrieves the ContextItemKey as derived by the HCP Key
   */
  suspend fun retrieveContextItemKey(request: IcsContextItemKeySignedRequest): IcsContextItemKeyResponse
}
