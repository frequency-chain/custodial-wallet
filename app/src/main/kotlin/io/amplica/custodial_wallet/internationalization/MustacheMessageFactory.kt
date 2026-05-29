package io.amplica.custodial_wallet.internationalization

import com.github.mustachejava.Mustache
import java.io.StringWriter
import java.util.*

class MustacheMessageFactory (val templateResolver: MustacheTemplateResolver) : MessageFactory {
  override fun createMessage(templateName: String, context: Map<String, Any>, provider: String?, locale: Locale?): String {
    val stringOutput = StringWriter()
    val resolvedTemplate: Mustache = templateResolver.resolveTemplate(templateName, provider, locale)
    resolvedTemplate.execute(stringOutput, context).flush()
    return stringOutput.toString()
  }
}