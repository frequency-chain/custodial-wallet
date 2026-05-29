package io.amplica.custodial_wallet.client.kms

import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import io.amplica.custodial_wallet.client.conf.AwsConfigurationProperties
import io.amplica.custodial_wallet.client.kms.conf.KmsClientConf
import io.amplica.custodial_wallet.client.kms.conf.KmsConfigurationProperties
import io.amplica.custodial_wallet.util.key_creation.Sr25519KeyPairCreator
import org.junit.jupiter.api.*
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kms.KmsAsyncClient
import software.amazon.awssdk.services.kms.model.CreateAliasRequest
import software.amazon.awssdk.services.kms.model.CreateKeyRequest
import software.amazon.awssdk.services.kms.model.KeySpec
import java.net.URI

@Testcontainers
class KmsClientTest {
  companion object {
    private var localstackImage: DockerImageName = DockerImageName.parse("localstack/localstack:2.3.2")
    lateinit var kmsClient: KmsClient
    lateinit var kmsAsyncClient: KmsClient
    private const val kmsKeyAlias = "alias/keypair"

    @Container
    @JvmStatic
    var localstack: LocalStackContainer = LocalStackContainer(localstackImage)
      .withServices(LocalStackContainer.Service.KMS)

    @BeforeAll
    @JvmStatic
    fun setUpClass() {
      runBlocking {
        val awsConfigurationProperties = AwsConfigurationProperties("x", "x", "x")
        val kmsConfigurationProperties = KmsConfigurationProperties(
          kmsKeyAlias,
          "us-east-1",
          localstack.getEndpointOverride(LocalStackContainer.Service.KMS).toString()
        )

        // Create a temporary client that makes a key and an alias to match
        val tempClientCredentials: AwsCredentialsProvider = StaticCredentialsProvider.create(
          AwsBasicCredentials.create(
            awsConfigurationProperties.access_key,
            awsConfigurationProperties.secret_key
          )
        )

        /*val tempKmsClient = software.amazon.awssdk.services.kms.KmsClient.builder()
          .credentialsProvider(tempClientCredentials)
          .region(Region.of(kmsConfigurationProperties.region))
          .endpointOverride(URI(kmsConfigurationProperties.service_endpoint!!))
          .build()*/

        val tempKmsAsyncClient = KmsAsyncClient.builder()
          .credentialsProvider(tempClientCredentials)
          .region(Region.of(kmsConfigurationProperties.region))
          .endpointOverride(URI(kmsConfigurationProperties.service_endpoint!!))
          .build()

        val clientConf = KmsClientConf()
        kmsClient = clientConf.v2KmsClient(tempClientCredentials, kmsConfigurationProperties)
        kmsAsyncClient = AwsSdkKmsAsyncClient(tempKmsAsyncClient, kmsKeyAlias)

        val createKeyRequest = CreateKeyRequest.builder()
          .description("Test Key for testing keypair encryption and decryption")
          .keySpec(KeySpec.SYMMETRIC_DEFAULT)
          .keyUsage("ENCRYPT_DECRYPT")
          .build()

        /*val createKeyResult = tempKmsClient.createKey(createKeyRequest)*/
        val reactiveCreateKeyResult = tempKmsAsyncClient.createKey(createKeyRequest).await()

        /*val createAliasRequest = CreateAliasRequest.builder()
          .aliasName(kmsKeyAlias)
          .targetKeyId(createKeyResult.keyMetadata().keyId())
          .build()*/
        val reactiveCreateAliasRequest = CreateAliasRequest.builder()
          .aliasName(kmsKeyAlias)
          .targetKeyId(reactiveCreateKeyResult.keyMetadata().keyId())
          .build()
        /*tempKmsClient.createAlias(createAliasRequest)*/
        tempKmsAsyncClient.createAlias(reactiveCreateAliasRequest).await()


      }

    }

    @AfterAll
    @JvmStatic
    fun afterClass() {
      localstack.close()
    }
  }

  @Nested
  inner class DefaultKmsClientTests{

    @Test
    fun healthcheck() {
      runBlocking {
        val isHealthy = kmsClient.healthcheck()
        Assertions.assertTrue(isHealthy)
      }
    }

    @Test
    fun encryptAndDecryptPrivateKey() {
      runBlocking {
        val keyPair = Sr25519KeyPairCreator.createKeyPair()
        val kmsEncryptionKey = KmsEncryptionKey(kmsKeyAlias, KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)
        val encryptedPrivateKey = kmsClient.encryptPrivateKey(keyPair.privateKeyBytes, kmsEncryptionKey)
        Assertions.assertNotEquals(keyPair.privateKeyBytes, encryptedPrivateKey.encryptedValue)

        val decryptedPrivateKey = kmsClient.decryptPrivateKey(encryptedPrivateKey)

        Assertions.assertArrayEquals(keyPair.privateKeyBytes, decryptedPrivateKey)
      }
    }
  }

  @Nested
  inner class ReactiveKmsClientTests{

    @Test
    fun healthcheck() {
      runBlocking {
        val isHealthy = kmsAsyncClient.healthcheck()
        Assertions.assertTrue(isHealthy)
      }
    }

    @Test
    fun encryptAndDecryptPrivateKey() {
      runBlocking {
        val keyPair = Sr25519KeyPairCreator.createKeyPair()
        val kmsEncryptionKey = KmsEncryptionKey(kmsKeyAlias, KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)
        val encryptedPrivateKey = kmsAsyncClient.encryptPrivateKey(keyPair.privateKeyBytes, kmsEncryptionKey)
        Assertions.assertNotEquals(keyPair.privateKeyBytes, encryptedPrivateKey.encryptedValue)

        val decryptedPrivateKey = kmsAsyncClient.decryptPrivateKey(encryptedPrivateKey)

        Assertions.assertArrayEquals(keyPair.privateKeyBytes, decryptedPrivateKey)
      }
    }
  }

}
