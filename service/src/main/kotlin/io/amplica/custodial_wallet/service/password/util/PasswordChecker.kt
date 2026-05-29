package io.amplica.custodial_wallet.service.password.util

import java.nio.charset.Charset

/**
 * Intended to be implemented by an `object` without any configuration to make backwards compatibility easy to maintain.
 */
interface PasswordChecker {
  fun matches(encodedPassword: String, rawPassword: ByteArray): Boolean

  /* Helper method to handle serializing strings to bytes */
  fun matches(encodedPassword: String, rawPassword: String, charset: Charset = PasswordEncoder.DEFAULT_CHARSET): Boolean {
    return matches(encodedPassword, rawPassword.toByteArray(charset))
  }
}
