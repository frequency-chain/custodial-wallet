package io.amplica.custodial_wallet.client.notification

import io.amplica.custodial_wallet.client.notification.dto.*
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.web.X_SESSION_ID_NAME
import io.amplica.custodial_wallet.web.X_TRACE_ID_NAME
import io.amplica.custodial_wallet.web.X_UNFINISHED_SESSION_ID_NAME
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody


class RestNotificationServiceClient(private val webClient: WebClient) : NotificationServiceClient {

  companion object {
    data class HealthCheckResponse(val status: String)
  }

  override suspend fun sendSms(
    sendSmsRequest: SendSmsRequest,
    sessionId: String?,
    xSessionId: String?,
    xTraceId: String?,
  ): SendSmsResponse {
    val response = webClient.post()
      .uri("/api/sms/send")
      .header(X_UNFINISHED_SESSION_ID_NAME, sessionId)
      .header(X_SESSION_ID_NAME, xSessionId)
      .header(X_TRACE_ID_NAME, xTraceId)
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(sendSmsRequest)
      .retrieve()
      .onStatus(HttpStatusCode::isError) { clientResponse ->
        clientResponse.bodyToMono(NotificationServiceApiErrorResponse::class.java)
          .map { errorResponse ->
            mapNotificationServiceErrorResponseToApiException(
              errorResponse,
              "An error occurred attempting to send an SMS to ${sendSmsRequest.destinationPhoneNumber}"
            )
          }
      }
      .awaitBody<NotificationServiceSendSmsResponse>()
    return SendSmsResponse(
      response.id,
      response.request.destinationPhoneNumber,
      response.details.sourcePhoneNumber,
      sendSmsRequest.messageBody
    )
  }

  override suspend fun lookupPhoneNumber(phoneNumber: String): PhoneNumberLookupResponse {
    val response = webClient.get()
      .uri("/api/lookup/phoneNumber/${phoneNumber}")
      .retrieve()
      .onStatus(HttpStatusCode::isError) { clientResponse ->
        clientResponse.bodyToMono(NotificationServiceApiErrorResponse::class.java)
          .map { errorResponse ->
            mapNotificationServiceErrorResponseToApiException(
              errorResponse,
              "An error occurred attempting to perform a lookup for phone number ${phoneNumber}",
            )
          }
      }
      .awaitBody<NotificationServicePhoneNumberLookupResponse>()

    val blockStatus = when (response.blockStatus) {
      is NotificationServiceBlockStatus.Blocked -> {
        val reason = when (response.blockStatus.reason) {
          NotificationServiceBlockReason.FORBIDDEN_PREFIX -> PhoneNumberBlockReason.FORBIDDEN_PREFIX
          NotificationServiceBlockReason.UNREACHABLE -> PhoneNumberBlockReason.UNREACHABLE
          NotificationServiceBlockReason.RATE_LIMIT_EXCEEDED -> PhoneNumberBlockReason.RATE_LIMIT_EXCEEDED
        }
        PhoneNumberBlockStatus.Blocked(reason)
      }
      is NotificationServiceBlockStatus.NotBlocked -> PhoneNumberBlockStatus.NotBlocked
    }

    return PhoneNumberLookupResponse(blockStatus)
  }

  override suspend fun healthcheck(): Boolean {
    val response = webClient.get()
      .uri("/actuator/health")
      .retrieve()
      .awaitBody<HealthCheckResponse>()

    return response.status == "UP"
  }

  private fun mapNotificationServiceErrorResponseToApiException(
    errorResponse: NotificationServiceApiErrorResponse,
    fallbackMessage: String
  ): ApiException {
    val apiError = when (errorResponse.id) {
      1 -> ApiError.INVALID_SHARED_SECRET
      2, 3, 4 -> ApiError.BLOCKED_CONTACT_METHOD
      5 -> ApiError.ALL_SMS_CLIENTS_FAILED
      else -> ApiError.UNKNOWN_NOTIFICATION_SERVICE_ERROR
    }

    val message = when (errorResponse.id) {
      2, 3, 4 -> errorResponse.message // Sms block/ban errors pass through the message from the notification service
      else -> fallbackMessage
    }

    return ApiException(apiError, message)
  }

}
