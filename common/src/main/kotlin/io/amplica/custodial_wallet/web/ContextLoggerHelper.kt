package io.amplica.custodial_wallet.web

import io.amplica.custodial_wallet.exception.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import net.logstash.logback.argument.StructuredArguments.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.slf4j.event.Level
import org.springframework.http.server.reactive.ServerHttpRequest
import java.util.*
import kotlin.collections.LinkedHashMap

object LoggingAttributes {
  const val SESSION_ID = "SessionId"
  const val X_SESSION_ID = "XSessionId"
  const val X_TRACE_ID = "XTraceId"
  const val PROVIDER_NAME = "ProviderName"
}

fun interface LoggingAttributesCallback<T> {
  suspend fun withAttributes(sessionId: String?, xSessionId: String?, xTraceId: String?): T
}

class ContextLoggerHelper {
  companion object{
    private val LOG: Logger = LoggerFactory.getLogger(ContextLoggerHelper::class.java)

    suspend fun<T> putContext(keyValueMap: Map<String, String>, block: suspend CoroutineScope.() -> T): T {
      keyValueMap.forEach {
        MDC.put(it.key, it.value)
      }
      return withContext(MDCContext()) {
        block()
      }
    }

    suspend fun<T> logContext(request: ServerHttpRequest, sessionId: String?, block: suspend CoroutineScope.() -> T): T {
      val headers = extractHeaders(request, sessionId)
      MDC.setContextMap(headers)
      return withContext(MDCContext()) {
        block()
      }
    }

    fun extractHeaders(request: ServerHttpRequest, sessionId: String? = null): Map<String, String?> {
      val headers = LinkedHashMap<String, String?>()
      headers.put(LoggingAttributes.SESSION_ID, sessionId ?: request.headers[X_UNFINISHED_SESSION_ID_NAME]?.firstOrNull()) //TODO is there another way to get this information as it will be null in the ExceptionHandler context
      headers.put(LoggingAttributes.X_TRACE_ID, request.headers[X_TRACE_ID_NAME]?.firstOrNull() ?: UUID.randomUUID().toString())
      headers.put(LoggingAttributes.X_SESSION_ID, request.headers[X_SESSION_ID_NAME]?.firstOrNull() ?: UUID.randomUUID().toString())

      return headers
    }

    fun extractHeadersAsString(request: ServerHttpRequest, sessionId: String? = null): String {
      val headers = extractHeaders(request, sessionId)

      return headers.asSequence().joinToString(" ")
    }

    fun logError(apiException: ApiException, errorMessage: String, logger: Logger, logLevel: Level){
      logger.atLevel(logLevel).log("ApiException {} {} {}", fields(apiException.apiError), apiException.structuredArguments, errorMessage, apiException)
    }

    fun getMdcContext(): Map<String, String> {
      return MDC.getCopyOfContextMap() ?: emptyMap()
    }

    suspend fun<T> withMdcContext(loggingAttributesCallback: LoggingAttributesCallback<T>): T {
      val map = getMdcContext()
      return loggingAttributesCallback.withAttributes(map[LoggingAttributes.SESSION_ID], map[LoggingAttributes.X_SESSION_ID], map[LoggingAttributes.X_TRACE_ID])
    }
  }
}