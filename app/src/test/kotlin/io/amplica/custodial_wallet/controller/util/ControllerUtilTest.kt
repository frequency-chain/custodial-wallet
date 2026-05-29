package io.amplica.custodial_wallet.controller.util

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.http.HttpHeaders

class ControllerUtilTest {

  @Nested
  @DisplayName("getPermissionKeysForSchemaIds")
  inner class GetPermissionKeysForSchemaIdsTests {

    @Test
    fun succeeds() {
      val result = getPermissionKeysForSchemaIds(
        listOf(1, 2),
        mapOf(setOf(1) to "permissions.one", setOf(2) to "permissions.two"),
        null,
      )

      Assertions.assertThat(result).isEqualTo(listOf("permissions.one", "permissions.two"))
    }

    @Test
    fun allowsUndefinedSchemaIds() {
      val result = getPermissionKeysForSchemaIds(
        listOf(1,2,3,4,5),
        mapOf(setOf(1) to "permissions.one"),
        null,
      )

      Assertions.assertThat(result).isEqualTo(listOf("permissions.one"))
    }

    @Test
    fun allowsUnusedMapKeys() {
      val result = getPermissionKeysForSchemaIds(
        listOf(1),
        mapOf(setOf(1) to "permissions.one", setOf(2) to "permissions.two"),
        null,
      )

      Assertions.assertThat(result).isEqualTo(listOf("permissions.one"))
    }

    @Test
    fun throwsForSubsetsOfMapKeys() {
      Assertions.assertThatThrownBy { getPermissionKeysForSchemaIds(
        listOf(1),
        mapOf(setOf(1, 2) to "permissions.one.two", setOf(3) to "permissions.three"),
        null
      ) }.hasMessageStartingWith("Invalid Schema IDs")
    }
  }

  @ParameterizedTest
  @CsvSource(
    delimiter = '|',
    value = [
      " | ", // Header missing
      "'' | ", // Header is empty string
      "1.0.0.0 | 1.0.0.0",
      "1.0.0.0, 2.0.0.0 | 1.0.0.0",
      "1.0.0.0, 2.0.0.0, 3.1.2.7, 0.0.20.308 | 1.0.0.0"
    ],
  )
  fun getClientIpAddressTest(headerValue: String?, expectedClientIp: String?) {
    // GIVEN
    val headers = HttpHeaders().apply {
      if (headerValue != null) {
        set("X-Forwarded-For", headerValue)
      }
    }

    // WHEN
    val result = getClientIpAddress(headers)

    // THEN
    Assertions.assertThat(result).isEqualTo(expectedClientIp)
  }

}