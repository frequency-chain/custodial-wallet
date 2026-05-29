package io.amplica.custodial_wallet.template

import com.github.mustachejava.MustacheFactory
import java.io.StringWriter

class MustacheTemplateRenderer(
  private val mustacheFactory: MustacheFactory,
  private val templatePath: String,
) : TemplateRenderer {

  override fun render(context: Map<String, Any>): String {
    val template = mustacheFactory.compile(templatePath)
    val writer = StringWriter()
    template.execute(writer, context).flush()
    return writer.toString()
  }

}