package io.amplica.custodial_wallet.util

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration


class MockClaimService : WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort()) {

  companion object {
    const val NONCE_ENDPOINT = "/v2/user/nonce"
    const val OPT_IN_ENDPOINT = "/v2/user/agree"
  }

  init {
    start()
    configureFor(port())
    configureStubs()
  }

  private fun configureStubs() {
    stubFor(
      get(urlEqualTo(NONCE_ENDPOINT)).willReturn(
        okJson(
          // An example response lifted from: https://qa-claimfrequency.liberti.social/docs/
          """
              {
                "nonce": "1745352567448-b2a13173-b613-4018-b33d-fcb59d788bd6",
                "issuedAt": 1745352567448,
                "expirationTime": 1745353167448,
                "domain": "rewards.frequency.xyz",
                "uri": "https://rewards.frequency.xyz/",
                "chainId": "frequency:0x4a587bf17a404e3572747add7aab7bbe56e805a5479c6c436f07f36fcc8d3ae1",
                "statement": "I agree to the terms for participation in the Frequency Community Rewards program."
              }
            """.trimIndent()
        )
      )
    )

    stubFor(
      post(
        urlEqualTo(OPT_IN_ENDPOINT)
      ).withRequestBody(
        equalToJson(
          // For JSON matching see: https://wiremock.org/docs/request-matching/#placeholders
          // For $ shenanigans see: https://kotlinlang.org/docs/strings.html#string-templates
          """
            {
              "userPublicKey": {
                "encodedValue": "${'$'}{json-unit.any-string}",
                "encoding": "base58",
                "format": "ss58",
                "type": "Sr25519"
              },
              "payloads": [{
                "type": "login",
                "payload": {
                  "message": "${'$'}{json-unit.any-string}"
                },
                "signature": {
                  "algo": "SR25519",
                  "encoding": "base16",
                  "encodedValue": "${'$'}{json-unit.any-string}"
                }
              }]
            }
          """.trimIndent()
        )
      )
        .willReturn(
          okJson("""{"termsTimestamp": 1745353167448, "msaId": "478"}""")
        )
    )

    stubFor(
      post(
        urlEqualTo(OPT_IN_ENDPOINT)
      ).withRequestBody(
        equalToJson(
          // For JSON matching see: https://wiremock.org/docs/request-matching/#placeholders
          // For $ shenanigans see: https://kotlinlang.org/docs/strings.html#string-templates
          """
            {
              "userPublicKey": {
                "encodedValue": "${'$'}{json-unit.any-string}",
                "encoding": "base16",
                "format": "bare",
                "type": "SECP256K1"
              },
              "payloads": [{
                "type": "login",
                "payload": {
                  "message": "${'$'}{json-unit.any-string}"
                },
                "signature": {
                  "algo": "SECP256K1",
                  "encoding": "base16",
                  "encodedValue": "${'$'}{json-unit.any-string}"
                }
              }]
            }
          """.trimIndent()
        )
      )
        .willReturn(
          okJson("""{"termsTimestamp": 1745353167448, "msaId": "478"}""")
        )
    )
  }

  fun reset() {
    resetAll() // The resets the state, including removing all stubs
    configureStubs()
  }
}