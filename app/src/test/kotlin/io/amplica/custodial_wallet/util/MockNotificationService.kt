package io.amplica.custodial_wallet.util

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.amplica.custodial_wallet.client.notification.SendSmsRequest
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class MockNotificationService: WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort()) {

  companion object {
    const val HEALTH_CHECK_ENDPOINT = "/actuator/health"
    const val SMS_SEND_ENDPOINT = "/api/sms/send"
    val mapper = jacksonObjectMapper()
  }

  init {
    this.start()
    configureFor(this.port())
    configureStubs()
  }

  private fun configureStubs() {
    // Health check
    stubFor(
      get(urlEqualTo(HEALTH_CHECK_ENDPOINT))
        .willReturn(
          okJson(
            """{"status":"UP"}"""
          )
        )
    )

    stubFor(
      post(urlEqualTo(SMS_SEND_ENDPOINT))
        .willReturn(
          okJson(
            """{
                "id":"example-ns-message-id",
                "request":{
                  "destinationPhoneNumber":"+155555555555",
                  "messageBody":"Hello, world!"
                },
                "details":{
                  "platform":"Twilio",
                  "platformMessageId":"123456789",
                  "sourcePhoneNumber":"+16268183501"
                }
              }"""
          )
        )
    )
    stubFor(
      get(urlPathTemplate("/api/lookup/phoneNumber/{phoneNumber}"))
        .withPathParam("phoneNumber", equalTo("+18484448888"))
        .willReturn(
          okJson(
            """{
                "phoneNumber":"+18484448888",
                "blockStatus":{"isBlocked":"true", "reason":"FORBIDDEN_PREFIX"}
              }"""
          )
        ).atPriority(1)
    )
    stubFor(
      get(urlPathTemplate("/api/lookup/phoneNumber/{phoneNumber}"))
        .willReturn(
          okJson(
            """{
                "phoneNumber":"+16268403496",
                "blockStatus":{"isBlocked":"false"}
              }"""
          )
        ).atPriority(2)
    )
  }

  fun reset() {
    resetAll() // The resets the state, including removing all stubs
    configureStubs()
  }

  fun verifySmsSendInvokedOnce() {
    verify(1, postRequestedFor(urlEqualTo(SMS_SEND_ENDPOINT)))
  }

  private fun getAllSmsSendRequests(): List<SendSmsRequest> {
    return allServeEvents.filter { it.request.url.equals(SMS_SEND_ENDPOINT) }.map {
      mapper.readValue(it.request.body, SendSmsRequest::class.java)
    }
  }

  fun getLastSmsSendRequest(): SendSmsRequest = getAllSmsSendRequests().first()

}