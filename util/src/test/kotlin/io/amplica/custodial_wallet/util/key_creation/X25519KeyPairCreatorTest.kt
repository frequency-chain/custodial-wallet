package io.amplica.custodial_wallet.util.key_creation

import io.amplica.frequency.util.GraphConfiguration
import io.amplica.frequency.util.GraphHelper
import io.amplica.frequency.util.FrequencyEnvironment
import io.amplica.frequency.util.fromHex
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class X25519KeyPairCreatorTest {

  @Test
  fun isX25519KeyPairCreated() {
    val x25519KeyPair = X25519KeyPairCreator.createKeyPair()
    Assertions.assertNotNull(x25519KeyPair.privateKeyBytes)
    Assertions.assertNotNull(x25519KeyPair.publicKeyBytes)
  }

  @Test
  fun convertX25519KeyPairToKeyPairDto() {
    val x25519KeyPair = X25519KeyPairCreator.createKeyPair()
    val graphConfiguration = GraphConfiguration(FrequencyEnvironment.ROCOCO, listOf())
    val graphHelper = GraphHelper(graphConfiguration)
    val kpdto = X25519KeyPairCreator.createX25519PublicKeyDtoDsnpFormat(graphHelper, x25519KeyPair)

    Assertions.assertEquals(KeyPairType.X25519, kpdto.type)
    Assertions.assertEquals(PublicKeyFormat.DSNP_PUBLIC_KEY, kpdto.format)
    Assertions.assertNotNull(kpdto)
  }

  @Test
  fun convertHexEncodedKeyBackToX25519Key() {
    val graphConfiguration = GraphConfiguration(FrequencyEnvironment.ROCOCO, listOf())
    val graphHelper = GraphHelper(graphConfiguration)
    val x25519KeyPair = X25519KeyPairCreator.createKeyPair()
    val pubKey = graphHelper.convertToDsnpPublicKey(x25519KeyPair.publicKeyBytes)
    val kpdto = X25519KeyPairCreator.createX25519PublicKeyDtoDsnpFormat(graphHelper, x25519KeyPair)
    val convertedPubKey: ByteArray = fromHex(kpdto.encodedValue)

    Assertions.assertArrayEquals(pubKey, convertedPubKey)
  }

  @Test
  fun createX25519KeyPairDto() {
    val x25519KeyPair = X25519KeyPairCreator.createKeyPair()
    val kpdto = X25519KeyPairCreator.createX25519KeyPairDto(x25519KeyPair)

    Assertions.assertNotNull(kpdto)
    Assertions.assertEquals(kpdto.type, KeyPairType.X25519)
    Assertions.assertEquals(kpdto.format, PublicKeyFormat.BARE)
    Assertions.assertEquals(kpdto.encoding, Encoding.HEX)
    Assertions.assertNotEquals(kpdto.encodedPublicKeyValue, kpdto.encodedPrivateKeyValue)
  }
}