package io.amplica.custodial_wallet.controller.util

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.*

class CachingLocalizationUtilTest {
  companion object {
    private val LOCALE: Locale = Locale.US
    private val EXPECTED_UNESCAPED_MESSAGES = mapOf("foo" to "bar")
    private val EXPECTED_ESCAPED_MESSAGES = mapOf("baz" to "biff")
  }
  private lateinit var delegate: LocalizationUtil
  private lateinit var cachingLocalizationUtil: CachingLocalizationUtil
  @BeforeEach
  fun setUp() {
    delegate = mock()
    cachingLocalizationUtil = CachingLocalizationUtil(delegate, 10)

    whenever(delegate.getUnescapedMessagesForLocale(LOCALE)).thenReturn(EXPECTED_UNESCAPED_MESSAGES)
    whenever(delegate.getEscapedMessagesForLocale(LOCALE)).thenReturn(EXPECTED_ESCAPED_MESSAGES)
  }

  @Test
  fun getUnescapedMessagesForLocale() {
    //GIVEN WHEN
    val messages = cachingLocalizationUtil.getUnescapedMessagesForLocale(LOCALE)

    //THEN
    Assertions.assertThat(messages).isSameAs(EXPECTED_UNESCAPED_MESSAGES)
  }

  @Test
  fun getUnescapedMessagesForLocaleCached() {
    //GIVEN
    getUnescapedMessagesForLocale()

    //WHEN
    val messages = cachingLocalizationUtil.getUnescapedMessagesForLocale(LOCALE)

    //THEN
    Assertions.assertThat(messages).isSameAs(EXPECTED_UNESCAPED_MESSAGES)
    verify(delegate, times(1)).getUnescapedMessagesForLocale(LOCALE)
  }

  @Test
  fun getEscapedMessagesForLocale() {
    //GIVEN WHEN
    val messages = cachingLocalizationUtil.getEscapedMessagesForLocale(LOCALE)

    //THEN
    Assertions.assertThat(messages).isSameAs(EXPECTED_ESCAPED_MESSAGES)
  }

  @Test
  fun getEscapedMessagesForLocaleCached() {
    //GIVEN
    getEscapedMessagesForLocale()

    //WHEN
    val messages = cachingLocalizationUtil.getEscapedMessagesForLocale(LOCALE)

    //THEN
    Assertions.assertThat(messages).isSameAs(EXPECTED_ESCAPED_MESSAGES)
    verify(delegate, times(1)).getEscapedMessagesForLocale(LOCALE)
  }
}
