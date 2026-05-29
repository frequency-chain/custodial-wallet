package io.amplica.custodial_wallet.util.key_creation

import io.amplica.frequency.util.GraphHelper
import io.amplica.frequency.util.toHex
import io.amplica.frequency.crypto.provider.X25519CryptoProvider

class X25519KeyPair(publicKeyBytes: PublicKeyBytes, privateKeyBytes: PrivateKeyBytes) : KeyPairBytes(
  publicKeyBytes, privateKeyBytes, KeyPairType.X25519,
  KeyPairSignatureAlgorithm.ECDH
)

object X25519KeyPairCreator : KeyPairCreator {

  @Deprecated("Use X25519CryptoProvider instead", ReplaceWith("X25519CryptoProvider.createKeyPair()"))
  override fun createKeyPair(): X25519KeyPair {
    // the underlying library is using `OsRng` which is a `CSPRNG` to generate randomness, more info in following link
    // https://stackoverflow.com/questions/71048413/rng-getting-randomness-from-operating-system-vs-crypto-rngs
    val pair = X25519CryptoProvider.createKeyPair()
    val publicKeyBytes = pair.publicKeyBytes.bytes
    val privateKeyBytes = pair.privateKeyBytes.bytes

    return X25519KeyPair(publicKeyBytes, privateKeyBytes)
  }


  fun createX25519PublicKeyDtoDsnpFormat(graphHelper: GraphHelper, x25519KeyPair: X25519KeyPair): PublicKeyDto {
    return PublicKeyDto(
      toHex(graphHelper.convertToDsnpPublicKey(x25519KeyPair.publicKeyBytes)),
      Encoding.HEX,
      PublicKeyFormat.DSNP_PUBLIC_KEY,
      KeyPairType.X25519
    )
  }

  fun createX25519PublicKeyDtoBareFormat(x25519KeyPair: X25519KeyPair): PublicKeyDto {
    return PublicKeyDto(toHex(x25519KeyPair.publicKeyBytes), Encoding.HEX, PublicKeyFormat.BARE, KeyPairType.X25519)
  }

  fun createX25519KeyPairDto(x25519KeyPair: X25519KeyPair): KeyPairDto {
    return x25519KeyPair.toKeyPairDto()
  }

}
