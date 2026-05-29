package io.amplica.custodial_wallet.conf

import io.amplica.frequency.util.GraphConfiguration
import io.amplica.frequency.util.FrequencyEnvironment
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*


@Configuration
class GraphConf {

  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(GraphConf::class.java)
  }

  @Bean
  fun graphParamProvider(
    @Value("\${sentry.environment}") sentryInput: String,
    @Value("#{\${unfinished.custodial-wallet.frequency.graph.schema.ids}}") graphSchemaIds: List<Int>,
  ): GraphParamProvider {
    val environment = when(sentryInput.lowercase(Locale.US)) {
      "dev" -> FrequencyEnvironment.DEV
      "paseo" -> FrequencyEnvironment.PASEO
      "rococo" -> FrequencyEnvironment.ROCOCO
      "mainnet" -> FrequencyEnvironment.MAINNET

      else -> throw IllegalArgumentException("Unknown sentryInput $sentryInput")
    }
    LOG.info("SENTRY INPUT: $sentryInput, DETECTED ENVIRONMENT: $environment, SCHEMAS: $graphSchemaIds")

    return DefaultGraphParamProvider(
      GraphConfiguration(environment, graphSchemaIds)
    )
  }
}


interface GraphParamProvider {
  fun getGraphConfiguration(): GraphConfiguration
}

class DefaultGraphParamProvider(
  private val graphConfiguration: GraphConfiguration,
) : GraphParamProvider {

  override fun getGraphConfiguration(): GraphConfiguration {
    return this.graphConfiguration
  }
}