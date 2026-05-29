package io.amplica.custodial_wallet.email.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.amplica.custodial_wallet.client.conf.AwsConfigurationProperties
import io.amplica.custodial_wallet.email.client.conf.SesConfigurationProperties
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.future.await
import org.junit.jupiter.api.*
import org.mockito.kotlin.*
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ses.model.CreateTemplateRequest
import software.amazon.awssdk.services.ses.model.Template
import software.amazon.awssdk.services.ses.model.VerifyEmailIdentityRequest
import software.amazon.awssdk.services.ses.SesAsyncClient as AwsSesAsyncClient
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.time.Duration

@Testcontainers
class SesClientTest {
  companion object {
    const val TEST_SOURCE_NAME = "Teddy Willard"
    const val TEST_SOURCE_EMAIL_ADDRESS = "teddywillard@gmail.com"
    const val TEST_EMAIL_ADDRESS = "teddy.willard@unfinished.com"
    const val TEMPLATE_NAME = "samplev3"
    private var LOCALSTACK_IMAGE: DockerImageName = DockerImageName.parse("localstack/localstack:2.3.2")
    lateinit var sesUrl: String
    lateinit var mapper: ObjectMapper
    lateinit var cachingSesAsyncClient: CachingSesClient
    lateinit var sesAsyncClient: AwsSdkSesAsyncClient
    lateinit var internalAwsSesAsyncClient: AwsSesAsyncClient

    @Container
    @JvmStatic
    var localstack: LocalStackContainer = LocalStackContainer(LOCALSTACK_IMAGE)
      .withServices(LocalStackContainer.Service.SES)

    @BeforeAll
    @JvmStatic
    fun setUpClass() {
      runBlocking {
        val awsConfigurationProperties = AwsConfigurationProperties("x", "x", "x")

        val sesConfigurationProperties = SesConfigurationProperties(
          "us-east-1",
          localstack.getEndpointOverride(LocalStackContainer.Service.SES).toString()
        )

        sesUrl = sesConfigurationProperties.service_endpoint + "/_aws/ses"

        val v2CredentialsProvider: AwsCredentialsProvider = StaticCredentialsProvider.create(AwsBasicCredentials.create(awsConfigurationProperties.access_key, awsConfigurationProperties.secret_key))

        internalAwsSesAsyncClient = AwsSesAsyncClient.builder()
          .credentialsProvider(v2CredentialsProvider)
          .region(Region.of(sesConfigurationProperties.region))
          .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SES))
          .build()

        sesAsyncClient = spy(AwsSdkSesAsyncClient(internalAwsSesAsyncClient))

        cachingSesAsyncClient = CachingSesClient(sesAsyncClient, Duration.ofMinutes(5), setOf(TEMPLATE_NAME))

        mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val verifyEmailIdentityRequest = VerifyEmailIdentityRequest.builder().emailAddress(TEST_SOURCE_EMAIL_ADDRESS).build()
        internalAwsSesAsyncClient.verifyEmailIdentity(verifyEmailIdentityRequest).await()
      }
    }

    @AfterAll
    @JvmStatic
    fun afterClass() {
      println("Just Break Here")
    }
  }

  @Nested
  inner class AwsSdkSesAsyncClientTests {
    @Test
    fun templateExists() {
      runBlocking {
        val templateExistsRequest = TemplateExistsRequest(TEMPLATE_NAME)
        cachingSesAsyncClient.templateExists(templateExistsRequest)
        reset(sesAsyncClient)
        cachingSesAsyncClient.templateExists(templateExistsRequest)
        verifyNoInteractions(sesAsyncClient)
      }
    }

    @Test
    fun sendEmail() {
      runBlocking {
        val template: Template = Template.builder()
          .templateName(TEMPLATE_NAME)
          .subjectPart("welcome {{name}}")
          .textPart("yada yada {{name}}")
          .build()
        val request = CreateTemplateRequest.builder()
          .template(template)
          .build()
        internalAwsSesAsyncClient.createTemplate(request)
        val sendEmailRequest = SendEmailRequest(
          TEST_EMAIL_ADDRESS,
          TEST_SOURCE_NAME,
          TEST_SOURCE_EMAIL_ADDRESS,
          TEMPLATE_NAME,
          mutableMapOf("url" to "testurl", "name" to "Teddy")
        )
        val sendTemplatedEmailResponse = sesAsyncClient.sendEmail(sendEmailRequest)
        val connection: HttpURLConnection = URI(sesUrl).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        val input = BufferedReader(
          InputStreamReader(connection.inputStream)
        )
        val content = StringBuffer()
        for (inputLine in input.readLines()) {
          content.append(inputLine)
        }
        input.close()
        val result = mapper.readValue(
          content.toString(),
          SesResponse::class.java
        )
        Assertions.assertNotNull(sendTemplatedEmailResponse.messageId)
        Assertions.assertEquals("$TEST_SOURCE_NAME <$TEST_SOURCE_EMAIL_ADDRESS>", result.messages[0].Source)
        Assertions.assertEquals(TEST_EMAIL_ADDRESS, result.messages[0].Destination.ToAddresses[0])
      }
    }
  }
}

data class SesResponse(val messages: List<SesMessage>)

data class SesMessage(
  val Id: String,
  val Region: String,
  val Source: String,
  val Template: String?,
  val TemplateData: String?,
  val Destination: DestinationObject,
  val Subject: String?,
  val Body: EmailBody?
)

data class DestinationObject(val ToAddresses: List<String>)

data class EmailBody(val text_part: String?, val html_part: String?)
