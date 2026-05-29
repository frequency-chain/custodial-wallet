package io.amplica.custodial_wallet.util.key_creation

import com.fasterxml.jackson.databind.exc.ValueInstantiationException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class KeyPairTypesTest {

  private val jsonPublicKeyDtoCorrect = "{\"encodedValue\":\"5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY\",\"encoding\":\"base58\",\"format\":\"ss58\",\"type\":\"Sr25519\"}"
  private val jsonPublicKeyDtoAlternative = "{\"encodedValue\":\"5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY\",\"encoding\":\"BASE58\",\"format\":\"SS58\",\"type\":\"SR25519\"}"
  private val jsonPublicKeyDtoWrong = "{\"encodedValue\":\"5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY\",\"encoding\":\"bad58\",\"format\":\"sad58\",\"type\":\"Sad25519\"}"

  @Test
  fun deserializeEncoding() {
    val encodingJson = jacksonObjectMapper().writeValueAsString(Encoding.BASE_58)
    val encoding = jacksonObjectMapper().readValue(encodingJson, Encoding::class.java)
    Assertions.assertEquals(encoding, Encoding.BASE_58)
  }

  @Test
  fun deserializePublicKeyFormat() {
    val publicKeyFormatJson = jacksonObjectMapper().writeValueAsString(PublicKeyFormat.SS58)
    val publicKeyFormat = jacksonObjectMapper().readValue(publicKeyFormatJson, PublicKeyFormat::class.java)
    Assertions.assertEquals(publicKeyFormat, PublicKeyFormat.SS58)
  }

  @Test
  fun deserializeKeyPairType() {
    val keyPairTypeJson = jacksonObjectMapper().writeValueAsString(KeyPairType.SR25519)
    val keyPairType = jacksonObjectMapper().readValue(keyPairTypeJson, KeyPairType::class.java)
    Assertions.assertEquals(keyPairType, KeyPairType.SR25519)
  }

  @Test
  fun deserializeCorrectPublicKeyDto() {
    val publicKeyDto = jacksonObjectMapper().readValue(jsonPublicKeyDtoCorrect, PublicKeyDto::class.java)
    Assertions.assertEquals(publicKeyDto.encoding, Encoding.BASE_58)
    Assertions.assertEquals(publicKeyDto.format, PublicKeyFormat.SS58)
    Assertions.assertEquals(publicKeyDto.type, KeyPairType.SR25519)
  }

  @Test
  fun deserializeAlternatePublicKeyDto() {
    val publicKeyDto = jacksonObjectMapper().readValue(jsonPublicKeyDtoAlternative, PublicKeyDto::class.java)
    Assertions.assertEquals(publicKeyDto.encoding, Encoding.BASE_58)
    Assertions.assertEquals(publicKeyDto.format, PublicKeyFormat.SS58)
    Assertions.assertEquals(publicKeyDto.type, KeyPairType.SR25519)
  }

  @Test
  fun deserializeWrongPublicKeyDto() {
    Assertions.assertThrows(ValueInstantiationException::class.java) {
      jacksonObjectMapper().readValue(
        jsonPublicKeyDtoWrong,
        PublicKeyDto::class.java
      )
    }
  }


}