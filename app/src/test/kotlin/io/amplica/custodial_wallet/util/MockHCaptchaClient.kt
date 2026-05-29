package io.amplica.custodial_wallet.util

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.amplica.custodial_wallet.client.captcha.HCaptchaClient

class MockHCaptchaClient: WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort()) {

  companion object {
    val mapper = jacksonObjectMapper()
  }

  init {
    this.start()
    configureFor(this.port())
    configureStubs()
  }

  private fun configureStubs() {
    stubFor(
      post(urlEqualTo(HCaptchaClient.VERIFY_CAPTCHA_ENDPOINT))
        .willReturn(
          okJson(
            """{
                                "success": "true", 
                                "score": "0.0", 
                                "sitekey": "siteKey"
                                }"""
          )
        )
    )
    stubFor(
      get(urlEqualTo(HCaptchaClient.HEALTHCHECK_ENDPOINT))
        .willReturn(
          okJson(
            """{
                      "status": {
                          "description": "All Systems Operational",
                          "indicator": "none"
                      }
                  }"""
          )
        )
    )
  }

  fun reset() {
    resetAll() // The resets the state, including removing all stubs
    configureStubs()
  }
}
