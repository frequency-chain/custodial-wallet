package io.amplica.custodial_wallet.verifiablecredentials.codec

import org.apache.commons.codec.binary.Hex
import org.assertj.core.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class MultikeyCodecTest {

  @ParameterizedTest
  @CsvSource(value =  [
    "Ed25519, 1288CE31B8C6AD36B2BF77DEED0571623493C0303040DF38506C5D7FA57B02BF, z6MkfhcNjLE6A3EKzS9BiX7c6LMkHdNTnftUZwHy4PPjrHR8",
    "Sr25519, 1288CE31B8C6AD36B2BF77DEED0571623493C0303040DF38506C5D7FA57B02BF, z6QNmFn8sD7EeCmn2f542N1P9M2nEUqwDE2tC1gTk22iFySN",
  ])
  fun encode(keyType: KeyType, inputHex: String, expected: String) {
    // GIVEN
    val codec = MultikeyCodec(keyType, Base58MultibaseCodec)
    val input = Hex.decodeHex(inputHex)

    // WHEN
    val output = codec.encode(input)

    // THEN
    Assertions.assertThat(output).isEqualTo(expected)
  }

  @ParameterizedTest
  @CsvSource(value =  [
    "Ed25519, z6MkfhcNjLE6A3EKzS9BiX7c6LMkHdNTnftUZwHy4PPjrHR8, 1288CE31B8C6AD36B2BF77DEED0571623493C0303040DF38506C5D7FA57B02BF",
    "Sr25519, z6QNmFn8sD7EeCmn2f542N1P9M2nEUqwDE2tC1gTk22iFySN, 1288CE31B8C6AD36B2BF77DEED0571623493C0303040DF38506C5D7FA57B02BF",
  ])
  fun decode(keyType: KeyType, input: String, expectedHex: String) {
    // GIVEN
    val codec = MultikeyCodec(keyType, Base58MultibaseCodec)

    // WHEN
    val output = codec.decode(input)

    // THEN
    val expected = Hex.decodeHex(expectedHex)
    Assertions.assertThat(output).isEqualTo(expected)
  }

  @ParameterizedTest
  @CsvSource(value =  [
    // Prefixes in encoded data are wrong
    "Ed25519, z6QNmFn8sD7EeCmn2f542N1P9M2nEUqwDE2tC1gTk22iFySN, 1288CE31B8C6AD36B2BF77DEED0571623493C0303040DF38506C5D7FA57B02BF",
    "Sr25519, z6MkfhcNjLE6A3EKzS9BiX7c6LMkHdNTnftUZwHy4PPjrHR8, 1288CE31B8C6AD36B2BF77DEED0571623493C0303040DF38506C5D7FA57B02BF",
  ])
  fun decodeThrows(keyType: KeyType, input: String, expectedHex: String) {
    // GIVEN
    val codec = MultikeyCodec(keyType, Base58MultibaseCodec)

    // WHEN
    Assertions.assertThatThrownBy { codec.decode(input) }
      // THEN
      .isInstanceOf(IllegalArgumentException::class.java)
  }

}
