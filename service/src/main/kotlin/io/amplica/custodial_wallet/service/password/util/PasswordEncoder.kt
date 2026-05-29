package io.amplica.custodial_wallet.service.password.util

import java.nio.charset.Charset


interface PasswordEncoder {
  companion object {
    val DEFAULT_CHARSET = Charsets.UTF_8
  }

  fun encode(rawPassword: ByteArray): String

  /* Helper method to handle serializing strings to bytes */
  fun encode(rawPassword: String, charset: Charset = DEFAULT_CHARSET): String {
    return encode(rawPassword.toByteArray(charset))
  }
}