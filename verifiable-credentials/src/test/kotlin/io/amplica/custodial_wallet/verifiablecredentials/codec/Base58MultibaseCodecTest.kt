package io.amplica.custodial_wallet.verifiablecredentials.codec

import org.apache.commons.codec.binary.Hex
import org.assertj.core.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class Base58MultibaseCodecTest {

  @ParameterizedTest
  @CsvSource(value =  [
    "18CE31BF, zdmxke",
    "0000, z11" // No '0' in base-58
  ])
  fun encode(inputHex: String, expected: String) {
    // GIVEN
    val input = Hex.decodeHex(inputHex)

    // WHEN
    val output = Base58MultibaseCodec.encode(input)

    // THEN
    Assertions.assertThat(output).isEqualTo(expected)
  }

  @ParameterizedTest
  @CsvSource(value =  [
    "zdmxke, 18CE31BF",
    "z11, 0000"
  ])
  fun decode(input: String, expectedHex: String) {
    // WHEN
    val output = Base58MultibaseCodec.decode(input)

    // THEN
    val expected = Hex.decodeHex(expectedHex)
    Assertions.assertThat(output).isEqualTo(expected)
  }

  @ParameterizedTest
  @CsvSource(value =  [
    "updog, Unexpectedly found prefix 'u' when attempting to decode", // ...
    "z==, InvalidCharacter in base 58"
  ])
  fun decodeThrows(input: String, errorMessage: String) {
    Assertions.assertThatThrownBy { Base58MultibaseCodec.decode(input) }.hasMessageStartingWith(errorMessage)
  }

}
