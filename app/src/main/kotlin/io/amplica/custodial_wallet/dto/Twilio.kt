package io.amplica.custodial_wallet.dto

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import io.amplica.custodial_wallet.util.prefixKeysInMap

/**
 * https://www.twilio.com/docs/usage/troubleshooting/debugging-event-webhooks
 */
data class DebuggingEventPayload(
  @JsonProperty("resource_sid") val resourceSid: String?,
  @JsonProperty("service_sid") val serviceSid: String?,
  @JsonProperty("error_code") val errorCode: String?,
  @JsonProperty("more_info") val moreInfo: DebuggingEventDetails?,
  // Ignoring 'webhook' for now since it seems to not provide any valuable information
) {
  fun toFlatMap(prefix: String? = null): Map<String, String?> {
    val map = mapOf(
      "resourceSid" to resourceSid,
      "serviceSid" to serviceSid,
      "errorCode" to errorCode,
    ) + (moreInfo?.toFlatMap("moreInfo") ?: emptyMap())

    return if (!prefix.isNullOrEmpty()) prefixKeysInMap(map, prefix) else map
  }
}

data class DebuggingEventDetails(
  @JsonAlias("Msg") val msg: String?,
  val sourceComponent: String?,
  val ErrorCode: String?,
  val httpResponse: String?,
  val url: String?,
  val LogLevel: String?
) {
  fun toFlatMap(prefix: String? = null): Map<String, String?> {
    val map = mapOf(
      "msg" to msg,
      "sourceComponent" to sourceComponent,
      "ErrorCode" to ErrorCode,
      "httpResponse" to httpResponse,
      "url" to url,
      "LogLevel" to LogLevel
    )

    return if (!prefix.isNullOrEmpty()) prefixKeysInMap(map, prefix) else map
  }
}