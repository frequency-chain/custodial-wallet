package io.amplica.custodial_wallet.util

import com.microsoft.playwright.ConsoleMessage
import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.amplica.custodial_wallet.extension.BrowserEngine
import org.slf4j.Logger
import java.net.URI
import java.util.*
import kotlin.jvm.optionals.getOrDefault


fun attachLoggerToPage(logger: Logger, page: Page) {
  page.onPageError { message ->
    logger.error("[Browser] $message")
  }

  page.onConsoleMessage { consoleMessage ->
    val message = "[Browser] (at ${consoleMessage.location()}) ${consoleMessage.text()}"
    when (consoleMessage.type()) {
      "error" -> logger.error(message)
      "warning" -> logger.warn(message)
      "info", "log" -> logger.info(message)
      else -> logger.debug(message)
    }
  }
}

val webkitErrorLocationsToIgnore = setOf(
  "localhost" to "/siwa/payloads",
)

fun collectJavaScriptErrors(engine: BrowserEngine, page: Page): MutableList<String> {
  val errorMessages = mutableListOf<String>()

  page.onPageError {
    errorMessages.addLast(it)
  }
  page.onConsoleMessage { consoleMessage ->
    val locationURI = try{
      Optional.of(URI(consoleMessage.location()))
    } catch(e: Exception){
      Optional.empty<URI>()
    }
    val ignoreError = engine.compareTo(BrowserEngine.Webkit) == 0 && locationURI.map { uri ->
      webkitErrorLocationsToIgnore.contains(uri.host to uri.path) }.getOrDefault(true)
    if (consoleMessage.type() == "error" && !ignoreError) {
      errorMessages.addLast(consoleMessage.text())
    }
  }

  return errorMessages
}

fun waitForConsoleMessageStartingWith(page: Page, prefix: String, runnable: Runnable): ConsoleMessage {
  return page.waitForConsoleMessage(
    Page.WaitForConsoleMessageOptions().setPredicate { it.text().startsWith(prefix) },
    runnable
  )
}

fun assertOnPageWithElements(page: Page, title: String, vararg elementTestIds: String) {
  assertThat(page).hasTitle(title)
  assertElementsVisible(page, *elementTestIds)
}

fun assertElementsVisible(page: Page, vararg elementTestIds: String) {
  elementTestIds.forEach { elementTestId ->
    assertThat(page.getByTestId(elementTestId)).isVisible()
  }
}

fun setUpPage(url: String, page: Page, body: String) {
  page.navigate(url)
  page.setContent(body)
}
