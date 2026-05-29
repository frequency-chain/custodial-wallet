package io.amplica.custodial_wallet.controller.util

import com.fasterxml.jackson.databind.ObjectMapper
import io.amplica.custodial_wallet.controller.AbstractApiController
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.web.ContextLoggerHelper
import org.slf4j.Logger
import org.slf4j.event.Level
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.ui.Model
import java.util.*

private fun populateModel(objectMapper: ObjectMapper,
                          targetOrigin: String,
                          showStackTraceOnFrontEnd: Boolean,
                          model: Model,
                          message: String?,
                          stackTrace: String,
                          apiError: ApiError,
                          locale: Locale,
                          messagesLocalizationUtil: LocalizationUtil){
  val messageMapJson = objectMapper.writeValueAsString(messagesLocalizationUtil.getEscapedMessagesForLocale(locale))
  model.addAttribute("messagesJson", messageMapJson)
  model.addAttribute("targetOrigin", targetOrigin)
  model.addAttribute("errorCode", apiError.id)
  model.addAttribute("errorMessage", message)
  model.addAttribute("errorStackTrace", stackTrace)
  model.addAttribute("escapedErrorStackTrace", stackTrace.replace("\n", "\\n"))
  model.addAttribute("showStackTrace",showStackTraceOnFrontEnd)
}

fun handleException(log: Logger,
                    objectMapper: ObjectMapper,
                    targetOrigin: String,
                    showStackTraceOnFrontEnd: Boolean,
                    apiException: ApiException,
                    model: Model,
                    locale: Locale,
                    request: ServerHttpRequest,
                    messagesLocalizationUtil: LocalizationUtil) {
  val apiError = apiException.apiError
  val httpStatus = apiError.httpStatus
  val headersString = ContextLoggerHelper.extractHeadersAsString(request)
  val message = "httpStatus=${apiError.httpStatus.value()} apiError=$apiError ${apiException.message} $headersString"
  if (httpStatus.is4xxClientError) {
    ContextLoggerHelper.logError(apiException, message, AbstractApiController.LOG, Level.WARN)
  } else {
    ContextLoggerHelper.logError(apiException, message, AbstractApiController.LOG, Level.ERROR)

  }
  populateModel(objectMapper, targetOrigin, showStackTraceOnFrontEnd, model, apiException.message, apiException.stackTraceToString(), apiError, locale, messagesLocalizationUtil)
}

fun catchAllHandler(log: Logger,
                    objectMapper: ObjectMapper,
                    targetOrigin: String,
                    showStackTraceOnFrontEnd: Boolean,
                    exception: Exception,
                    model: Model,
                    locale: Locale,
                    request: ServerHttpRequest,
                    messagesLocalizationUtil: LocalizationUtil) {
  val headersString = ContextLoggerHelper.extractHeadersAsString(request)
  log.error("httpStatus=${ApiError.UNKNOWN_ERROR.httpStatus.value()} ${exception.message} $headersString", exception)
  populateModel(objectMapper, targetOrigin, showStackTraceOnFrontEnd, model, exception.message, exception.stackTraceToString(), ApiError.UNKNOWN_ERROR, locale, messagesLocalizationUtil)
}