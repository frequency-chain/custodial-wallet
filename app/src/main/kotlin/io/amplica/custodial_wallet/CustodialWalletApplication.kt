package io.amplica.custodial_wallet

import io.amplica.custodial_wallet.client.conf.AwsConfigurationProperties
import io.amplica.custodial_wallet.client.conf.SetSystemPropertiesApplicationEventListener
import io.amplica.custodial_wallet.client.kms.conf.KmsConfigurationProperties
import io.amplica.custodial_wallet.client.redis.conf.RedisConfigurationProperties
import io.amplica.custodial_wallet.conf.BlockingStrategyConfigurationProperties
import io.amplica.custodial_wallet.email.client.conf.SesConfigurationProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springdoc.core.configuration.SpringDocKotlinConfiguration
import org.springdoc.core.configuration.SpringDocKotlinxConfiguration
import org.springframework.beans.factory.BeanCreationException
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.EnableConfigurationProperties

/**
 * The intent of this file is to just bootstrap the Spring Boot Environment, please don't put business objects in here that
 * aren't purely of a Spring Boot concern, e.g. Controllers, Services, Components, Repositories, Entities, DTOs etc etc
 */
@SpringBootApplication
@EnableConfigurationProperties(
  SesConfigurationProperties::class,
  KmsConfigurationProperties::class,
  AwsConfigurationProperties::class,
  RedisConfigurationProperties::class,
  BlockingStrategyConfigurationProperties::class,
)
@ImportAutoConfiguration(
  SpringDocKotlinxConfiguration::class,
  SpringDocKotlinConfiguration::class
)
class CustodialWalletApplication

private val LOG: Logger = LoggerFactory.getLogger(CustodialWalletApplication::class.java)

fun addListeners(springApplication: SpringApplication) {
  springApplication.addListeners(SetSystemPropertiesApplicationEventListener())
}

fun main(args: Array<String>) {
  val app = SpringApplicationBuilder(CustodialWalletApplication::class.java).build()
  addListeners(app)
  try{
    app.run(*args)
  }
  catch (e: BeanCreationException){
    LOG.error(e.message, e)
    System.exit(1)
  }
}
