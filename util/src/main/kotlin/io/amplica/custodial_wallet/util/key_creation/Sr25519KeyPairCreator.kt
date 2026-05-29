package io.amplica.custodial_wallet.util.key_creation

import com.strategyobject.substrateclient.crypto.ss58.SS58AddressFormat
import io.amplica.custodial_wallet.util.toBase58AddressFormat
import io.amplica.frequency.crypto.provider.Sr25519CryptoProvider

class Sr25519KeyPairBytes(
  publicKeyBytes: ByteArray,
  privateKeyBytes: ByteArray,
  override val algo: KeyPairSignatureAlgorithm = KeyPairSignatureAlgorithm.SR25519
) : KeyPairBytes(publicKeyBytes, privateKeyBytes, KeyPairType.SR25519, algo)

object Sr25519KeyPairCreator: KeyPairCreator {
  const val SS58_SIZE_BYTES = 35

  @Deprecated("Use Sr25519CryptoProvider instead", ReplaceWith("Sr25519CryptoProvider.createKeyPair()"))
  override fun createKeyPair(): Sr25519KeyPairBytes {
    val keyPair = Sr25519CryptoProvider.createKeyPair()
    val publicKeyBytes = keyPair.publicKeyBytes.bytes
    val privateKeyBytes = keyPair.privateKeyBytes.bytes

    return Sr25519KeyPairBytes(publicKeyBytes, privateKeyBytes)
  }

  @Deprecated("Use Sr25519CryptoProvider's createKeyPair() instead", ReplaceWith("Sr25519CryptoProvider.createKeyPair()"))
  fun createSr25519KeyPair(): Sr25519KeyPairBytes {
    return createKeyPair()
  }

  fun createSr25519PublicKeyDto(sr25519KeyPair: KeyPairBytes, addressFormat: SS58AddressFormat): PublicKeyDto {
    return PublicKeyDto(
      encodeSr25519PublicKey(sr25519KeyPair.publicKeyBytes, addressFormat),
      Encoding.BASE_58,
      PublicKeyFormat.SS58,
      KeyPairType.SR25519
    )
  }

  fun encodeSr25519PublicKey(publicKeyBytes: PublicKeyBytes, addressFormat: SS58AddressFormat) : String {
    return toBase58AddressFormat(publicKeyBytes, addressFormat)
  }


}
