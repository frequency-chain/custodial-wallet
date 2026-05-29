package io.amplica.custodial_wallet.verifiablecredentials.crypto


interface SignatureVerifier {
  fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean
}
