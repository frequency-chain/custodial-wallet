package io.amplica.custodial_wallet.verifiablecredentials.crypto

import org.apache.commons.codec.binary.Hex
import org.assertj.core.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class Ed25519SignatureManagerTest {

  @ParameterizedTest
  @CsvSource(value = [
    "1288ce31b8c6ad36b2bf77deed0571623493c0303040df38506c5d7fa57b02bf, 64dda6e27fa67aecf089a3c481e353658c5e6989866e209841f5e72937d0092a"
  ])
  fun newKeyPairFromSeed(secretSeedHex: String, expectedPublicKeyHex: String) {
    // GIVEN
    val secretSeed = Hex.decodeHex(secretSeedHex)

    // WHEN
    val keyPair = Ed25519SignatureManager.newKeyPairFromSeed(secretSeed)

    // THEN
    // NOTE: The secret seed *is* the private key
    val publicKeyHex = Hex.encodeHex(keyPair.publicKey).joinToString("")
    Assertions.assertThat(publicKeyHex).isEqualTo(expectedPublicKeyHex)
  }

  @ParameterizedTest
  @CsvSource(value = [
    "1288ce31b8c6ad36b2bf77deed0571623493c0303040df38506c5d7fa57b02bf, 'Hello, world', f37573886d5368068b3172408c578accc2153febbff5a9557416137db28f06dae52dff9e8c93f9f7b64f327d933a1b74a73e12f083c29a74a0147d323af8c50e"
  ])
  fun sign(privateKeyHex: String, data: String, expectedSignatureHex: String) {
    // GIVEN
    val privateKey = Hex.decodeHex(privateKeyHex)

    // WHEN
    val signature = Ed25519SignatureManager.sign(privateKey, data.toByteArray(Charsets.UTF_8))

    // THEN
    val signatureHex = Hex.encodeHex(signature).joinToString("")
    Assertions.assertThat(signatureHex).isEqualTo(expectedSignatureHex)
  }

  @ParameterizedTest
  @CsvSource(value = [
    "64dda6e27fa67aecf089a3c481e353658c5e6989866e209841f5e72937d0092a, 'Hello, world', f37573886d5368068b3172408c578accc2153febbff5a9557416137db28f06dae52dff9e8c93f9f7b64f327d933a1b74a73e12f083c29a74a0147d323af8c50e"
  ])
  fun verify(publicKeyHex: String, data: String, signatureHex: String) {
    // GIVEN
    val publicKey = Hex.decodeHex(publicKeyHex)
    val signature = Hex.decodeHex(signatureHex)

    // WHEN
    val isValid = Ed25519SignatureManager.verify(publicKey, data.toByteArray(Charsets.UTF_8), signature)

    // THEN
    Assertions.assertThat(isValid).isTrue()
  }

}
