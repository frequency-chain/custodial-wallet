package io.amplica.custodial_wallet.client.claim

import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import reactor.kotlin.core.publisher.toMono

class RestClaimServiceClient(private val webClient: WebClient) : ClaimServiceClient {
  override suspend fun userAgree(userAgreeRequest: UserAgreeRequest): UserAgreeResponse {
    val response = webClient.post()
      .uri("/v2/user/agree")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(userAgreeRequest)
      .retrieve()
      .onStatus(HttpStatusCode::isError) { _ ->
        ApiException(
          ApiError.CLAIM_SERVICE_USER_AGREE_ERROR,
          "Claim Service bad request or validation error."
        ).toMono()
      }
      .awaitBody<UserAgreeResponse>()

    return response
  }

  override suspend fun getNonce(): NonceResponse {
    val response = webClient.get()
      .uri("/v2/user/nonce")
      .retrieve()
      .onStatus(HttpStatusCode::isError) { _ ->
        ApiException(
          ApiError.CLAIM_SERVICE_GET_NONCE_ERROR,
          "Claim Service bad request or validation error."
        ).toMono()
      }
      .awaitBody<NonceResponse>()

    return response
  }
}