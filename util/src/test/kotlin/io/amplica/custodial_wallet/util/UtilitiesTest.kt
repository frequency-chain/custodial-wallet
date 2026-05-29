package io.amplica.custodial_wallet.util

import io.amplica.custodial_wallet.util.toHex
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.net.URI

class UtilitiesTest {

  @ParameterizedTest
  @CsvSource(value = [
    "https://localhost/path/to/resource, true",
    "http://localhost/path/to/resource, true",
    "file://localhost, true",
    "https://example.com, false",
    "https://127.0.0.1/path/to/resource, false",
    "https://localhost.malicious.xyz, false"
  ])
  fun hostIsLocalhost(url: String, isLocalHost: Boolean) {
    Assertions.assertEquals(isLocalHost, URI(url).hostIsLocalhost)
  }

  @ParameterizedTest
  @CsvSource(value = [
    "https://127.0.0.1/path/to/resource, true",
    "https://[::1]/path/to/resource, true", // NOTE: The syntax for IPv6 addresses is to wrap in square braces
    "file://127.0.0.1, false",
    "myscheme://login, false",
    "https://example.com, false",
    "https://192.168.0.1/path/to/resource, false",
    "https://[FE80:CD00:0:CDE:1257:0:211E:729C], false",
  ])
  fun hostIsLoopbackAddress(url: String, isLoopback: Boolean) {
    Assertions.assertEquals(isLoopback, URI(url).hostIsLoopbackAddress)
  }

  @ParameterizedTest
  @CsvSource(
    value =
      [
        "f6dEwwZbfvkfXJbypDaWYqk6Anvk1fma6RCmp3Th6Uy35Vg8p, 0xfc8a0111417906078d870ea99d8dcb37bee29cd05e1a01df1625654b8b0ae601", // 90
        "f6cL4wq1HUNx11TcvdABNf9UNXXoyH47mVUwT59tzSFRW8yDH, 0xd43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d", // 90
        "5HmpvJpdYNF7KEcskGwazp8ELW8vd8k9JNXtAhQUqzsuGZir, 0xfc8a0111417906078d870ea99d8dcb37bee29cd05e1a01df1625654b8b0ae601", // 42
        "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY, 0xd43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d", // 42
        "16i84e5hQ9WakmdPhuzb8xxPC88aKSJHNsGNKzPqQ5uRTAmq, 0xfc8a0111417906078d870ea99d8dcb37bee29cd05e1a01df1625654b8b0ae601", // 0
        "15oF4uVJwmo4TdGW7VfQxNLavjCXviqxT9S1MgbjMNHr6Sp5, 0xd43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d", // 0
        "aUuBHS3LZKnPuxyDJYhteYPwGWg932LjHPtbBQKQBA55F4B1T, 0xd43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d", // 1328
      ]
  )
  fun canDecodeSs58(ss58: String, hex: String) {
    Assertions.assertEquals(hex, toHex(base58DecodeAndExtractPublicKey(ss58)), "Expected decoded value of SS58 '$ss58' to match hex '$hex'")
  }
}
