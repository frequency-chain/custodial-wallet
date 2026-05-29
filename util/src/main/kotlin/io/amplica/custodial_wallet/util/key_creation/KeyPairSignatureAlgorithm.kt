package io.amplica.custodial_wallet.util.key_creation

import com.google.common.collect.FluentIterable
import java.util.*

enum class KeyPairSignatureAlgorithm(val algorithm: String){
  SR25519("SR25519"),
  ECDSA("ECDSA"),
  ECDH("ECDH"), //This is kinda bullshit as it's a definition of a Key exchange but I need something here
  // for X25519 which doesn't generate signatures but does key exchanges
  ED25519("ED25519"),
  ;

  companion object {
    @Suppress("UnstableApiUsage") //from is marked @Beta but its been beta for years
    private val ALGORITHM_INDEX: Map<String, KeyPairSignatureAlgorithm> = FluentIterable.from(entries.toTypedArray()).uniqueIndex { it.algorithm.uppercase(
      Locale.US) }

    fun fromAlgorithm(algorithm: String): KeyPairSignatureAlgorithm {
      return ALGORITHM_INDEX[algorithm.uppercase(Locale.US)]
        ?: throw IllegalArgumentException("Algorithm=${algorithm} is not recognized")
    }
  }
}