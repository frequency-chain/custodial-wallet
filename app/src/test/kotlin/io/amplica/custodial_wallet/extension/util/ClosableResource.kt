package io.amplica.custodial_wallet.extension.util

import org.junit.jupiter.api.extension.ExtensionContext


/*
 * Wraps around a class that already satisfies `AutoClosable` to satisfy the JUnit `CloseableResource` interface.
 * Implementing this interface enables JUnit to call `close()` when the associated `ExtensionContext` is terminated.
 */
data class ClosableResource(val resource: AutoCloseable) : ExtensionContext.Store.CloseableResource {
  override fun close() {
    resource.close()
  }
}
