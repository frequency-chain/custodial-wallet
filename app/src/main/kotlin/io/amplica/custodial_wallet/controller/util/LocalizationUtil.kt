package io.amplica.custodial_wallet.controller.util

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import java.util.*

interface LocalizationUtil {
  fun getUnescapedMessagesForLocale(locale: Locale): Map<String, String>
  fun getEscapedMessagesForLocale(locale: Locale) : Map<String, String>
}

class CachingLocalizationUtil(private val delegate: LocalizationUtil,
  maxCacheSize: Long) : LocalizationUtil {
  private val unescapedResourceBundleCache: LoadingCache<Locale, Map<String, String>>
  private val escapedResourceBundleCache: LoadingCache<Locale, Map<String, String>>

  init {
    val unescapedResourceBundleCacheLoader: CacheLoader<Locale, Map<String, String>> = object : CacheLoader<Locale, Map<String, String>>() {
      override fun load(locale: Locale): Map<String, String> {
        return delegate.getUnescapedMessagesForLocale(locale)
      }
    }
    unescapedResourceBundleCache = CacheBuilder.newBuilder().maximumSize(maxCacheSize).build(unescapedResourceBundleCacheLoader)

    val escapedResourceBundleCacheLoader: CacheLoader<Locale, Map<String, String>> = object : CacheLoader<Locale, Map<String, String>>() {
      override fun load(locale: Locale): Map<String, String> {
        return delegate.getEscapedMessagesForLocale(locale)
      }
    }
    escapedResourceBundleCache = CacheBuilder.newBuilder().maximumSize(maxCacheSize).build(escapedResourceBundleCacheLoader)
  }

  override fun getUnescapedMessagesForLocale(locale: Locale): Map<String, String> {
    return unescapedResourceBundleCache.get(locale)
  }

  override fun getEscapedMessagesForLocale(locale: Locale): Map<String, String> {
    return escapedResourceBundleCache.get(locale)
  }
}

class ResourceBundleBackedLocalizationUtil(private val resourceBundleName: String) : LocalizationUtil {
  override fun getUnescapedMessagesForLocale(locale: Locale): Map<String, String> {
    return getMessagesForLocale(locale, false)
  }

  override fun getEscapedMessagesForLocale(locale: Locale) : Map<String, String> {
    return getMessagesForLocale(locale, true)
  }

  private fun getMessagesForLocale(locale: Locale, escape: Boolean) : Map<String, String> {
    val messages = ResourceBundle.getBundle(resourceBundleName, locale)
    return messages.keys.toList().associateWith { messageKey ->
      if(escape) {
        messages.getString(messageKey).replace("\"", "\\\"")
      }else {
        messages.getString(messageKey)
      }
    }
  }
}
