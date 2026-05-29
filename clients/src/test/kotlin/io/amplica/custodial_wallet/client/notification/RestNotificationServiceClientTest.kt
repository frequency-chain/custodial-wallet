package io.amplica.custodial_wallet.client.notification

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.amplica.custodial_wallet.client.redis.generateUUID
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.web.SHARED_SECRET
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient

class RestNotificationServiceClientTest {
  private lateinit var restNotificationServiceClient: RestNotificationServiceClient
  private lateinit var server: WireMockServer

  @BeforeEach
  fun setup() {
    server = WireMockServer(wireMockConfig().dynamicPort())
    server.start()
    configureFor(server.port())
    restNotificationServiceClient = RestNotificationServiceClient(
      WebClient.builder()
        .baseUrl("http://localhost:${server.port()}")
        .defaultHeader(SHARED_SECRET, "1234")
        .build()
    )
  }

  @Test
  fun sendSms(): Unit = runBlocking {
    // GIVEN
    stubFor(
      post(urlEqualTo("/api/sms/send"))
        .willReturn(
          okJson(
            """{
                "id":"567656765",
                "request":{
                  "destinationPhoneNumber":"+15553332222",
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

    // WHEN
    val response = restNotificationServiceClient.sendSms(
      SendSmsRequest("+15553332222", "Hello, world!", null, null),
      generateUUID(),
      generateUUID(),
      generateUUID(),
    )

    // THEN
    assertThat(response).isEqualTo(
      SendSmsResponse(
        "567656765",
        "+15553332222",
        "+16268183501",
        "Hello, world!"
      )
    )
  }

  @Test
  fun sendSmsReturnsError(): Unit = runBlocking {
    // GIVEN
    stubFor(
      post(urlEqualTo("/api/sms/send"))
        .willReturn(
          jsonResponse(
            """{
                "id":2,
                "message":"Error Description"
              }""",
            HttpStatus.INTERNAL_SERVER_ERROR.value()
          )
        )
    )

    // WHEN / THEN
    runCatching {
      restNotificationServiceClient.sendSms(
        SendSmsRequest("+15553332222", "Hello, world!", null, null),
        generateUUID(),
        generateUUID(),
        generateUUID(),
      )
    }.onFailure { ex ->
      assertThat(ex).isInstanceOf(ApiException::class.java).extracting {
        (it as ApiException).apiError
      }.isEqualTo(ApiError.BLOCKED_CONTACT_METHOD)
    }
  }

  @ParameterizedTest
  @CsvSource(
    delimiter = '|', // Avoid having to escape the commas in JSON
    value = [
      """{"isBlocked":"false"} | false""",
      """{"isBlocked":"true", "reason":"FORBIDDEN_PREFIX"} | true""",
    ]
  )
  fun lookupPhoneNumber(blockStatusJson: String, blockIsExpected: Boolean) {
    // GIVEN
    val phoneNumber = "+15553332222"
    stubFor(
      get(urlEqualTo("/api/lookup/phoneNumber/$phoneNumber"))
        .willReturn(
          okJson(
            """{
                "phoneNumber":"+15553332222",
                "blockStatus":$blockStatusJson
              }"""
          )
        )
    )

    // WHEN
    val response = runBlocking {
      restNotificationServiceClient.lookupPhoneNumber(phoneNumber)
    }

    // THEN
    assertThat(response.blockStatus.isBlocked).isEqualTo(blockIsExpected)
  }

  @Test
  fun lookupPhoneNumberReturnsError(): Unit = runBlocking {
    // GIVEN
    val phoneNumber = "+15553332222"
    stubFor(
      get(urlEqualTo("/api/lookup/phoneNumber/$phoneNumber"))
        .willReturn(
          jsonResponse(
            """{
                "id":0,
                "message":"Error Description"
              }""",
            HttpStatus.INTERNAL_SERVER_ERROR.value()
          )
        )
    )

    runCatching {
      // WHEN
      restNotificationServiceClient.lookupPhoneNumber(phoneNumber)
    }.onFailure { ex ->
      // THEN
      assertThat(ex).isInstanceOf(ApiException::class.java)
      assertThat(ex).hasFieldOrPropertyWithValue("apiError", ApiError.UNKNOWN_NOTIFICATION_SERVICE_ERROR)
    }
  }

}