package io.amplica.custodial_wallet.internationalization

import com.github.mustachejava.DefaultMustacheFactory
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.core.io.DefaultResourceLoader
import java.util.*

class DefaultMessageFactoryTest {

  companion object {
    private val mustacheFactory = DefaultMustacheFactory()
    private val mustacheTemplateResolver = MustacheTemplateResolver(mustacheFactory, DefaultResourceLoader())
    private val messageFactory = MustacheMessageFactory(mustacheTemplateResolver)
    const val HANDLE = "handlebarMustache"
    const val CODE = "325345"
    const val BASE_MESSAGE = "Hi $HANDLE! Welcome to Frequency Access. Your verification code is: $CODE"
    const val TEMPLATE_NAME = "verification"
    const val TEST_TEMPLATE_NAME = "test"
  }

  @Test
  fun getRealDefaultMessage() {
    val message =
      messageFactory.createMessage(TEMPLATE_NAME, createVerificationContext(), null, null)
    Assertions.assertEquals(
      BASE_MESSAGE,
      message
    )
  }

  @Test
  fun getDefaultMessage() {
    val message =
      messageFactory.createMessage(
        TEST_TEMPLATE_NAME,
        createVerificationContext(),
        null,
        null
      )
    Assertions.assertEquals(
      "Default - $BASE_MESSAGE",
      message
    )
  }

  @Test
  fun getMessageWithLocale() {
    val message =
      messageFactory.createMessage(
        TEST_TEMPLATE_NAME,
        createVerificationContext(),
        null,
        Locale.ENGLISH
      )
    Assertions.assertEquals(
      "en - $BASE_MESSAGE",
      message
    )
  }

  @Test
  fun getMessageWithLocaleSpecific() {
    val message =
      messageFactory.createMessage(
        TEST_TEMPLATE_NAME,
        createVerificationContext(),
        null,
        Locale.Builder().setLanguage("en").setRegion("us").build()
      )
    println(message)
    Assertions.assertEquals(
      "en us - $BASE_MESSAGE",
      message
    )
  }

  @Test
  fun getFallbackMessageWithLocale() {
    val message =
      messageFactory.createMessage(
        TEST_TEMPLATE_NAME,
        createVerificationContext(),
        null,
        Locale.Builder().setLanguage("en").setRegion("uk").build()
      )
    Assertions.assertEquals(
      "en - $BASE_MESSAGE",
      message
    )
  }

  @Test
  fun getMessageWithProvider() {
    val message =
      messageFactory.createMessage(
        TEST_TEMPLATE_NAME,
        createVerificationContext(),
        "mewe",
        null
      )
    Assertions.assertEquals(
      "mewe - $BASE_MESSAGE",
      message
    )
  }

  @Test
  fun getMessageWithProviderAndLocale() {
    val message =
      messageFactory.createMessage(
        TEST_TEMPLATE_NAME,
        createVerificationContext(),
        "mewe",
        Locale.Builder().setLanguage("en").setRegion("us").build()
      )
    Assertions.assertEquals(
      "mewe en us - $BASE_MESSAGE",
      message
    )
  }

  @Test
  fun getFallbackMessageWithProviderAndLocale() {
    val message =
      messageFactory.createMessage(
        TEST_TEMPLATE_NAME,
        createVerificationContext(),
        "mewe",
        Locale.CHINESE
      )
    Assertions.assertEquals(
      "mewe - $BASE_MESSAGE",
      message
    )
  }

  private fun createVerificationContext(): Map<String, String> {
    return mapOf(Pair("handle", HANDLE), Pair("code", CODE))
  }
}