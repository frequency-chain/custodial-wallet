package io.amplica.custodial_wallet.client.redis.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.As
import com.fasterxml.jackson.annotation.JsonValue
import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto

object PayloadType {
  const val ADD_PROVIDER = "addProvider"
  const val CLAIM_HANDLE = "claimHandle"
  const val LOGIN = "login"
  const val ITEM_ACTIONS = "itemActions"
  const val PAGINATED_UPSERT = "paginatedUpsert"
  const val PAGINATED_DELETE = "paginatedDelete"
}

enum class DebugDescription(@JsonValue val value: String) {
  HCP_PUBLIC_KEY_PAYLOAD("hcpPublicKeyPayload"),
  HCP_ACL_PAYLOAD("hclAclPayload"),
}

class TypedPayloadRequestWithSignature<out T: PayloadRequest>(
  @JsonProperty("signature") val signature: Signature,
  @JsonProperty("type") val type: String,
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = As.EXTERNAL_PROPERTY)
  @JsonSubTypes(
    value = [
      JsonSubTypes.Type(value = AddProviderPayloadRequest::class, name = PayloadType.ADD_PROVIDER),
      JsonSubTypes.Type(value = HandlePayloadRequest::class, name = PayloadType.CLAIM_HANDLE),
      JsonSubTypes.Type(value = LoginPayloadRequest::class, name = PayloadType.LOGIN),
    ]
  )
  @JsonProperty("payload") val payload: T
)

class TypedPayloadResponseWithSignature<T: PayloadResponse>(
  @JsonProperty("signature") val signature: Signature,
  @JsonProperty("endpoint") val endpoint: FrequencyEndpoint?,
  @JsonProperty("debugDescription") val debugDescription: DebugDescription?,
  @JsonProperty("type") val type: String,
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = As.EXTERNAL_PROPERTY)
  @JsonSubTypes(
    value = [
      JsonSubTypes.Type(value = AddProviderPayloadResponse::class, name = PayloadType.ADD_PROVIDER),
      JsonSubTypes.Type(value = HandlePayloadResponse::class, name = PayloadType.CLAIM_HANDLE),
      JsonSubTypes.Type(value = Caip122LoginPayloadResponse::class, name = PayloadType.LOGIN),
      JsonSubTypes.Type(value = ItemizedSignaturePayloadResponse::class, name = PayloadType.ITEM_ACTIONS),
      JsonSubTypes.Type(value = PaginatedUpsertSignaturePayloadResponse::class, name = PayloadType.PAGINATED_UPSERT),
      JsonSubTypes.Type(value = PaginatedDeleteSignaturePayloadResponse::class, name = PayloadType.PAGINATED_DELETE),
    ]
  )
  @JsonProperty("payload") val payload: T
)

data class BatchPayloadToSignRequest(
  @JsonProperty("sessionId") val id: String?,
  @JsonProperty("externalUserId") val externalUserId: String,
  @JsonProperty("userIdentifier") val userIdentifier: UserIdentifier,
  @JsonProperty("publicKey") val publicKey: PublicKeyDto,
  @JsonProperty("callback") val callback: String,
  @JsonProperty("payloads") val payloads: List<TypedPayloadRequestWithSignature<PayloadRequest>>,
  @JsonProperty("authenticationCode") val authenticationCode: String?,
  @JsonProperty("authorizationCode") val authorizationCode: String?,
)

data class PayloadToSignRequest<T>(
  @JsonProperty("id") val id: String?,
  @JsonProperty("externalUserId") val externalUserId: String,
  @JsonProperty("userIdentifier") val userIdentifier: UserIdentifier,
  @JsonProperty("publicKey") val publicKey: PublicKeyDto,
  @JsonProperty("signature") val signature: Signature,
  @JsonProperty("callback") val callback: String,
  @JsonProperty("payload") val payload: T,
  @JsonProperty("authenticationCode") val authenticationCode: String?,
  @JsonProperty("authorizationCode") val authorizationCode: String?,
)

interface WithSignature {
  val signature: Signature
}

data class BatchSignedPayloadResponse(
  val externalUserId: String,
  val userIdentifier: UserIdentifier,
  // NOTE(Julian, 2024-08-14): The type of List elements should be '*Response*' instead of '*Request*',
  // but we are going to revisit the OAuth flow once SIWA is complete, so I am leaving this as originally written.
  val payloads: List<TypedPayloadRequestWithSignature<PayloadRequest>>
)
