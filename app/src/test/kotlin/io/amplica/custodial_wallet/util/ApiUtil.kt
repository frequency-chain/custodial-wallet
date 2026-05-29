package io.amplica.custodial_wallet.util

import com.fasterxml.jackson.databind.ObjectMapper
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.dto.OrganizationDataBody
import io.amplica.custodial_wallet.dto.SiwaPayloadResponse
import io.amplica.custodial_wallet.orchestration.siwa.TokenResponse
import io.amplica.custodial_wallet.web.AUTHORIZATION_CODE_PARAMETER_NAME
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.ResponseEntity
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI


class ApiUtil(private val testRestTemplate: TestRestTemplate, private val objectMapper: ObjectMapper) {

  fun postBatchPayloadRequest(request: BatchPayloadToSignRequest): ResponseEntity<Unit> {
    return postRequest(URI("/api/sign/batch"), request, Unit::class.java)
  }

  fun postSiwaSaveProviderRequest(request: SiwaRequest): ResponseEntity<Unit> {
    return postRequest(URI.create("/siwa/api/request"), request, Unit::class.java)
  }

  fun postSiwaProviderRequest(request: SiwaRequest): ResponseEntity<String> {
    return postRequest(URI("/siwa/start"), request, String::class.java)
  }

  fun getSiwaTokenForSessionId(sessionId: String): ResponseEntity<TokenResponse> {
    return getRequest(URI("/testing/siwa/token/$sessionId"), TokenResponse::class.java)
  }

  fun getSiwaPayloadRequest(authorizationCode: String): ResponseEntity<SiwaPayloadResponse> {
    val stringResponseEntity = getRequest(
      UriComponentsBuilder.fromUriString("/siwa/api/payload?authorizationCode={$AUTHORIZATION_CODE_PARAMETER_NAME}")
        .buildAndExpand(mapOf(Pair(AUTHORIZATION_CODE_PARAMETER_NAME, authorizationCode))).toUri(),
      String::class.java
    )

    // NOTE(Peter, 2024-08-20): Simplifies the process of updating the contract document
    val stringBody = stringResponseEntity.body
    return ResponseEntity.status(stringResponseEntity.statusCode).headers(stringResponseEntity.headers)
      .body(objectMapper.readValue(stringBody, SiwaPayloadResponse::class.java))
  }

  fun postOrganizationData(body: OrganizationDataBody): ResponseEntity<Unit> {
    return postRequest(URI("/api/admin/organization"), body, Unit::class.java)
  }

  private fun <RESPONSE> getRequest(uri: URI, responseType: Class<RESPONSE>): ResponseEntity<RESPONSE> {
    return testRestTemplate.getForEntity(uri, responseType)
  }

  private fun <REQUEST, RESPONSE> postRequest(uri: URI, request: REQUEST, responseType : Class<RESPONSE>): ResponseEntity<RESPONSE> {
    val httpEntity = HttpEntity(request, createDefaultHttpHeaders())
    return testRestTemplate.postForEntity(
      uri, httpEntity, responseType
    )
  }

  private fun <REQUEST, RESPONSE> postRequestWithHeaders(uri: URI, request: REQUEST, headers: Map<String,String>, responseType: Class<RESPONSE>): ResponseEntity<RESPONSE> {
    val requestHeaders = setCustomHeaders(createDefaultHttpHeaders(), headers)
    val httpEntity = HttpEntity(request, requestHeaders)
    return testRestTemplate.postForEntity(
      uri, httpEntity, responseType
    )
  }

  private fun <REQUEST, RESPONSE> postRequestBody(uri: URI, request: REQUEST, responseType : Class<RESPONSE>): RESPONSE {
    val response = postRequest(uri, request, responseType)
    return response.body ?: throw AssertionError("Empty body")
  }

  private fun <REQUEST> postRequestPage(uri: URI, request: REQUEST): String {
    return postRequestBody(uri, request, String::class.java)
  }
}
