package io.amplica.custodial_wallet.orchestration.siwa.io.amplica.custodial_wallet.orchestration.ics

import io.amplica.custodial_wallet.client.redis.dto.ItemizedSignaturePayloadResponse
import io.amplica.custodial_wallet.client.redis.dto.TypedPayloadResponseWithSignature

data class MockIcsPayloads(
  val publicKeyRegistration: TypedPayloadResponseWithSignature<ItemizedSignaturePayloadResponse>,
  val contextGroupAcl: TypedPayloadResponseWithSignature<ItemizedSignaturePayloadResponse>,
)
