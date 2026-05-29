/**
 * DTOs used for communicating with the Notification Service via Rest that should not
 * leak into the CW project at large.
 */

package io.amplica.custodial_wallet.client.notification.dto

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

data class NotificationServiceSendSmsResponse(
  val id: String, // Canonical within the notification service
  val request: NotificationServiceSendSmsRequest,
  val details: NotificationServiceSentSmsDetails,
)

data class NotificationServiceSentSmsDetails(
  val platform: String,
  val platformMessageId: String,
  val sourcePhoneNumber: String,
)

data class NotificationServiceApiErrorResponse(
  val id: Int,
  val message: String,
  val stacktrace: String?,
)

data class NotificationServiceSendSmsRequest(
  val destinationPhoneNumber: String,
  val messageBody: String
)

data class NotificationServicePhoneNumberLookupResponse(
  val phoneNumber: String,
  val blockStatus: NotificationServiceBlockStatus,
)

// Instruct Jackson on how to deserialize correctly using the value of `isBlocked` as the "discriminator" between types
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "isBlocked")
@JsonSubTypes(
  JsonSubTypes.Type(value = NotificationServiceBlockStatus.NotBlocked::class, name = "false"),
  JsonSubTypes.Type(value = NotificationServiceBlockStatus.Blocked::class, name = "true"),
)
sealed interface NotificationServiceBlockStatus {
  val isBlocked: Boolean

  data object NotBlocked : NotificationServiceBlockStatus {
    override val isBlocked = false
  }

  data class Blocked(
    val reason: NotificationServiceBlockReason,
  ) : NotificationServiceBlockStatus {
    override val isBlocked = true
  }
}

enum class NotificationServiceBlockReason {
  FORBIDDEN_PREFIX,
  UNREACHABLE,
  RATE_LIMIT_EXCEEDED,
}
