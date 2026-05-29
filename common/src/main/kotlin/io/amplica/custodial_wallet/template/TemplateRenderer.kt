package io.amplica.custodial_wallet.template

interface TemplateRenderer {
  fun render(context: Map<String, Any>): String
}
