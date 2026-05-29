package io.amplica.custodial_wallet.service.password.util

import org.springframework.security.crypto.bcrypt.BCrypt
import java.security.SecureRandom
import java.util.regex.Pattern


/**
 * Notes:
 * - [rounds] must be 10 or greater in production to be sufficiently secure.
 * - The computational complexity increase exponentially (2^strength) as [rounds] increases
 */
data class BCryptPasswordEncoder(val rounds: Int) : PasswordEncoder {
  val random = SecureRandom()

  /**
   * Notes:
   * - Truncates [rawPassword] to 56 bytes
   * - Generates a random 16 byte salt (2 base-64 digits between version and encoded value)
   *
   * @return A string in the format: `"$2a$<rounds>$<salt><hash>"` where `rounds` is base 10 and
   *         `salt` and `hash` are encoded using a custom base-64 scheme. `salt` has a length of 22 characters
   *         and `hash` has a length of 31.
   */
  override fun encode(rawPassword: ByteArray): String {
    val salt = BCrypt.gensalt(rounds, random) // Generate a (securely) random 16 byte salt
    return BCrypt.hashpw(rawPassword, salt)
  }
}


object BCryptPasswordChecker: PasswordChecker {
  private val ENCODED_PASSWORD_PATTERN: Pattern = Pattern.compile(
    "\\A\\$2[aby]?\\$\\d\\d\\$[./0-9A-Za-z]{53}"
  )

  override fun matches(encodedPassword: String, rawPassword: ByteArray): Boolean {
    if (!ENCODED_PASSWORD_PATTERN.matcher(encodedPassword).matches()) {
      throw IllegalArgumentException("`encodedPassword` is not a valid BCrypt-encoded password")
    }

    return BCrypt.checkpw(rawPassword, encodedPassword)
  }
}