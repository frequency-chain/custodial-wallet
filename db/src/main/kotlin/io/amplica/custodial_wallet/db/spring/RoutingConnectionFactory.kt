package io.amplica.custodial_wallet.db.spring

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.r2dbc.connection.lookup.AbstractRoutingConnectionFactory
import reactor.core.publisher.Mono

enum class ConnectionMode {
  READ_WRITE,
  READ_ONLY;

  companion object {
    const val CONTEXT_KEY_NAME: String = "CONNECTION_MODE"
  }
}

//https://stackoverflow.com/questions/69009294/how-can-i-configure-spring-r2dbc-to-use-separate-read-only-and-read-write-db-url
class RoutingConnectionFactory : AbstractRoutingConnectionFactory() {
  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(RoutingConnectionFactory::class.java)
  }
  override fun determineCurrentLookupKey(): Mono<Any> {
    return Mono.deferContextual {
      if(it.hasKey(ConnectionMode.CONTEXT_KEY_NAME)){
        val determinedValue = it.get(ConnectionMode.CONTEXT_KEY_NAME) as ConnectionMode
        LOG.info("contextKeyName determined to be: {}", determinedValue)
        return@deferContextual Mono.just(determinedValue)
      }

      Mono.empty()
    }
  }
}
