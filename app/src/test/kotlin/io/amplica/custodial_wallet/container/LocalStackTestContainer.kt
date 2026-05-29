package io.amplica.custodial_wallet.container

import io.amplica.custodial_wallet.client.conf.AwsConfigurationProperties
import io.amplica.custodial_wallet.client.kms.conf.KmsConfigurationProperties
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kms.model.CreateAliasRequest
import software.amazon.awssdk.services.kms.model.CreateKeyRequest
import software.amazon.awssdk.services.kms.model.KeySpec
import software.amazon.awssdk.services.kms.model.ListAliasesRequest
import software.amazon.awssdk.services.ses.SesAsyncClient
import software.amazon.awssdk.services.ses.model.CreateTemplateRequest
import software.amazon.awssdk.services.ses.model.Template
import software.amazon.awssdk.services.ses.model.VerifyEmailIdentityRequest
import java.net.URI


class LocalStackTestContainer : LocalStackContainer(IMAGE), CustodialWalletTestContainer {
  companion object {
    private val IMAGE = DockerImageName.parse("localstack/localstack:3.0.2")
    const val SOURCE_EMAIL_ADDRESS = "saas-devs@unfinished.com"

    // KMS config
    const val kmsKeyAlias = "alias/custodial_wallet_keypair"

    // Email templates
    const val signupEmailTemplateName = "FrequencyAccessSignupTemplate"
    const val loginEmailTemplateName = "FrequencyAccessLoginTemplate"
    const val directLoginEmailTemplateName = "FrequencyAccessDirectLoginTemplate"
    const val signupEmailTemplateOTPName = "FrequencyAccessSignupTemplateOTP"
    const val loginEmailTemplateOTPName = "FrequencyAccessLoginTemplateOTP"
    const val directLoginEmailTemplateOTPName = "FrequencyAccessDirectLoginTemplateOTP"
  }

  init {
    withServices(Service.SES)
    withServices(Service.KMS)
  }

  override fun start() {
    super.start()
    setUpKms()
    setUpSes()
  }

  override fun getPropertyValues(): Map<String, String> {
    return mapOf(
      Pair("unfinished.custodial-wallet.aws-ses.service_endpoint", getEndpointOverride(Service.SES).toString()),
      Pair("unfinished.custodial-wallet.aws-ses.source_email", SOURCE_EMAIL_ADDRESS),
      Pair("unfinished.custodial-wallet.aws-kms.service_endpoint", getEndpointOverride(Service.KMS).toString()),
      Pair("unfinished.custodial-wallet.aws-kms.key_alias", kmsKeyAlias),
    )
  }

  private fun setUpKms() {
    // Create kms encryption key in localstack
    val awsConfigurationProperties = AwsConfigurationProperties("x", "x", "x")
    val kmsConfigurationProperties =
      KmsConfigurationProperties(
        kmsKeyAlias,
        "us-east-2",
        getEndpointOverride(Service.KMS).toString()
      )

    // Create a temporary client that makes a key and an alias to match
    val tempClientCredentials: AwsCredentialsProvider =
      StaticCredentialsProvider.create(
        AwsBasicCredentials.create(
          awsConfigurationProperties.access_key,
          awsConfigurationProperties.secret_key
        )
      )

    val tempKmsClient =
      software.amazon.awssdk.services.kms.KmsAsyncClient.builder()
        .credentialsProvider(tempClientCredentials)
        .region(Region.of(kmsConfigurationProperties.region))
        .endpointOverride(URI(kmsConfigurationProperties.service_endpoint!!))
        .build()

    runBlocking {
      val createKeyRequest =
        CreateKeyRequest.builder()
          .description("Test Key for testing keypair encryption and decryption")
          .keySpec(KeySpec.SYMMETRIC_DEFAULT)
          .keyUsage("ENCRYPT_DECRYPT")
          .build()
      val createKeyResult = tempKmsClient.createKey(createKeyRequest)
      val kmsKeyId = createKeyResult.await().keyMetadata().keyId()

      val listAliasesRequest = ListAliasesRequest.builder().keyId(kmsKeyId).build()
      val listAliasesResponse = tempKmsClient.listAliases(listAliasesRequest)

      if (listAliasesResponse.await().aliases().size == 0) {
        val createAliasRequest =
          CreateAliasRequest.builder()
            .aliasName(LocalStackTestContainer.kmsKeyAlias)
            .targetKeyId(kmsKeyId)
            .build()
        tempKmsClient.createAlias(createAliasRequest)
      }
    }
  }

