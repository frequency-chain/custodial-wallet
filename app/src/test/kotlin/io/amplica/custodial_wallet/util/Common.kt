package io.amplica.custodial_wallet.util

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.microsoft.playwright.options.Cookie
import io.amplica.custodial_wallet.client.redis.dto.SignUpResponse
import io.amplica.custodial_wallet.dto.SiwaPayloadResponse
import io.amplica.custodial_wallet.web.SESSION_ID_COOKIE_NAME
import io.netty.handler.codec.http.cookie.ClientCookieDecoder
import io.netty.handler.codec.http.cookie.DefaultCookie
import org.apache.http.entity.ContentType
import org.junit.jupiter.api.Assertions
import org.springframework.http.HttpHeaders
import org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils
import java.util.*

// Source: https://github.com/LibertyDSNP/frequency-schemas/blob/main/dsnp/index.ts#L243
val PROFILE_SCHEMA_IDS = listOf(15)
val GRAPH_SCHEMA_IDS = listOf(7, 8, 9, 10)
val TOMBSTONE_SCHEMA_IDS = listOf(1)
val BROADCAST_SCHEMA_IDS = listOf(2)
val REPLY_SCHEMA_IDS = listOf(3)
val UPDATE_SCHEMA_IDS = listOf(19)
val REACTION_SCHEMA_IDS = listOf(4)

const val BASE_HANDLE = "teddy"
const val HOST_NAME = "localhost"

fun createDefaultHttpHeaders(): HttpHeaders {
  val headers = HttpHeaders()
  headers.set(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
  headers.set(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())

  return headers
}

fun setCustomHeaders(headers: HttpHeaders, headerMap: Map<String,String>): HttpHeaders {
  headerMap.forEach {
    headers.set(it.key, it.value)
  }

  return headers
}

fun checkAndGetSessionIdFromCookieInHeaders(headers: HttpHeaders): String {
  Assertions.assertNotNull(headers)
  val cookieHeaders = headers[HttpHeaders.SET_COOKIE]
  Assertions.assertNotNull(cookieHeaders)
  val cookieMap: Map<String, DefaultCookie> = com.google.common.collect.FluentIterable.from(cookieHeaders!!)
    .transform { ClientCookieDecoder.LAX.decode(it) as DefaultCookie }.uniqueIndex { it?.name() }
  val sessionIdCookie = cookieMap[SESSION_ID_COOKIE_NAME]
  assertSessionIdCookie(sessionIdCookie)
  return sessionIdCookie!!.value()
}

fun generateUniqueEmail(): String {
  return "test+${UUID.randomUUID()}@amplica.io"
}

fun generateUniquePassword(): String {
  return RandomStringUtils.randomAscii(32)
}

fun generateUniquePhone(useTestPhonePrefix: Boolean): String {
  if(useTestPhonePrefix){
    return "+896268${(Math.random()*1000000).toInt()}"
  }
  val phoneNumberUtil = PhoneNumberUtil.getInstance()
  var phone = "+16268${(Math.random()*1000000).toInt()}"
  while (!phoneNumberUtil.isValidNumber(phoneNumberUtil.parse(phone,""))){
    phone = "+16268${(Math.random()*1000000).toInt()}"
  }
  return phone
}

fun generateHandle(): String {
  return BASE_HANDLE + (Math.random()*1000000).toInt()
}

fun findSessionIdCookie(cookies: List<Cookie>) = cookies.find { it.name == SESSION_ID_COOKIE_NAME} ?: Assertions.fail("SESSION_ID cookie not found")