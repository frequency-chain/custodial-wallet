package io.amplica.custodial_wallet.util

import io.amplica.frequency.crypto.provider.Secp256K1CryptoProvider
import io.amplica.frequency.crypto.toPublicKeyBytes
import io.amplica.custodial_wallet.util.key_creation.PayloadBytes
import io.amplica.custodial_wallet.util.key_creation.PublicKeyBytes
import io.amplica.custodial_wallet.util.key_creation.SignatureBytes
import org.web3j.crypto.Sign

@Deprecated("Ethereum implementation has moved to the `saas-frequency-client`")
object EthereumHelper {

  /*
   * To verify signatures in web3j we reverse engineer the public key from the payload/message bytes and the signature
   * We do this by using web3js standard for storing signature data which has 3 sections. R, S, V.
   * R + S is the signature in 2 32-bit parts.
   * V is the recoveryId which is needed for rebuilding the public key but not actually a part of the signature
   * Web3j provides the workhorse method for recovering the public key so all
   * we need to do is compare the recovered public key to the provided one and see if they match.
   * Also, there is no way to know if a message has been wrapped with the Etherum Message Prefix. So we are forced
   * to check it both with and without as we do not want to expect the consumer of this message to track that info.
   */
  @Deprecated("This method combines hashing and signing steps; use a `HashingStrategy` and `Secp256K1CryptoProvider` instead.")
  fun verifySignature(
    publicKeyBytes: PublicKeyBytes,
    payloadBytes: PayloadBytes,
    signatureBytes: SignatureBytes,
  ): Boolean {
    val signatureData = Sign.signatureDataFromHex(toHex(signatureBytes))
    val providedPublicKey = Secp256K1CryptoProvider.coercePublicKeyBytesToScalar(publicKeyBytes.toPublicKeyBytes())

    var recoveredPublicKey = Sign.signedPrefixedMessageToKey(payloadBytes, signatureData)
    if (providedPublicKey == recoveredPublicKey) {
      return true
    } else {
      recoveredPublicKey = Sign.signedMessageToKey(payloadBytes, signatureData)
      return providedPublicKey == recoveredPublicKey
    }
  }

}