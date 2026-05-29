package io.amplica.custodial_wallet.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.ExceptionHandler
import java.util.*
import io.amplica.custodial_wallet.controller.util.*


abstract class AbstractWebController(
  private val showStackTraceOnFrontEnd: Boolean,
  private val targetOrigin: String,
  private val objectMapper: ObjectMapper,
  private val messagesLocalizationUtil: LocalizationUtil)
{

  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(AbstractWebController::class.java)
  }

  @ExceptionHandler
  fun handleException(apiException: ApiException, model: Model, locale: Locale, request: ServerHttpRequest): String {
    handleException(LOG, objectMapper, targetOrigin, showStackTraceOnFrontEnd, apiException, model, locale, request, messagesLocalizationUtil)
    return "redirect:/?apiError=${apiException.apiError.id}"
  }

  @ExceptionHandler
  fun catchAllHandler(exception: Exception, model: Model, locale: Locale, request: ServerHttpRequest): String {
    catchAllHandler(LOG, objectMapper, targetOrigin, showStackTraceOnFrontEnd, exception, model, locale, request, messagesLocalizationUtil)
    return "redirect:/?apiError=${ApiError.UNKNOWN_ERROR.id}"
  }
}