package io.amplica.custodial_wallet.internationalization

import java.util.Locale

interface MessageFactory {
  fun createMessage(templateName: String, context: Map<String, Any>, provider: String?, locale: Locale?): String
}