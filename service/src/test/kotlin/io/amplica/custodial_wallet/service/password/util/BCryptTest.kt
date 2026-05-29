package io.amplica.custodial_wallet.service.password.util

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource


class BCryptTest {
  private val encoder = BCryptPasswordEncoder(4) // Use an (unsafe) small number of rounds to make the tests run quickly
  private val checker = BCryptPasswordChecker

  @ParameterizedTest
  @ValueSource(strings = ["password", "94553068", "!@#$%^&*"])
  fun encodingSucceeds(userPassword: String) {
    val encodedPassword = encoder.encode(userPassword)

    Assertions.assertTrue(checker.matches(encodedPassword, userPassword))
  }

  @ParameterizedTest
  @ValueSource(strings = ["كلمة المرور", "contraseña", "密码", "mật khẩu", "пароль"])
  fun encodingUtf16Succeeds(userPassword: String) {
    val encodedPassword = encoder.encode(userPassword, Charsets.UTF_16)

    Assertions.assertTrue(checker.matches(encodedPassword, userPassword, Charsets.UTF_16))
  }

  /**
   * Demonstrates that only the first 72 bytes are taken into account by bcrypt because passwords
   * identical in the first 72 bytes are considered matches.
   */
  @Test
  fun longPasswordsAreTruncated() {
    val longPassword = "a".repeat(72).toByteArray(Charsets.UTF_8) // Create a 72-byte password
    val encodedPassword = encoder.encode(longPassword)

    // Append extra bytes to the original password and check against the encoded password
    val differentPassword = longPassword + "xyz".toByteArray(Charsets.UTF_8)
    Assertions.assertTrue(checker.matches(encodedPassword, differentPassword))
  }
}