package io.amplica.custodial_wallet.util.key_creation

import com.strategyobject.substrateclient.crypto.ss58.SS58AddressFormat
import io.amplica.custodial_wallet.util.base58Decode
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class Sr25519KeyPairCreatorTest {

  @Test
  fun isSr25519KeyPairCreated() {
    val sr25519KeyPair = Sr25519KeyPairCreator.createSr25519KeyPair()
    Assertions.assertEquals(KeyPairSignatureAlgorithm.SR25519, sr25519KeyPair.algo)
    Assertions.assertNotNull(sr25519KeyPair.privateKeyBytes)
    Assertions.assertNotNull(sr25519KeyPair.publicKeyBytes)
  }

  @Test
  fun convertSr25519KeyPairToKeyPairDto() {
    val sr25519KeyPair = Sr25519KeyPairCreator.createSr25519KeyPair()
    val kpdto = Sr25519KeyPairCreator.createSr25519PublicKeyDto(sr25519KeyPair, SS58AddressFormat.SUBSTRATE_ACCOUNT)

    Assertions.assertEquals(KeyPairType.SR25519, kpdto.type)
    Assertions.assertNotNull(kpdto)
  }

  @Test
  fun convertHexEncodedKeyBackToSr25519Key() {
    val sr25519KeyPair = Sr25519KeyPairCreator.createSr25519KeyPair()
    val pubKey = sr25519KeyPair.publicKeyBytes
    val kpdto = Sr25519KeyPairCreator.createSr25519PublicKeyDto(sr25519KeyPair, SS58AddressFormat.SUBSTRATE_ACCOUNT)
    val convertedPubKey: ByteArray = base58Decode(kpdto.encodedValue)

    Assertions.assertEquals(Sr25519KeyPairCreator.SS58_SIZE_BYTES, convertedPubKey.size)
    val pubKeyMaterial = convertedPubKey.slice(1..32)
    Assertions.assertArrayEquals(pubKey, pubKeyMaterial.toByteArray())
  }

  @Test
  fun encodedSr25519PublicKeyToSS58() {
    val sr25519KeyPair = Sr25519KeyPairCreator.createKeyPair()
    val pubKey = sr25519KeyPair.publicKeyBytes
    val address = Sr25519KeyPairCreator.encodeSr25519PublicKey(pubKey, SS58AddressFormat.SUBSTRATE_ACCOUNT)

    Assertions.assertNotNull(address)
  }
}