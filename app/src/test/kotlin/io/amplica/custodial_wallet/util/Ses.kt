package io.amplica.custodial_wallet.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI


// TODO: This file should be merged into `SesUtil`

const val SES_PATH = "/_aws/ses"

data class SesMessage(
  val Id: String,
  val Region: String,
  val Source: String,
  val Template: String?,
  val TemplateData: String?,
  val Destination: DestinationObject,
  val Subject: String?,
  val Body: EmailBody?
) {
  val recipients: List<String>
    get() {
      return Destination.ToAddresses
    }
}

data class DestinationObject(val ToAddresses: List<String>)

data class EmailBody(val text_part: String?, val html_part: String?)

fun getLastSesMessage(endpoint: String): SesMessage {
  return getSesMessages(endpoint).last()
}

fun getSesMessages(endpoint: String): List<SesMessage> {
  val payload = requestSesMessages(endpoint)

  return mapper
    .readValue(payload, SesResponse::class.java)
    .messages
}

private data class SesResponse(val messages: List<SesMessage>)

private val mapper: ObjectMapper = jacksonObjectMapper()
  .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

private fun requestSesMessages(endpoint: String): String {
  val sesUrl = endpoint + SES_PATH

  val connection = URI(sesUrl).toURL().openConnection() as HttpURLConnection
  connection.requestMethod = "GET"

  val input = BufferedReader(InputStreamReader(connection.inputStream))
  val content = StringBuilder()
  for (inputLine in input.readLines()) {
    content.append(inputLine)
  }
  input.close()

  return content.toString()
}
