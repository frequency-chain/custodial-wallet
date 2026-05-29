package io.amplica.custodial_wallet.caip122

import com.github.mustachejava.DefaultMustacheFactory
import io.amplica.custodial_wallet.internationalization.MustacheMessageFactory
import io.amplica.custodial_wallet.internationalization.MustacheTemplateResolver
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.core.io.DefaultResourceLoader

class Caip122MessageFactoryTest {

  companion object {
    private val mustacheFactory = DefaultMustacheFactory()
    private val caip122mustacheTemplateResolver = MustacheTemplateResolver(mustacheFactory, DefaultResourceLoader(), "classpath:/templates/caip122/")
    private val caip122MessageFactory = MustacheMessageFactory(caip122mustacheTemplateResolver)
    const val domain = "qa-claimfrequency.liberti.social"
    const val chainReference = "chainReference"
    const val ss58Address = "0x203c6838fc78ea3660a2f298a58d859519c72a5efdc0f194abd6f0d5ce1838e0"
    const val version = 1
    const val uri = "https://qa-claimfrequency.liberti.social/signup/"
    const val nonce = "1745847016937-b0ae5230-9c88-4463-ba98-9e9327057a1a"
    const val issuedAt = "2025-04-29T18:24:26.060222Z"
    const val statement = "I agree to the terms for participation in the Frequency Community Rewards program."
  }

  @Test
  fun createCaip122LoginPayloadResponseNoStatement(): Unit = runBlocking {
    val expectedMessage = """qa-claimfrequency.liberti.social wants you to sign in with your Frequency account:
frequency:chainReference:0x203c6838fc78ea3660a2f298a58d859519c72a5efdc0f194abd6f0d5ce1838e0

URI: https://qa-claimfrequency.liberti.social/signup/
Version: 1
Nonce: 1745847016937-b0ae5230-9c88-4463-ba98-9e9327057a1a
Chain ID: frequency:chainReference
Issued At: 2025-04-29T18:24:26.060222Z
"""
    val message = caip122MessageFactory.createMessage(
      "caip122",
      mapOf(
        "domain" to domain,
        "chainReference" to chainReference,
        "userAddress" to ss58Address,
        "uri" to uri,
        "version" to version,
        "nonce" to nonce,
        "issuedAt" to issuedAt,
      ),
      "mewe",
      locale = null,
    )
    Assertions.assertEquals(expectedMessage, message)
  }

  @Test
  fun createCaip122LoginPayloadResponseWithStatement(): Unit = runBlocking {
    val expectedMessage = """qa-claimfrequency.liberti.social wants you to sign in with your Frequency account:
frequency:chainReference:0x203c6838fc78ea3660a2f298a58d859519c72a5efdc0f194abd6f0d5ce1838e0

I agree to the terms for participation in the Frequency Community Rewards program.

URI: https://qa-claimfrequency.liberti.social/signup/
Version: 1
Nonce: 1745847016937-b0ae5230-9c88-4463-ba98-9e9327057a1a
Chain ID: frequency:chainReference
Issued At: 2025-04-29T18:24:26.060222Z
"""
    val message = caip122MessageFactory.createMessage(
      "caip122",
      mapOf(
        "domain" to domain,
        "chainReference" to chainReference,
        "userAddress" to ss58Address,
        "uri" to uri,
        "version" to version,
        "nonce" to nonce,
        "issuedAt" to issuedAt,
        "statement" to statement,
        "showStatement" to true,
      ),
      "mewe",
      locale = null,
    )
    Assertions.assertEquals(expectedMessage, message)
  }
}