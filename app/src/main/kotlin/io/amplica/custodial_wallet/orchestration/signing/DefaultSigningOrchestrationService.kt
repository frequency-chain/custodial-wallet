package io.amplica.custodial_wallet.orchestration.signing

import com.strategyobject.substrateclient.crypto.ss58.SS58AddressFormat
import io.amplica.custodial_wallet.client.redis.dto.Signature
import io.amplica.custodial_wallet.util.encodeValueFromBytes
import io.amplica.custodial_wallet.util.key_creation.Encoding
import io.amplica.custodial_wallet.util.key_creation.KeyPairBytes
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto
import io.amplica.frequency.crypto.AccountKeyPair
import io.amplica.frequency.crypto.AccountPublicKey
import io.amplica.frequency.crypto.toPrivateKeyBytes
import io.amplica.frequency.crypto.toPublicKeyBytes
import io.amplica.frequency.crypto.toSignatureBytes
import io.amplica.frequency.service.SigningService
import io.amplica.frequency.crypto.provider.Secp256K1CryptoProvider
import io.amplica.frequency.crypto.provider.Sr25519CryptoProvider
import io.amplica.frequency.serialization.FrequencySerializable


class DefaultSigningOrchestrationService(
  private val signingService: SigningService,
  private val sS58AddressFormat: SS58AddressFormat,
) : SigningOrchestrationService {

  private fun keyPairBytesToAccountKeyPair(keyPairBytes: KeyPairBytes): AccountKeyPair {
    val cryptoProvider = when (keyPairBytes.keyPairType) {
      KeyPairType.SR25519 -> Sr25519CryptoProvider
      KeyPairType.SECP256K1 -> Secp256K1CryptoProvider
      else -> throw IllegalArgumentException("KeyPairType is not an account key pair: ${keyPairBytes.keyPairType}")
    }

    return AccountKeyPair(
      keyPairBytes.publicKeyBytes.toPublicKeyBytes(),
      keyPairBytes.privateKeyBytes.toPrivateKeyBytes(),
      cryptoProvider,
    )
  }

  private fun publicKeyDtoToAccountPublicKey(dto: PublicKeyDto): AccountPublicKey {
    val cryptoProvider = when (dto.type) {
      KeyPairType.SR25519 -> Sr25519CryptoProvider
      KeyPairType.SECP256K1 -> Secp256K1CryptoProvider
      else -> throw IllegalArgumentException("KeyPairType is not an account key pair: ${dto.type}")
    }

    return AccountPublicKey(
      dto.toPublicKeyBytes().toPublicKeyBytes(), cryptoProvider
    )
  }

  override fun signPayload(
    keyPairBytes: KeyPairBytes,
    payload: FrequencySerializable<Any>,
    encoding: Encoding,
  ): Signature {
    val keyPair = keyPairBytesToAccountKeyPair(keyPairBytes)
    val signatureBytes = signingService.signPayload(keyPair, payload)

    return Signature(
      keyPairBytes.keyPairType.signatureKeyPairType,
      encoding,
      encodeValueFromBytes(signatureBytes.bytes, encoding, sS58AddressFormat)
    )
  }

  override fun verifySignedPayload(
    publicKeyDto: PublicKeyDto, payload: FrequencySerializable<Any>, signature: Signature
  ): Boolean {
    val accountPublicKey = publicKeyDtoToAccountPublicKey(publicKeyDto)

    return signingService.verifySignedPayload(
      accountPublicKey,
      payload,
      signature.toSignatureBytes().toSignatureBytes()
    )
  }

  override fun signMessage(keyPairBytes: KeyPairBytes, message: String, encoding: Encoding): Signature {
    val keyPair = keyPairBytesToAccountKeyPair(keyPairBytes)
    val signatureBytes = signingService.signMessage(keyPair, message)

    return Signature(
      keyPairBytes.keyPairType.signatureKeyPairType,
      encoding,
      encodeValueFromBytes(signatureBytes.bytes, encoding, sS58AddressFormat)
    )
  }

  override fun verifySignedMessage(publicKeyDto: PublicKeyDto, message: String, signature: Signature): Boolean {
    val accountPublicKey = publicKeyDtoToAccountPublicKey(publicKeyDto)

    return signingService.verifySignedMessage(
      accountPublicKey,
      message,
      signature.toSignatureBytes().toSignatureBytes()
    )
  }

  override fun signPublicKey(keyPairBytes: KeyPairBytes, message: ByteArray, encoding: Encoding): Signature {
    val keyPair = keyPairBytesToAccountKeyPair(keyPairBytes)
    val signatureBytes = signingService.signPublicKey(keyPair, message)

    return Signature(
      keyPairBytes.keyPairType.signatureKeyPairType,
      encoding,
      encodeValueFromBytes(signatureBytes.bytes, encoding, sS58AddressFormat)
    )
  }

  override fun verifySignedPublicKey(publicKeyDto: PublicKeyDto, message: ByteArray, signature: Signature): Boolean {
    val accountPublicKey = publicKeyDtoToAccountPublicKey(publicKeyDto)

    return signingService.verifySignedPublicKey(
      accountPublicKey,
      message,
      signature.toSignatureBytes().toSignatureBytes()
    )
  }

}
