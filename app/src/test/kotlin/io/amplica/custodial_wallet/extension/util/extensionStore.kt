package io.amplica.custodial_wallet.extension.util

import org.junit.jupiter.api.extension.ExtensionContext


fun getExtensionStore(context: ExtensionContext, cls: Class<*>): ExtensionContext.Store {
  return context.getStore(ExtensionContext.Namespace.create(cls))
}

class ExtensionStoreKeys {
  companion object {
    const val CONTAINER = "container"
  }
}
