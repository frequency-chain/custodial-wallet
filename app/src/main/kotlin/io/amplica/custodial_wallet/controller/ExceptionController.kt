package io.amplica.custodial_wallet.controller

import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.web.ContextLoggerHelper
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

@RestController
@Profile("test")
@RequestMapping("exception")
class ExceptionController @Autowired constructor(
  @Value("\${unfinished.enable.stack.trace}") private val enableStackTrace: Boolean
) : AbstractApiController(enableStackTrace) {
  @GetMapping("{apiErrorId}")
  fun throwApiException(@PathVariable("apiErrorId") apiErrorId: Int) {
    //Find the ApiError by id
    //throw an ApiException
    val apiError = ApiError.fromId(apiErrorId)
    throw ApiException(apiError, apiError.description)
  }

  @GetMapping("catchAll")
  suspend fun throwException(request: ServerHttpRequest) {
    return ContextLoggerHelper.logContext(request, null) {
      LOG.info("Outside the CompletableFuture context")
      CompletableFuture.supplyAsync(withMdc {
        LOG.info("Inside the CompletableFuture context")
      }).thenCompose { fakeClientCall() }.await()
    }
  }

  @GetMapping("catchAllReactor")
  suspend fun throwExceptionReactorChain(request: ServerHttpRequest) {
    return ContextLoggerHelper.logContext(request, null) {
      LOG.info("Outside the reactor context")
      fakeReactorClientCall().awaitSingleOrNull()
    }
  }

  private fun fakeReactorClientCall(): Mono<Void> {
    return Mono.defer<Void?> {
      LOG.info("Inside the reactor context")
      throw RuntimeException()
    }.subscribeOn(Schedulers.boundedElastic()) //Using boundedElastic to ensure it's on a Different scheduler than what may be fielding the web request
  }
}

fun <U> withMdc(supplier: Supplier<U>): Supplier<U> {
  val mdc = MDC.getCopyOfContextMap()
  return Supplier {
    MDC.setContextMap(mdc)
    supplier.get()
  }
}

fun fakeClientCall(): CompletableFuture<Void> {
  return CompletableFuture.runAsync {
    throw RuntimeException()
  }
}