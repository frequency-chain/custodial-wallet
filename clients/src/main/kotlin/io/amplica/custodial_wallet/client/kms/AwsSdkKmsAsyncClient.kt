package io.amplica.custodial_wallet.client.kms

import kotlinx.coroutines.future.await
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest
import software.amazon.awssdk.services.kms.model.EncryptionAlgorithmSpec
import software.amazon.awssdk.services.kms.model.KeyState
import java.util.concurrent.CompletableFuture
import software.amazon.awssdk.services.kms.KmsAsyncClient as AwsKmsAsyncClient
import software.amazon.awssdk.services.kms.model.DecryptRequest as AwsDecryptRequest
import software.amazon.awssdk.services.kms.model.EncryptRequest as AwsEncryptRequest


class AwsSdkKmsAsyncClient(private val awsKmsService: AwsKmsAsyncClient, private val alias: String) : KmsClient {

  override suspend fun encryptPrivateKey(privateKey: ByteArray, kmsEncryptionKey: KmsEncryptionKey): EncryptedKey {
    val privateKeyResponse = encrypt(EncryptRequest(alias, privateKey, EncryptionAlgorithmSpec.SYMMETRIC_DEFAULT)).await()
    val kmsPrivateEncryptionAlgorithm = KmsEncryptionAlgorithm.fromAlgorithm(privateKeyResponse.algorithm.name)
    val kmsPrivateDecryptionKey = KmsDecryptionKey(privateKeyResponse.keyId, kmsPrivateEncryptionAlgorithm)

    return EncryptedKey(privateKeyResponse.cipherText,  kmsPrivateDecryptionKey)
  }

  override suspend fun decryptPrivateKey(encryptedKey: EncryptedKey): ByteArray {
    val privateKeyResponse = decrypt(DecryptRequest(alias, encryptedKey.encryptedValue, EncryptionAlgorithmSpec.SYMMETRIC_DEFAULT)).await()
    return privateKeyResponse.plainText
  }

  override suspend fun encryptData(data: ByteArray, kmsEncryptionKey: KmsEncryptionKey): EncryptedData {
    val algorithm = when (kmsEncryptionKey.kmsEncryptionAlgorithm) {
      KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT -> EncryptionAlgorithmSpec.SYMMETRIC_DEFAULT
    }

    val privateKeyResponse = encrypt(EncryptRequest(kmsEncryptionKey.keyAlias, data, algorithm)).await()
    val kmsPrivateEncryptionAlgorithm = KmsEncryptionAlgorithm.fromAlgorithm(privateKeyResponse.algorithm.name)
    val kmsPrivateDecryptionKey = KmsDecryptionKey(privateKeyResponse.keyId, kmsPrivateEncryptionAlgorithm)

    return EncryptedData(privateKeyResponse.cipherText,  kmsPrivateDecryptionKey)
  }

  override suspend fun decryptData(encryptedData: EncryptedData): ByteArray {
    val algorithm = when (encryptedData.kmsDecryptionKey.decryptionAlgorithm) {
      KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT -> EncryptionAlgorithmSpec.SYMMETRIC_DEFAULT
    }

    val privateKeyResponse = decrypt(
      DecryptRequest(
        encryptedData.kmsDecryptionKey.decryptionKeyId,
        encryptedData.value,
        algorithm,
      )
    ).await()

    return privateKeyResponse.plainText
  }

  private fun encrypt(encryptRequest: EncryptRequest): CompletableFuture<EncryptResponse> {
    val myBytes = SdkBytes.fromByteArray(encryptRequest.plainText)
    val awsEncryptRequest = AwsEncryptRequest.builder()
      .keyId(encryptRequest.keyId)
      .plaintext(myBytes)
      .encryptionAlgorithm(encryptRequest.algorithm)
      .build()

    return awsKmsService.encrypt(awsEncryptRequest).thenApply {
      if(it.encryptionAlgorithm() != null)
        EncryptResponse(it.keyId(),  it.ciphertextBlob().asByteArray(), it.encryptionAlgorithm() )
      else
        EncryptResponse(it.keyId(),  it.ciphertextBlob().asByteArray(), encryptRequest.algorithm )
    }
  }

  private fun decrypt(decryptRequest: DecryptRequest): CompletableFuture<DecryptResponse> {
    val myBytes = SdkBytes.fromByteArray(decryptRequest.cipherText)
    val awsDecryptRequest = AwsDecryptRequest.builder()
      .keyId(decryptRequest.keyId)
      .ciphertextBlob(myBytes)
      .encryptionAlgorithm(decryptRequest.algorithm)
      .build()

    return awsKmsService.decrypt(awsDecryptRequest).thenApply {
      if(it.encryptionAlgorithm() != null) {
        DecryptResponse(it.keyId(), it.plaintext().asByteArray(), it.encryptionAlgorithm())
      } else {
        DecryptResponse(it.keyId(), it.plaintext().asByteArray(), decryptRequest.algorithm)
      }
    }
  }

  override suspend fun listAliasNames(): ListOfAliasNames {
    val aliasNames = awsKmsService.listAliases().await().aliases().map { alias -> alias.aliasName() }

    return ListOfAliasNames(aliasNames)
  }

  override suspend fun describeKey(): KeyDescription {
    val describeKeyRequest = DescribeKeyRequest.builder().keyId(alias).build()
    val describeKeyResponse = awsKmsService.describeKey(describeKeyRequest).await()
    val returnedKeyId = describeKeyResponse.keyMetadata().keyId()
    val description = describeKeyResponse.keyMetadata().description()
    val arn = describeKeyResponse.keyMetadata().arn()
    val keyState = describeKeyResponse.keyMetadata().keyState().name
    return KeyDescription(returnedKeyId, description, arn, keyState)
  }

  override suspend fun healthcheck(): Boolean {
    val notDisabled = describeKey().keyState != KeyState.DISABLED.name
    val notUnavailable = describeKey().keyState != KeyState.UNAVAILABLE.name
    return notDisabled && notUnavailable
  }
}