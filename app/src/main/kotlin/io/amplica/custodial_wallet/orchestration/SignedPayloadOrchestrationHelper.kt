package io.amplica.custodial_wallet.orchestration

import io.amplica.custodial_wallet.client.redis.dto.AddProviderPayloadRequest
import io.amplica.custodial_wallet.client.redis.dto.HandlePayloadRequest
import io.amplica.custodial_wallet.client.redis.dto.PayloadType

class AddProviderPayloadToComponentWithContext : TypedPayloadToComponentWithContextMapper<AddProviderPayloadRequest> {

  override fun mapToComponentWithContext(typedPayload: AddProviderPayloadRequest): ComponentWithContext {
    val name = PayloadType.ADD_PROVIDER
    return ComponentWithContext(
      name,
      mapOf(
        Pair("${name}MsaId", typedPayload.msaId),
        Pair("${name}SchemaIds", typedPayload.schemaIds),
        Pair("${name}Url", typedPayload.url))
    )
  }
}

class ClaimHandlePayloadToComponentWithContext : TypedPayloadToComponentWithContextMapper<HandlePayloadRequest>{

  override fun mapToComponentWithContext(typedPayload: HandlePayloadRequest): ComponentWithContext {
    val name = PayloadType.CLAIM_HANDLE
    return ComponentWithContext(
      name,
      mapOf(
        Pair("${name}BaseHandle", typedPayload.baseHandle)
      )
    )
  }
}