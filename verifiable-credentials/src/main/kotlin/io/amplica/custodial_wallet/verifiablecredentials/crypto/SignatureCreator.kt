package io.amplica.custodial_wallet.verifiablecredentials.crypto


interface SignatureCreator {
  fun sign(privateKey: ByteArray, data: ByteArray): ByteArray
}
