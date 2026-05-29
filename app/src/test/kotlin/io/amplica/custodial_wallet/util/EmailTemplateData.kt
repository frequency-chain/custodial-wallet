package io.amplica.custodial_wallet.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions
import org.springframework.web.util.UriComponents
import org.springframework.web.util.UriComponentsBuilder


data class EmailTemplateData(
  val url: String,
  val expirationTimeMinutes: Long,
  val sessionId: String,
  val token: String
) {
  companion object {
    private val mapper: ObjectMapper =
      jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun fromString(templateData: String): EmailTemplateData {
      return mapper.readValue(templateData, EmailTemplateData::class.java)
    }
  }

  private val uriComponents: UriComponents
    get() = UriComponentsBuilder.fromUriString(url).build()

  fun getQueryParam(paramName: String): String {
    return uriComponents.queryParams.getFirst(paramName) ?: throw AssertionError("Query param is null")
  }

  val queryParams: EmailQueryParams
    get() {
      val token = getQueryParam("token")
      val sessionId = getQueryParam("sessionId")
      val timestamp = getQueryParam("t")

      Assertions.assertNotEquals("", token)
      Assertions.assertNotEquals("", sessionId)
      Assertions.assertNotEquals("", timestamp)

      return EmailQueryParams(token, sessionId, timestamp)
    }
}

data class EmailQueryParams(val token: String, val sessionId: String, val timestamp: String)

fun extractQueryParamFromUrl(templateData: String, paramName: String): String {
  return EmailTemplateData.fromString(templateData).getQueryParam(paramName)
}

fun retrieveEmailParams(message: SesMessage): EmailQueryParams {
  // Extract token, session id, and timestamp from email
  val templateData = EmailTemplateData.fromString(message.TemplateData!!)
  return templateData.queryParams
}
