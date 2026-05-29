package io.amplica.custodial_wallet.client.notification

import java.math.BigInteger

data class SendSmsRequest(
  val destinationPhoneNumber: String,
  val messageBody: String,
  val verifiedMillis: BigInteger?,
  val userIp: String?,
)

data class SendSmsResponse(
  val messageId: String,
  val destinationPhoneNumber: String,
  val sourcePhoneNumber: String,
  val messageBody: String,
)

enum class PhoneNumberBlockReason {
  FORBIDDEN_PREFIX,
  UNREACHABLE,
  RATE_LIMIT_EXCEEDED,
}

sealed interface PhoneNumberBlockStatus{
  val isBlocked: Boolean

  data object NotBlocked: PhoneNumberBlockStatus {
    override val isBlocked = false
  }

  data class Blocked(
    val reason: PhoneNumberBlockReason
  ): PhoneNumberBlockStatus {
    override val isBlocked = true
  }
}

data class PhoneNumberLookupResponse(
  val blockStatus: PhoneNumberBlockStatus
)


interface NotificationServiceClient {
  suspend fun sendSms(
    sendSmsRequest: SendSmsRequest,
    sessionId: String?,
    xSessionId: String?,
    xTraceId: String?,
  ): SendSmsResponse

  suspend fun lookupPhoneNumber(phoneNumber: String): PhoneNumberLookupResponse

  suspend fun healthcheck(): Boolean
}
