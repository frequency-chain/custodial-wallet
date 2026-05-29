package io.amplica.custodial_wallet.orchestration

import io.amplica.custodial_wallet.client.redis.dto.*
import java.util.*

typealias ComponentContext = Map<String, *>

data class AuthorizationCodeAndCallback(val authorizationCode: String, val callback: String)

data class ComponentWithContext(val component: String, val context: ComponentContext)



interface TypedPayloadToComponentWithContextMapper<TYPED_PAYLOAD> {
  fun mapToComponentWithContext(typedPayload: TYPED_PAYLOAD): ComponentWithContext
}

interface SignedPayloadOrchestrationService {
  suspend fun persistBatchPayloadToSign(batchPayloadToSignRequest: BatchPayloadToSignRequest): String
  suspend fun getPermissionsContextForBatchPayloadToSign(sessionId: String, locale: Locale): List<ComponentWithContext>
  suspend fun sendAuthenticationCode(sessionId: String, locale: Locale): UserIdentifier
  suspend fun generateAuthorizationCode(sessionId: String, authenticationCode: String): AuthorizationCodeAndCallback
  suspend fun retrieveBatchSignedPayload(sessionId: String, authorizationCode: String): BatchSignedPayloadResponse
}
