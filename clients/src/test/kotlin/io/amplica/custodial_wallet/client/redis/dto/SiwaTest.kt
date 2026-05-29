package io.amplica.custodial_wallet.client.redis.dto

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test

class SiwaTest {

  data class RequestedCredentialsHolder(val requestedCredentials: List<RequestedCredential>)

  @Test
  fun deserializesRequestedCredentialsCorrectly() {
    // From: https://projectlibertylabs.github.io/siwa/DataStructures/All.html#request
    val jsonString = """
      {
        "requestedCredentials": [
          {
            "anyOf": [
              {
                "type": "VerifiedEmailAddressCredential",
                "hash": [
                  "bciqe4qoczhftici4dzfvfbel7fo4h4sr5grco3oovwyk6y4ynf44tsi"
                ]
              },
              {
                "type": "VerifiedPhoneNumberCredential",
                "hash": [
                  "bciqjspnbwpc3wjx4fewcek5daysdjpbf5xjimz5wnu5uj7e3vu2uwnq"
                ]
              }
            ]
          },
          {
            "type": "VerifiedGraphKeyCredential",
            "hash": [
              "bciqmdvmxd54zve5kifycgsdtoahs5ecf4hal2ts3eexkgocyc5oca2y"
            ]
          }
        ]
      }
    """.trimIndent()

    jacksonObjectMapper().readValue(jsonString, RequestedCredentialsHolder::class.java)
  }
}