package io.amplica.custodial_wallet.controller

import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiErrorDto
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.web.ContextLoggerHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.server.MissingRequestValueException
import org.springframework.web.server.ServerWebInputException
import org.springframework.web.server.UnsatisfiedRequestParameterException
import java.nio.channels.ClosedChannelException

abstract class AbstractApiController(private val enableStackTrace: Boolean) {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(AbstractApiController::class.java)
  }

  @ExceptionHandler
  fun handleApiException(apiException: ApiException, request: ServerHttpRequest): ResponseEntity<ApiErrorDto> {
    val apiError = apiException.apiError
    val httpStatus = apiError.httpStatus
    val headersString = ContextLoggerHelper.extractHeadersAsString(request)
    val message = "apiError=$apiError ${apiException.message} $headersString"
    if (httpStatus.is4xxClientError) {
      ContextLoggerHelper.logError(apiException, message, LOG, Level.WARN)
    } else {
      ContextLoggerHelper.logError(apiException, message, LOG, Level.ERROR)
    }
    return ResponseEntity(ApiErrorDto(
      apiError.id,
      apiError.description,
      if(enableStackTrace) apiException.stackTraceToString() else null
    ), apiError.httpStatus)
  }

  @ExceptionHandler
  fun handleClosedChannelException(exception: ClosedChannelException, request: ServerHttpRequest) {
    val headersString = ContextLoggerHelper.extractHeadersAsString(request)
    val unknownApiError = ApiError.UNKNOWN_ERROR
    LOG.error("apiError=${unknownApiError} ${exception.message} $headersString", exception)
  }

  @ExceptionHandler
  fun catchAllHandler(exception: Exception, request: ServerHttpRequest): ResponseEntity<ApiErrorDto> {
    val headersString = ContextLoggerHelper.extractHeadersAsString(request)

    val apiError = when (exception) {
      is MissingRequestValueException -> ApiError.NULL_REQUIRED_FIELD_ERROR
      is UnsatisfiedRequestParameterException -> ApiError.NULL_REQUIRED_FIELD_ERROR
      is ServerWebInputException -> ApiError.INVALID_REQUEST
      else -> ApiError.UNKNOWN_ERROR
    }

    LOG.error("apiError=${apiError} ${exception.message} $headersString", exception)
    return ResponseEntity(ApiErrorDto(
      apiError.id,
      exception.message ?: apiError.description,
      if(enableStackTrace) exception.stackTraceToString() else null
    ), apiError.httpStatus)
  }
}