package io.amplica.custodial_wallet.extension

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import io.amplica.custodial_wallet.extension.util.ClosableResource
import io.amplica.custodial_wallet.extension.util.getExtensionStore
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.*
import java.util.stream.Stream

enum class BrowserEngine { Chromium, /*Firefox,*/ Webkit }

data class ContextPagePair(
  val context: BrowserContext,
  val page: Page,
) : AutoCloseable {
  override fun close() {
    page.close()
    context.close()
  }
}

/**
 * An extension for creating browser tests that runs tests against all 3 browser engines provided by Playwright.
 *
 * When tests are run in interactive debugging mode (with envvar `PWDEBUG=1`) only a single browser is used.
 * Modify the `DEBUG_BROWSER` static constant to use a different browser (defaults to Chromium).
 */
class PlaywrightExtension() : BeforeAllCallback {
  companion object {
    // Check whether Playwright will run in debug mode (with debugger and browser open)
    private val IS_DEBUG_MODE = System.getenv("PWDEBUG") == "1"
    // Check whether an abbreviated subset of browsers should be used (for speeding up testing)
    private val BROWSER_SKIP_ENABLED = System.getenv("ENABLE_BROWSER_SKIP") == "1"

    // Defines the browsers that will be tested against
    private val BROWSERS = when {
      IS_DEBUG_MODE -> arrayOf(BrowserEngine.Chromium) // Change as needed for interactive debugging
      // NOTE(Julian, 2024-07-07): Chromium ran 15 seconds faster than other browsers
      BROWSER_SKIP_ENABLED -> arrayOf(BrowserEngine.Chromium)
      else -> BrowserEngine.entries.toTypedArray()
    }

    @JvmStatic
    fun browserSourceMethod(): Stream<BrowserEngine> {
      return Arrays.stream(BROWSERS)
    }

    @JvmStatic
    fun chromiumOnlyBrowserSourceMethod(): Stream<BrowserEngine> {
      return Arrays.stream(arrayOf(BrowserEngine.Chromium))
    }
  }

  private lateinit var pw: Playwright

  // The browsers live for the lifetime of the extension
  private var runningBrowsers: MutableMap<BrowserEngine, Browser> = mutableMapOf()

  override fun beforeAll(context: ExtensionContext) {
    getExtensionStore(context, javaClass).getOrComputeIfAbsent("playwright") {
      pw = Playwright.create()

      // We always need to start Chromium because the Passkey Wallet tests only execute there
      val browsersToStart = BROWSERS.toSet().plus(BrowserEngine.Chromium)

      browsersToStart.forEach {
        runningBrowsers[it] = when (it) {
          BrowserEngine.Chromium -> pw.chromium()?.launch()
          //BrowserEngine.Firefox -> pw.firefox()?.launch()
          BrowserEngine.Webkit -> pw.webkit()?.launch()
        } ?: throw Error("Playwright failed to instantiate browser: $it")
      }

      // Stores in extension storage which tells JUNIT to automatically close playwright when all tests finish (and the
      // extension is shut down).
      ClosableResource(pw)
    }
  }

  /**
   * Helper to create a new context and page. Should be used with a `use` block to handle closing the context.
   */
  fun createContext(inBrowser: BrowserEngine): ContextPagePair {
    val browser = runningBrowsers[inBrowser] ?: throw Error("Browser not running: $inBrowser")

    val context = browser.newContext()

    return ContextPagePair(context, context.newPage())
  }
}

@ParameterizedTest
// Note: Using a method as a source for parameterization allows using static values instead of literals, leading to
// more flexibility.
@MethodSource("io.amplica.custodial_wallet.extension.PlaywrightExtension#browserSourceMethod")
annotation class BrowserTest

@ParameterizedTest
@MethodSource("io.amplica.custodial_wallet.extension.PlaywrightExtension#chromiumOnlyBrowserSourceMethod")
annotation class ChromiumBrowserTest