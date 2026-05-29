package io.amplica.custodial_wallet.email.client.conf

import io.amplica.custodial_wallet.email.client.AwsSdkSesAsyncClient
import io.amplica.custodial_wallet.email.client.SesClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import java.net.URI
import software.amazon.awssdk.services.ses.SesAsyncClient as AwsSesAsyncClient

data class AwsSesProperties(
  val sourceName: String,
  val sourceEmail: String,
  val loginTemplateName: String,
  val signupTemplateName: String,
  val directLoginTemplateName: String,
  val addIdentifierTemplateName: String,
  val tokensReceivedTemplateName: String,
)

@ConfigurationProperties(prefix = "unfinished.custodial-wallet.aws-ses")
data class SesConfigurationProperties @ConstructorBinding constructor(
  val region: String,
  val service_endpoint: String?
)

@Configuration
class SesClientConf {
  @Bean
  fun awsSesProperties(
    @Value("\${unfinished.custodial-wallet.aws-ses.source_name}") sourceName: String,
    @Value("\${unfinished.custodial-wallet.aws-ses.source_email}") sourceEmail: String,
    @Value("\${unfinished.custodial-wallet.aws-ses.login_template_name}") loginTemplateName: String,
    @Value("\${unfinished.custodial-wallet.aws-ses.signup_template_name}") signupTemplateName: String,
    @Value("\${unfinished.custodial-wallet.aws-ses.direct_login_template_name}") directLoginTemplateName: String,
    @Value("\${unfinished.custodial-wallet.aws-ses.add_identifier_template_name}") addIdentifierTemplateName: String,
    @Value("\${unfinished.custodial-wallet.aws-ses.community-rewards.tokens_received}") tokensReceivedTemplateName: String,
  ) = AwsSesProperties(
    sourceName,
    sourceEmail,
    loginTemplateName,
    signupTemplateName,
    directLoginTemplateName,
    addIdentifierTemplateName,
    tokensReceivedTemplateName
  )

  @Bean
  fun secondaryAwsSesProperties(
    @Value("\${unfinished.custodial-wallet.aws-ses.source_name}") sourceName: String,
    @Value("\${unfinished.custodial-wallet.aws-ses.secondary_source_email}") sourceEmail: String,
    @Value("\${unfinished.custodial-wallet.aws-ses.login_template_name}") loginTemplateName: String,
    @Value("\${unfinished.custodial-wallet.aws-ses.signup_template_name}") signupTemplateName: String,
    @Value("\${unfinished.custodial-wallet.aws-ses.direct_login_template_name}") directLoginTemplateName: String,
    @Value("\${unfinished.custodial-wallet.aws-ses.add_identifier_template_name}") addIdentifierTemplateName: String,
    @Value("\${unfinished.custodial-wallet.aws-ses.community-rewards.tokens_received}") tokensReceivedTemplateName: String,
  ) = AwsSesProperties(
    sourceName,
    sourceEmail,
    loginTemplateName,
    signupTemplateName,
    directLoginTemplateName,
    addIdentifierTemplateName,
    tokensReceivedTemplateName
  )

  @Bean
  fun awsSesPropertiesSIWA(
    @Value("\${unfinished.custodial-wallet.aws-ses.source_name}") sourceName: String,
    @Value("\${unfinished.custodial-wallet.aws-ses.source_email}") sourceEmail: String,
    @Value("\${unfinished.custodial-wallet.aws-ses.login_template_otp_name}") loginTemplateName: String,
    @Value("\${unfinished.custodial-wallet.aws-ses.signup_template_otp_name}") signupTemplateName: String,
    @Value("\${unfinished.custodial-wallet.aws-ses.direct_login_otp_template_name}") directLoginTemplateName: String,
    @Value("\${unfinished.custodial-wallet.aws-ses.add_identifier_otp_template_name}") addIdentifierTemplateName: String,
    @Value("\${unfinished.custodial-wallet.aws-ses.community-rewards.tokens_received}") tokensReceivedTemplateName: String,
  ) = AwsSesProperties(
    sourceName,
    sourceEmail,
    loginTemplateName,
    signupTemplateName,
    directLoginTemplateName,
    addIdentifierTemplateName,
    tokensReceivedTemplateName
  )

  @Bean
  fun secondaryAwsSesPropertiesSIWA(
    @Value("\${unfinished.custodial-wallet.aws-ses.source_name}") sourceName: String,
    @Value("\${unfinished.custodial-wallet.aws-ses.secondary_source_email}") sourceEmail: String,
    @Value("\${unfinished.custodial-wallet.aws-ses.login_template_otp_name}") loginTemplateName: String,
    @Value("\${unfinished.custodial-wallet.aws-ses.signup_template_otp_name}") signupTemplateName: String,
    @Value("\${unfinished.custodial-wallet.aws-ses.direct_login_otp_template_name}") directLoginTemplateName: String,
    @Value("\${unfinished.custodial-wallet.aws-ses.add_identifier_otp_template_name}") addIdentifierTemplateName: String,
    @Value("\${unfinished.custodial-wallet.aws-ses.community-rewards.tokens_received}") tokensReceivedTemplateName: String,
  ) = AwsSesProperties(
    sourceName,
    sourceEmail,
    loginTemplateName,
    signupTemplateName,
    directLoginTemplateName,
    addIdentifierTemplateName,
    tokensReceivedTemplateName
  )

  @Bean
  fun internalAwsSesClient(
    @Qualifier("awsCredentialsProvider") awsCredentialsProvider: AwsCredentialsProvider,
    sesConfigurationProperties: SesConfigurationProperties
  ): AwsSesAsyncClient {
    val internalAwsSesAsyncClientBuilder = AwsSesAsyncClient.builder()
      .credentialsProvider(awsCredentialsProvider)
      .region(Region.of(sesConfigurationProperties.region))

    if(sesConfigurationProperties.service_endpoint != null) {
      internalAwsSesAsyncClientBuilder.endpointOverride(URI(sesConfigurationProperties.service_endpoint))
    }

    return internalAwsSesAsyncClientBuilder.build()
  }

  @Bean
  fun v2SesClient(@Qualifier("internalAwsSesClient") internalAwsSesClient: software.amazon.awssdk.services.ses.SesAsyncClient): SesClient {
    return AwsSdkSesAsyncClient(internalAwsSesClient)
  }

}
