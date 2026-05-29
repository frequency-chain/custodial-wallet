package io.amplica.custodial_wallet.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.amplica.custodial_wallet.controller.util.LocalizationUtil
import io.amplica.custodial_wallet.controller.util.ThymeleafHelper
import io.amplica.custodial_wallet.controller.util.catchAllHandler
import io.amplica.custodial_wallet.controller.util.handleException
import io.amplica.custodial_wallet.dto.*
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.orchestration.LookupOrchestrationService
import io.amplica.custodial_wallet.orchestration.siwa.SiwaOrchestrationService
import io.amplica.custodial_wallet.web.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.server.MissingRequestValueException
import java.util.*


abstract class AbstractSiwaController(
  private val showStackTraceOnFrontEnd: Boolean,
  private val targetOrigin: String,
  private val objectMapper: ObjectMapper,
  private val environment: Environment,
  private val orchestrationService: SiwaOrchestrationService,
  private val lookupOrchestrationService: LookupOrchestrationService,
  private val matomoProps: MatomoProps,
  private val messagesLocalizationUtil: LocalizationUtil,
  private val sentryEnv: String,
  private val sentryRelease: String,
) {

  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(AbstractSiwaController::class.java)
    val apiErrorIdToSiwaErrorTitleAndDescription = mapOf(
      65 to ("siwa.error.modal.title.expired-session" to "siwa.error.modal.desc.expired-session")
    )
    private val DEFAULT_PAIR = null to null
  }

  @ExceptionHandler
  suspend fun handleException(
    @CookieValue(SESSION_ID_COOKIE_NAME, required = false) sessionId: String?,
    apiException: ApiException,
    model: Model,
    locale: Locale,
    request: ServerHttpRequest,
    response: ServerHttpResponse
  ): String {
    handleException(LOG, objectMapper, targetOrigin, showStackTraceOnFrontEnd, apiException, model, locale, request, messagesLocalizationUtil)
    val apiError = apiException.apiError
    val httpStatus = apiError.httpStatus
    val headersString = ContextLoggerHelper.extractHeadersAsString(request)
    val headersMap = ContextLoggerHelper.extractHeaders(request)
    val message = "apiError=$apiError ${apiException.message} $headersString"
    if (httpStatus.is4xxClientError) {
      ContextLoggerHelper.logError(apiException, message, AbstractApiController.LOG, Level.WARN)
    } else {
      ContextLoggerHelper.logError(apiException, message, AbstractApiController.LOG, Level.ERROR)
    }
    model.addAttribute("helper", ThymeleafHelper)
    model.addAttribute("env", environment)
    model.addAttribute("matomo", addMatomoPropsErrorData(matomoProps))
    model.addAttribute("sentryEnv", sentryEnv)
    model.addAttribute("sentryRelease", sentryRelease)

    val siwaSession = sessionId?.let { lookupOrchestrationService.findSiwaSession(it) }


    response.statusCode = httpStatus
    return if (siwaSession == null) {
      model.addAttribute("props", createErrorProps(apiException, headersMap))
      "siwa/error"
    } else {
      val startErrorPage = orchestrationService.getSiwaErrorStartPage(siwaSession, apiException.apiError)
      model.addAttribute("props", startErrorPage.model)
      startErrorPage.template
    }
  }

  @ExceptionHandler
  fun catchAllHandler(
    exception: Exception,
    model: Model,
    locale: Locale,
    request: ServerHttpRequest
  ): String {
    catchAllHandler(LOG, objectMapper, targetOrigin, showStackTraceOnFrontEnd, exception, model, locale, request, messagesLocalizationUtil)
    val headersString = ContextLoggerHelper.extractHeadersAsString(request)
    val headersMap = ContextLoggerHelper.extractHeaders(request)
    var apiError = ApiError.UNKNOWN_ERROR
    var level = Level.ERROR
    if(exception is MissingRequestValueException){
      apiError = ApiError.NULL_REQUIRED_FIELD_ERROR
      level = Level.WARN
    }
    LOG.atLevel(level).log("apiError=${apiError} ${exception.message} $headersString", exception)
    model.addAttribute("helper", ThymeleafHelper)
    model.addAttribute("env", environment)
    model.addAttribute("props", createErrorProps(exception, headersMap))
    model.addAttribute("matomo", addMatomoPropsErrorData(matomoProps))
    model.addAttribute("sentryEnv", sentryEnv)
    model.addAttribute("sentryRelease", sentryRelease)
    return "siwa/error"
  }

  private fun createErrorProps(exception: Exception, headersMap: Map<String, String?>): ErrorProps {
    val context = ContextLoggerHelper.getMdcContext()
    val sessionId = headersMap[X_UNFINISHED_SESSION_ID_NAME]
    val xSessionId = headersMap[X_SESSION_ID_NAME]
    val xTraceId = headersMap[X_TRACE_ID_NAME]
    //Provider name is not a header, so check to see if it's in the context
    val providerName = context[LoggingAttributes.PROVIDER_NAME]
    var error = when {
      exception is ApiException -> exception.apiError
      else -> ApiError.UNKNOWN_ERROR
    }
    when(exception) {
      is MissingRequestValueException -> {
        val message = exception.message
        if (message.contains(SESSION_ID_COOKIE_NAME)) {
          error = ApiError.SIWA_SESSION_NOT_FOUND
        }
      }
    }
    val siwaErrorTitleAndDescription = apiErrorIdToSiwaErrorTitleAndDescription.getOrDefault(error.id, DEFAULT_PAIR)
    return ErrorProps(error, exception.message, exception.stackTraceToString(), sessionId, xSessionId, xTraceId, providerName, siwaErrorTitleAndDescription.first, siwaErrorTitleAndDescription.second)
  }

  private fun addMatomoPropsErrorData(matomoProps: MatomoProps): MatomoProps {
    val matomoErrorPage = MatomoPageName.SIWA_ERROR
    val matomoEvent = MatomoEvent(MatomoEvent.Category.SIWA, matomoErrorPage.noPrefix())
    val matomoData = MatomoData(matomoErrorPage.pageName, null, matomoEvent)
    return matomoProps.withData(matomoData)
  }
}