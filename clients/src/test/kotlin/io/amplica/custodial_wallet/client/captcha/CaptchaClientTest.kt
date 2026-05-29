package io.amplica.custodial_wallet.client.captcha

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class CaptchaClientTest {
  private val testSecretKey = "0x0000000000000000000000000000000000000000"
  val siteKeyGood = "20000000-ffff-ffff-ffff-000000000002"
  private val responseTokenGood = "20000000-aaaa-bbbb-cccc-000000000002"
  val siteKeyBad = "30000000-ffff-ffff-ffff-000000000003"
  private val responseTokenBad = "30000000-aaaa-bbbb-cccc-000000000003"

  @BeforeEach
  fun setup() {

  }

  suspend fun verifyCaptchaStatusGood(hCaptchaClient: HCaptchaClient) {
    //when
    val response = hCaptchaClient.verifyCaptcha(
      VerifyCaptchaRequest(responseTokenGood, "1.1.1.1"),
      false,
    )

    //then
    Assertions.assertThat(response).isEqualTo(
      CaptchaStatus.GOOD
    )
  }

  suspend fun verifyCaptchaStatusBad(hCaptchaClient: HCaptchaClient) {
    //when
    val response = hCaptchaClient.verifyCaptcha(
      VerifyCaptchaRequest(responseTokenBad, "1.1.1.1"),
      false,
    )

    //then
    Assertions.assertThat(response).isEqualTo(
      CaptchaStatus.BAD
    )
  }

  suspend fun verifyCaptchaStatusIgnoringScore(hCaptchaClient: HCaptchaClient) {
    //WHEN
    val response = hCaptchaClient.verifyCaptcha(
      VerifyCaptchaRequest(responseTokenBad, "1.1.1.1"),
      true,
    )

    //THEN
    Assertions.assertThat(response).isEqualTo(
      CaptchaStatus.GOOD
    )
  }

  suspend fun verifyCaptchaStatusError(hCaptchaClient: HCaptchaClient) {
    val badResponseToken = "bbaadd-ttookkeenn-rreessppoonnssee"
    val result = runCatching {
      hCaptchaClient.verifyCaptcha(
        VerifyCaptchaRequest(badResponseToken, "1.1.1.1"),
        false,
      )
    }.onFailure {
      Assertions.assertThat(it)
        .isInstanceOf(ApiException::class.java)
        .hasFieldOrPropertyWithValue("apiError", ApiError.HCAPTCHA_SITE_VERIFY_REQUEST_ERROR)
        .hasFieldOrPropertyWithValue(
          "message",
          "Error Encountered attempting to send POST request to hCaptcha for request: $badResponseToken with error: Error Codes returned from hCaptcha Request - invalid-input-response"
        )
    }
    Assertions.assertThat(result.isFailure).isTrue()
  }

  @Nested
  inner class LiveHCaptchaClientTests {
    private lateinit var testHCaptchaClientGood: HCaptchaClient
    private lateinit var testHCaptchaClientBad: HCaptchaClient

    @BeforeEach
    fun setUp() {
      val realVerifyWebClient = WebClient.builder()
        .baseUrl("https://api.hcaptcha.com")
        .build()

      val realStatusWebClient = WebClient.builder()
        .baseUrl("https://www.hcaptchastatus.com")
        .build()

      testHCaptchaClientGood = HCaptchaClient(
        enabled = true,
        siteKeyGood,
        realVerifyWebClient,
        realStatusWebClient,
        testSecretKey,
        includeUserIP = true,
        "not_applicable",
      )
      testHCaptchaClientBad = HCaptchaClient(
        enabled = true,
        siteKeyBad,
        realVerifyWebClient,
        realStatusWebClient,
        testSecretKey,
        includeUserIP = true,
        "not_applicable",
      )
    }

    @Test
    fun verifySiteCaptchaStatusGood(): Unit = runBlocking {
      Assumptions.assumeTrue(testHCaptchaClientGood.healthcheck())
      verifyCaptchaStatusGood(testHCaptchaClientGood)
    }

    @Test
    fun liveVerifyCaptchaStatusBad(): Unit = runBlocking {
      Assumptions.assumeTrue(testHCaptchaClientGood.healthcheck())
      verifyCaptchaStatusBad(testHCaptchaClientBad)
    }

    @Test
    fun liveVerifyCaptchaStatusError(): Unit = runBlocking {
      Assumptions.assumeTrue(testHCaptchaClientGood.healthcheck())
      verifyCaptchaStatusError(testHCaptchaClientBad)
    }

  }

  @Nested
  inner class MockHCaptchaClientTests {
    private lateinit var mockHCaptchaClient: HCaptchaClient
    private lateinit var server: WireMockServer

    @BeforeEach
    fun setUp() {
      server = WireMockServer(wireMockConfig().dynamicPort())
      server.start()
      configureFor(server.port())

      val mockWebClient = WebClient.builder()
        .baseUrl("http://localhost:${server.port()}")
        .build()

      mockHCaptchaClient =
        HCaptchaClient(
          enabled = true,
          "siteKey",
          mockWebClient,
          mockWebClient,
          "secretKey",
          includeUserIP = true,
          "xCaptchaHeaderValueSecret"
        )
    }

    @AfterEach
    fun tearDown() {
      server.resetAll()
    }

    @Test
    fun mockVerifyCaptchaStatusGood(): Unit = runBlocking {
      //prepare
      stubFor(
        post(urlEqualTo(HCaptchaClient.VERIFY_CAPTCHA_ENDPOINT))
          .willReturn(
            okJson(
              """{
                                "success": "true", 
                                "score": "0.0", 
                                "sitekey": "$siteKeyGood"
                                }"""
            )
          )
      )
      verifyCaptchaStatusGood(mockHCaptchaClient)
    }

    @Test
    fun mockVerifyCaptchaStatusBad(): Unit = runBlocking {
      //prepare
      stubFor(
        post(urlEqualTo(HCaptchaClient.VERIFY_CAPTCHA_ENDPOINT))
          .willReturn(
            okJson(
              """{
                                "success": "false", 
                                "score": "1.0", 
                                "sitekey": "$siteKeyGood"
                                }"""
            )
          )
      )
      verifyCaptchaStatusBad(mockHCaptchaClient)
    }

    @Test
    fun mockVerifyCaptchaStatusError(): Unit = runBlocking {
      //prepare
      stubFor(
        post(urlEqualTo(HCaptchaClient.VERIFY_CAPTCHA_ENDPOINT))
          .willReturn(
            okJson(
              """{
                                "success": "false", 
                                "score": "1.0", 
                                "sitekey": "$siteKeyBad",
                                "error-codes": ["invalid-input-response"]
                                }"""
            )
          )
      )
      verifyCaptchaStatusError(mockHCaptchaClient)
    }

    @Test
    fun mockVerifyCaptchaStatusIgnoringScore(): Unit = runBlocking {
      //prepare
      stubFor(
        post(urlEqualTo(HCaptchaClient.VERIFY_CAPTCHA_ENDPOINT))
          .willReturn(
            okJson(
              //A score of 1.0 would normally cause failure, but we are ignoring the score
              """{
                                "success": "true", 
                                "score": "1.0", 
                                "sitekey": "$siteKeyBad"
                                }"""
            )
          )
      )
      verifyCaptchaStatusIgnoringScore(mockHCaptchaClient)
    }
  }
}