  fun setUpSes() {
    runBlocking {
      val awsConfigurationProperties = AwsConfigurationProperties("x", "x", "x")
      val awsCredentialsProvider: AwsCredentialsProvider =
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create(
            awsConfigurationProperties.access_key,
            awsConfigurationProperties.secret_key
          )
        )

      val internalAwsSesAsyncClientBuilder =
        SesAsyncClient.builder()
          .credentialsProvider(awsCredentialsProvider)
          .region(Region.US_EAST_2)

      internalAwsSesAsyncClientBuilder.endpointOverride(
        getEndpointOverride(LocalStackContainer.Service.SES)
      )

      val internalAwsSesClient = internalAwsSesAsyncClientBuilder.build()

      val verifyEmailIdentityRequest =
        VerifyEmailIdentityRequest.builder()
          .emailAddress(SOURCE_EMAIL_ADDRESS)
          .build()
      internalAwsSesClient.verifyEmailIdentity(verifyEmailIdentityRequest)

      // Create Email template for signup and login
      // (this would already exist in AWS, we have to create it for localstack)
      val signupEmailOTPTemplate: Template =
        Template.builder()
          .templateName(signupEmailTemplateOTPName)
          .subjectPart("signup for wallet")
          .textPart("click here: {{url}}")
          .build()
      val signupTemplateOTPRequest =
        CreateTemplateRequest.builder().template(signupEmailOTPTemplate).build()
      internalAwsSesClient.createTemplate(signupTemplateOTPRequest).await()

      val loginEmailOTPTemplate: Template =
        Template.builder()
          .templateName(loginEmailTemplateOTPName)
          .subjectPart("login to wallet")
          .textPart("click here: {{url}}")
          .build()
      val loginTemplateOTPRequest =
        CreateTemplateRequest.builder().template(loginEmailOTPTemplate).build()
      internalAwsSesClient.createTemplate(loginTemplateOTPRequest).await()

      val directLoginEmailOTPTemplate: Template =
        Template.builder()
          .templateName(directLoginEmailTemplateOTPName)
          .subjectPart("login for wallet")
          .textPart("click here: {{url}}")
          .build()
      val directLoginTemplateOTPRequest =
        CreateTemplateRequest.builder().template(directLoginEmailOTPTemplate).build()
      internalAwsSesClient.createTemplate(directLoginTemplateOTPRequest).await()

      val signupEmailTemplate: Template =
        Template.builder()
          .templateName(signupEmailTemplateName)
          .subjectPart("signup for wallet")
          .textPart("click here: {{url}}")
          .build()
      val signupTemplateRequest =
        CreateTemplateRequest.builder().template(signupEmailTemplate).build()
      internalAwsSesClient.createTemplate(signupTemplateRequest).await()

      val loginEmailTemplate: Template =
        Template.builder()
          .templateName(loginEmailTemplateName)
          .subjectPart("login to wallet")
          .textPart("click here: {{url}}")
          .build()
      val loginTemplateRequest =
        CreateTemplateRequest.builder().template(loginEmailTemplate).build()
      internalAwsSesClient.createTemplate(loginTemplateRequest).await()

      val directLoginEmailTemplate: Template =
        Template.builder()
          .templateName(directLoginEmailTemplateName)
          .subjectPart("login for wallet")
          .textPart("click here: {{url}}")
          .build()
      val directLoginTemplateRequest =
        CreateTemplateRequest.builder().template(directLoginEmailTemplate).build()
      internalAwsSesClient.createTemplate(directLoginTemplateRequest).await()
    }
  }
}
