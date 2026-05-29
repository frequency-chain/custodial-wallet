package io.amplica.custodial_wallet.orchestration.siwa

import io.amplica.custodial_wallet.client.redis.dto.FrequencyKeyPairDto
import io.amplica.custodial_wallet.client.redis.dto.FrequencyKeyType
import io.amplica.custodial_wallet.client.redis.dto.IdentifierVerification
import io.amplica.custodial_wallet.orchestration.generateToken
import io.amplica.custodial_wallet.util.key_creation.X25519KeyPair
import java.time.Instant


fun createNewIdentifierVerification(existingSendCount: Int?) = IdentifierVerification(
  generateToken(), Instant.now(), (existingSendCount ?: 0) + 1, 0
)

fun createFrequencyKeyPairDto(graphKeyPair: X25519KeyPair): FrequencyKeyPairDto {
  val graphKeyPairDto = graphKeyPair.toKeyPairDto()

  return FrequencyKeyPairDto(
    graphKeyPairDto.encodedPublicKeyValue,
    graphKeyPairDto.encodedPrivateKeyValue,
    graphKeyPairDto.encoding,
    graphKeyPairDto.format,
    graphKeyPairDto.type,
    FrequencyKeyType.PublicKeyKeyAgreement,
  )
}
