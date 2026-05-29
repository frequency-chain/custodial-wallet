package io.amplica.custodial_wallet.service.ics.crypto

import com.goterl.lazysodium.LazySodiumJava
import com.goterl.lazysodium.SodiumJava
import com.goterl.lazysodium.interfaces.KeyExchange
import com.goterl.lazysodium.utils.LibraryLoader

object LazySodiumNaClProvider : NaClProvider {

  private const val ED_25519_PUBLIC_KEY_SIZE = 32
  private const val ED_25519_SECRET_KEY_SIZE = 32

  private val lazySodium = LazySodiumJava(SodiumJava(LibraryLoader.Mode.BUNDLED_ONLY))

  override fun cryptoBoxBeforeNm(secretKey: ByteArray, publicKey: ByteArray): ByteArray {
    val rootSharedSecretKey = ByteArray(32)
    val beforeNmOk = lazySodium.cryptoBoxBeforeNm(rootSharedSecretKey, publicKey, secretKey)
    require(beforeNmOk) { "`cryptoBoxBeforeNm` failed to create shared context" }

    return rootSharedSecretKey
  }

  override fun cryptoKdfDeriveFromKey(
    masterKey: ByteArray,
    context: ByteArray,
    subKeyId: Long,
    subKeyLength: Int
  ): ByteArray {
    val derivedSharedSecretKey = ByteArray(subKeyLength)
    val deriveOk = lazySodium.cryptoKdfDeriveFromKey(
      derivedSharedSecretKey,
      subKeyLength,
      subKeyId,
      context,
      masterKey
    )
    require(deriveOk == 0) { "`cryptoKdfDeriveFromKey` failed" }

    return derivedSharedSecretKey
  }

  override fun cryptoSecretBoxDetached(
    message: ByteArray,
    nonce: ByteArray,
    key: ByteArray
  ): AuthenticatedCipherText {
    val cipherText = ByteArray(message.size)
    val authenticationTag = ByteArray(16)

    val encryptOk = lazySodium.cryptoSecretBoxDetached(
      cipherText,
      authenticationTag,
      message,
      message.size.toLong(),
      nonce,
      key,
    )
    require(encryptOk) { "`cryptoSecretBoxDetached` failed" }

    return AuthenticatedCipherText(cipherText, authenticationTag)
  }

  override fun cryptoKxSeedKeypair(seed: ByteArray): KeyPair {
    require(seed.size == KeyExchange.SEEDBYTES) { "`seed` must have size: ${KeyExchange.SEEDBYTES}" }
    val publicKey = ByteArray(KeyExchange.PUBLICKEYBYTES)
    val secretKey = ByteArray(KeyExchange.SECRETKEYBYTES)

    val kxSeedOk = lazySodium.cryptoKxSeedKeypair(publicKey, secretKey, seed)
    require(kxSeedOk) { "`cryptoKxSeedKeypair` failed" }

    return KeyPair(publicKey, secretKey)
  }

  override fun cryptoSignEd25519PkToCurve25519(ed25519PublicKey: ByteArray): ByteArray {
    require(ed25519PublicKey.size == ED_25519_PUBLIC_KEY_SIZE) {
      "`ed25519PublicKey` must have size: $ED_25519_PUBLIC_KEY_SIZE"
    }

    val curve25519PublicKey = ByteArray(KeyExchange.PUBLICKEYBYTES)
    val conversionOk = lazySodium.convertPublicKeyEd25519ToCurve25519(curve25519PublicKey, ed25519PublicKey)
    require(conversionOk) { "`convertPublicKeyEd25519ToCurve25519` failed" }

    return curve25519PublicKey
  }

  override fun cryptoSignEd25519SkToCurve25519(ed25519SecretKey: ByteArray): ByteArray {
    require(ed25519SecretKey.size == ED_25519_SECRET_KEY_SIZE) {
      "`ed25519SecretKey` must have size: $ED_25519_SECRET_KEY_SIZE"
    }

    val curve25519SecretKey = ByteArray(KeyExchange.SECRETKEYBYTES)
    val conversionOk = lazySodium.convertSecretKeyEd25519ToCurve25519(curve25519SecretKey, ed25519SecretKey)
    require(conversionOk) { "`convertSecretKeyEd25519ToCurve25519` failed" }

    return curve25519SecretKey
  }

}
