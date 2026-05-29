package io.amplica.custodial_wallet.client.conf

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.get
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.core.SdkSystemSetting

/**
 * This is config that applies to one or more clients, e.g AWS Configuration for multiple clients
 *
 * @constructor Create empty Clients config
 */
@Configuration
class ClientsConfig {
  @Bean
  fun awsCredentialsProvider(): AwsCredentialsProvider {
    return DefaultCredentialsProvider.builder().build()
  }
}

class SetSystemPropertiesApplicationEventListener : ApplicationListener<ApplicationEnvironmentPreparedEvent> {

  override fun onApplicationEvent(event: ApplicationEnvironmentPreparedEvent) {
    val environment: ConfigurableEnvironment = event.environment
    val systemProperties = environment.systemProperties
    val awsConfigurationProperties = AwsConfigurationProperties(
      environment["unfinished.custodial-wallet.aws.access_key"],
      environment["unfinished.custodial-wallet.aws.secret_key"],
      environment["unfinished.custodial-wallet.aws.session_token"]
    ) //because we are plugging into the ApplicationContext right after the properties are set this class therefore cannot
    // use the AwsConfiguraionProperties injected as there would be nothing to inject at this time when the application is firing up
    if (awsConfigurationProperties.access_key != null && awsConfigurationProperties.secret_key != null) {
      systemProperties[SdkSystemSetting.AWS_ACCESS_KEY_ID.property()] = awsConfigurationProperties.access_key
      systemProperties[SdkSystemSetting.AWS_SECRET_ACCESS_KEY.property()] = awsConfigurationProperties.secret_key
      if(awsConfigurationProperties.session_token != null) {
        systemProperties[SdkSystemSetting.AWS_SESSION_TOKEN.property()] = awsConfigurationProperties.session_token
      }
    }
  }
}