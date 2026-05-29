package io.amplica.custodial_wallet.client.redis.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.amplica.custodial_wallet.util.key_creation.EncodedBytes
import java.math.BigInteger
import java.net.URI


sealed interface PayloadRequest

data class AddProviderPayloadRequest (
  @JsonProperty("msaId") val msaId: BigInteger,
  @JsonProperty("schemaIds") val schemaIds: List<Int>,
  @JsonProperty("url") val url: URI?
): PayloadRequest

data class HandlePayloadRequest(
  @JsonProperty("baseHandle") val baseHandle: String
): PayloadRequest

data class LoginPayloadRequest(
  @JsonProperty("message") val message: String
): PayloadRequest


sealed interface PayloadResponse

data class AddProviderPayloadResponse(
  val authorizedMsaId: BigInteger,
  val schemaIds: List<Int>,
  val expiration: Long
): PayloadResponse

data class HandlePayloadResponse(
  val baseHandle: String,
  val expiration: Long
): PayloadResponse

data class Caip122LoginPayloadResponse(
  val message: String
): PayloadResponse

/**
 * Used for operations that need to store things (e.g., graph public keys)
 */
data class ItemizedSignaturePayloadResponse(
  val schemaId: Int,
  val targetHash: Long,
  val expiration: Long,
  val actions: List<ItemAction>
): PayloadResponse

data class PaginatedUpsertSignaturePayloadResponse(
  val schemaId: Int,
  val pageId: Int,
  val targetHash: BigInteger,
  val expiration: Long,
  val payload: EncodedBytes,
): PayloadResponse

data class PaginatedDeleteSignaturePayloadResponse(
  val schemaId: Int,
  val pageId: Int,
  val targetHash: BigInteger,
  val expiration: Long,
): PayloadResponse

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
  JsonSubTypes.Type(value = AddItemAction::class, name = "addItem")
)
sealed interface ItemAction

data class AddItemAction(
  val payloadHex: String,
): ItemAction
