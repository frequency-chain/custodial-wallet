package io.amplica.custodial_wallet.client.kms.conf

import io.amplica.custodial_wallet.client.kms.AwsSdkKmsAsyncClient
import io.amplica.custodial_wallet.client.kms.KmsClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import java.net.URI
import software.amazon.awssdk.services.kms.KmsAsyncClient as AwsKmsAsyncClient

@ConfigurationProperties(prefix = "unfinished.custodial-wallet.aws-kms")
data class KmsConfigurationProperties @ConstructorBinding constructor(
  val key_alias: String,
  val region: String,
  val service_endpoint: String?)

@Configuration
class KmsClientConf {
  @Bean
  fun v2KmsClient(
    @Qualifier("awsCredentialsProvider") awsCredentialsProvider: AwsCredentialsProvider,
    kmsConfigurationProperties: KmsConfigurationProperties
  ): KmsClient {
    var clientBuilder = AwsKmsAsyncClient.builder()
      .credentialsProvider(awsCredentialsProvider)
      .region(Region.of(kmsConfigurationProperties.region))

    if (kmsConfigurationProperties.service_endpoint != null) {
      val uri = URI(kmsConfigurationProperties.service_endpoint)
      clientBuilder = clientBuilder.endpointOverride(uri)
    }

    val awsKmsAsyncClient = clientBuilder.build()

    return AwsSdkKmsAsyncClient(awsKmsAsyncClient, kmsConfigurationProperties.key_alias)
  }
}