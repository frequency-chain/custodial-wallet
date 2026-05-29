package io.amplica.custodial_wallet.filter

import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.mono
import io.amplica.custodial_wallet.web.ContextLoggerHelper
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * We aren't currently using this because this dispatch seems to cause issues, e.g. the MDC doesn't propagate to
 * other co-routines and the failure mode if something goes wrong with this is to hang. Almost certainly there are
 * ways around both these issues but we will revisit them later, for now we will be using the ContextLoggingHelper in
 * the Controller methods. See this ticket for the gory details
 * https://www.pivotaltracker.com/n/projects/2496572/stories/184533794/comments/235693415
 */
class LoggingWebFilter  : WebFilter {
  override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
    return mono {
      ContextLoggerHelper.logContext(exchange.request, null) {
        chain.filter(exchange).awaitFirstOrNull()
      }
    }
  }
}
