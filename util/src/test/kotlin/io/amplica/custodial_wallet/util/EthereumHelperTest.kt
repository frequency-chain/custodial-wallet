package io.amplica.custodial_wallet.util

import io.amplica.frequency.util.fromHex
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class EthereumHelperTest {

  @ParameterizedTest
  @CsvSource(value = [
    "0xf7fd90d6eda243bd23310c6500a0402134b7430877a82feea3c911321c8e38ac435162dcbcbf63de775b3a3e0d3fa3c8ca7dde60f22108b0846d7c72d6490cb41b, true",
    "0x3419ca02ff4f1533d2ce91955a2446ab5e55fb88d01bd9bbf283bebd42dfb04231652586f03cde66f798967459b80d14c6bd4a3bd7c740acc08e39c1e0292f841b, true",
    "0x800c89fa6b02fec2d0c6615c78bc0f52bc694e63dd6a76e8d4ea418832fa8727611e50bfc2218ec4db48cfb3824b2f274751ff50d4d67e2fb351c6996dc350c61c, false",
  ])
  fun verifyPayload(signatureHex: String, expected: Boolean) {
    // GIVEN
    val publicKeyBytes = fromHex(
      "0x4fa2727eb25acddc75480196270754bb1b4da48a2c9896b225e4bd0732a27c589b257bdef1c1b585ff6c8bba2938cfb58f3ea95c9c7a6eb383159f70c841b2ba"
    )
    val payload = "Hello World".toByteArray()
    val signature = fromHex(signatureHex)

    // WHEN
    val isValidSignature = EthereumHelper.verifySignature(publicKeyBytes, payload, signature)

    // THEN
    assertThat(isValidSignature).isEqualTo(expected)
  }

}