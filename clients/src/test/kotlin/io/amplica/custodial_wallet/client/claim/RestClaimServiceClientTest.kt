package io.amplica.custodial_wallet.client.claim

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.amplica.custodial_wallet.client.redis.dto.LoginPayloadRequest
import io.amplica.custodial_wallet.client.redis.dto.PayloadType
import io.amplica.custodial_wallet.client.redis.dto.Signature
import io.amplica.custodial_wallet.client.redis.dto.TypedPayloadRequestWithSignature
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.web.SHARED_SECRET
import io.amplica.custodial_wallet.util.key_creation.Encoding
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto
import io.amplica.custodial_wallet.util.key_creation.PublicKeyFormat
import io.amplica.custodial_wallet.util.key_creation.SignatureKeyPairType
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class RestClaimServiceClientTest {
  private lateinit var restClaimServiceClient: RestClaimServiceClient
  private lateinit var server: WireMockServer

  @BeforeEach
  fun setup() {
    server = WireMockServer(wireMockConfig().dynamicPort())
    server.start()
    configureFor(server.port())
    restClaimServiceClient = RestClaimServiceClient(
      WebClient.builder()
        .baseUrl("http://localhost:${server.port()}")
        .defaultHeader(SHARED_SECRET, "1234")
        .build()
    )
  }

  @Test
  fun userAgree(): Unit = runBlocking {
    // GIVEN
    stubFor(
      post(urlEqualTo("/v2/user/agree"))
        .withRequestBody(
          equalToJson(
            "{ \"userPublicKey\": {\"encodedValue\": \"3259258c\", \"encoding\": \"base58\", \"format\": \"ss58\", \"type\": \"Sr25519\" }, " +
                "\"payloads\": [{\"signature\" : {\"algo\" : \"SR25519\", \"encoding\" : \"base16\", \"encodedValue\" : \"0x3259258c\" }, \"type\" : \"login\", " +
                "\"payload\" : {\"message\" : \"qa-claimfrequency.liberti.social wants you to sign in with your Substrate account:\\\\nVBsDrugZdAYEkTEJSBVLHTbzjQa2Le2JR5zWgshDXeyT4gwh9\\\\n\\\\nI agree to the terms for participation in the Frequency Community Rewards program.\\\\n\\\\nURI: https://qa-claimfrequency.liberti.social/signup/\\\\nVersion: 1.0.0\\\\nChain ID: frequency:0x203c6838fc78ea3660a2f298a58d859519c72a5efdc0f194abd6f0d5ce1838e0\\\\nNonce: 1745847016937-b0ae5230-9c88-4463-ba98-9e9327057a1a\\\\nIssued At: 2025-04-28T13:30:16.937Z\\\\nExpiration Time: 2025-04-28T13:40:16.937Z\"}}]}", true, true
          )
        )
        .willReturn(
          okJson(
            """{
                "termsTimestamp":1745847914000,
                "msaId":"12345n"
              }"""
          )
        )
    )

    // WHEN
    val response = restClaimServiceClient.userAgree(
      UserAgreeRequest(
        PublicKeyDto("3259258c", Encoding.BASE_58, PublicKeyFormat.SS58, KeyPairType.SR25519),
        listOf(
            TypedPayloadRequestWithSignature(
              Signature(SignatureKeyPairType.SR25519, Encoding.HEX,"0x3259258c"),
              PayloadType.LOGIN,
              LoginPayloadRequest("qa-claimfrequency.liberti.social wants you to sign in with your Substrate account:\\nVBsDrugZdAYEkTEJSBVLHTbzjQa2Le2JR5zWgshDXeyT4gwh9\\n\\nI agree to the terms for participation in the Frequency Community Rewards program.\\n\\nURI: https://qa-claimfrequency.liberti.social/signup/\\nVersion: 1.0.0\\nChain ID: frequency:0x203c6838fc78ea3660a2f298a58d859519c72a5efdc0f194abd6f0d5ce1838e0\\nNonce: 1745847016937-b0ae5230-9c88-4463-ba98-9e9327057a1a\\nIssued At: 2025-04-28T13:30:16.937Z\\nExpiration Time: 2025-04-28T13:40:16.937Z")
            )
        )
      )
    )

    // THEN
    assertThat(response).isEqualTo(
      UserAgreeResponse(
        1745847914000,
        "12345n",
      )
    )
  }

  @Test
  fun userAgreeReturnsError(): Unit = runBlocking {
    // GIVEN
    stubFor(
      post(urlEqualTo("/v2/user/agree"))
        .willReturn(
          aResponse().withStatus(400)
        )
    )

    // WHEN / THEN
    runCatching {
      restClaimServiceClient.userAgree(
        UserAgreeRequest(
          PublicKeyDto("3259258c", Encoding.BASE_58, PublicKeyFormat.SS58, KeyPairType.SR25519),
          listOf(
            TypedPayloadRequestWithSignature(
              Signature(SignatureKeyPairType.SR25519, Encoding.HEX,"0x3259258c"),
              PayloadType.LOGIN,
              LoginPayloadRequest("qa-claimfrequency.liberti.social wants you to sign in with your Substrate account:\\nVBsDrugZdAYEkTEJSBVLHTbzjQa2Le2JR5zWgshDXeyT4gwh9\\n\\nI agree to the terms for participation in the Frequency Community Rewards program.\\n\\nURI: https://qa-claimfrequency.liberti.social/signup/\\nVersion: 1.0.0\\nChain ID: frequency:0x203c6838fc78ea3660a2f298a58d859519c72a5efdc0f194abd6f0d5ce1838e0\\nNonce: 1745847016937-b0ae5230-9c88-4463-ba98-9e9327057a1a\\nIssued At: 2025-04-28T13:30:16.937Z\\nExpiration Time: 2025-04-28T13:40:16.937Z")
            )
          )
        )
      )
    }.onFailure { ex ->
      assertThat(ex).isInstanceOf(ApiException::class.java).extracting {
        (it as ApiException).apiError
      }.isEqualTo(ApiError.CLAIM_SERVICE_USER_AGREE_ERROR)
    }
  }

  @Test
  fun getNonce(): Unit = runBlocking {
    // GIVEN
    stubFor(
      get(urlEqualTo("/v2/user/nonce"))
        .willReturn(
          okJson(
            """{
                "nonce": "1745352567448-b2a13173-b613-4018-b33d-fcb59d788bd6",
                "issuedAt": 1745352567448,
                "expirationTime": 1745353167448,
                "domain": "rewards.frequency.xyz",
                "uri": "https://rewards.frequency.xyz/signup/",
                "chainId": "frequency:0x4a587bf17a404e3572747add7aab7bbe56e805a5479c6c436f07f36fcc8d3ae1",
                "statement": "I agree to the terms for participation in the Frequency Community Rewards program."
              }"""
          )
        )
    )

    // WHEN
    val response = restClaimServiceClient.getNonce()

    // THEN
    assertThat(response).isEqualTo(
      NonceResponse(
        "1745352567448-b2a13173-b613-4018-b33d-fcb59d788bd6",
        1745352567448,
        1745353167448,
        "rewards.frequency.xyz",
        "https://rewards.frequency.xyz/signup/",
        "frequency:0x4a587bf17a404e3572747add7aab7bbe56e805a5479c6c436f07f36fcc8d3ae1",
        "I agree to the terms for participation in the Frequency Community Rewards program."
      )
    )
  }

  @Test
  fun getNonceReturnsError(): Unit = runBlocking {
    // GIVEN
    stubFor(
      get(urlEqualTo("/v2/user/nonce"))
        .willReturn(
          aResponse().withStatus(500)
        )
    )

    runCatching {
      // WHEN
      restClaimServiceClient.getNonce()
    }.onFailure { ex ->
      // THEN
      assertThat(ex).isInstanceOf(ApiException::class.java)
      assertThat(ex).hasFieldOrPropertyWithValue("apiError", ApiError.CLAIM_SERVICE_GET_NONCE_ERROR)
    }
  }

}