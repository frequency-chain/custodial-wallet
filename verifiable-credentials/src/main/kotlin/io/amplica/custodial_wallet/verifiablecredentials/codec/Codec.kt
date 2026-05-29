package io.amplica.custodial_wallet.verifiablecredentials.codec


interface Codec<A, B> {
  fun encode(input: A): B
  fun decode(input: B): A
}
