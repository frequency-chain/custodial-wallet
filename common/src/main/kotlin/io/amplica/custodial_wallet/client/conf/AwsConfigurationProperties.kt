package io.amplica.custodial_wallet.client.conf

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties(prefix = "unfinished.custodial-wallet.aws")
data class AwsConfigurationProperties @ConstructorBinding constructor(
  val access_key: String?,
  val secret_key: String?,
  val session_token: String?
)
