package io.amplica.custodial_wallet.orchestration.ics

data class DefaultIcsUserOrchestrationProperties(
  val signupBlockExpiration: Long,
  val storageServiceHostName: String,
  val canReturnExistingContextItemKey: Boolean,
)
