package io.amplica.custodial_wallet.orchestration.signing

import io.amplica.custodial_wallet.client.redis.dto.Signature
import io.amplica.custodial_wallet.util.key_creation.Encoding
import io.amplica.custodial_wallet.util.key_creation.KeyPairBytes
import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto
import io.amplica.frequency.serialization.FrequencySerializable


interface SigningOrchestrationService {
  /** Serializes, hashes, and signs a payload using the strategies inferred from the given `keyPairType` */
  fun signPayload(
    keyPairBytes: KeyPairBytes,
    payload: FrequencySerializable<Any>,
    encoding: Encoding = Encoding.HEX
  ): Signature

  /**
   * Serializes, hashes, and verifies a signature of a payload using the strategies inferred
   * from the given `keyPairType`
   */
  fun verifySignedPayload(
    publicKeyDto: PublicKeyDto,
    payload: FrequencySerializable<Any>,
    signature: Signature
  ): Boolean

  /** Serializes using UTF-8, and then hashes and signs using the strategies inferred from the given `keyPairType` */
  fun signMessage(keyPairBytes: KeyPairBytes, message: String, encoding: Encoding = Encoding.HEX): Signature

  /** Serializes using UTF-8, and then hashes and verifies using the strategies inferred from the given `keyPairType` */
  fun verifySignedMessage(publicKeyDto: PublicKeyDto, message: String, signature: Signature): Boolean

  /** Serializes, hashes, and signs a public key (`message`) using the strategies inferred from the given `keyPairType` */
  fun signPublicKey(keyPairBytes: KeyPairBytes, message: ByteArray, encoding: Encoding = Encoding.HEX): Signature

  /**
   * Serializes, hashes, and verifies a signature of a public key (`message`) using the strategies inferred
   * from the given `keyPairType`.
   */
  fun verifySignedPublicKey(publicKeyDto: PublicKeyDto, message: ByteArray, signature: Signature): Boolean

}
