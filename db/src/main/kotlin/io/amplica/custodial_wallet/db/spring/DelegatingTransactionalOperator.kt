package io.amplica.custodial_wallet.db.spring

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.reactive.awaitLast
import kotlinx.coroutines.reactor.mono
import org.springframework.transaction.ReactiveTransaction
import org.springframework.transaction.reactive.TransactionCallback
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Flux
import java.util.*

interface ReadOnlyTransactionalOperator {
  suspend fun <T> executeReadOnly(f: suspend (ReactiveTransaction) -> T): T
}

interface ReadWriteTransactionalOperator {
  suspend fun <T> executeReadWrite(f: suspend (ReactiveTransaction) -> T): T
}

class DelegatingTransactionalOperator(
  private val readWriteTransactionalOperator: TransactionalOperator,
  private val readOnlyTransactionalOperator: TransactionalOperator
) : ReadOnlyTransactionalOperator, ReadWriteTransactionalOperator {
  private suspend fun <T> execute(
    connectionMode: ConnectionMode,
    withTransactionFn: suspend (ReactiveTransaction) -> T
  ): T {
    val executeFn: (TransactionCallback<T>) -> Flux<T> = when (connectionMode) {
      ConnectionMode.READ_WRITE -> {
        readWriteTransactionalOperator::execute
      }

      ConnectionMode.READ_ONLY -> {
        readOnlyTransactionalOperator::execute
      }
    }

    val context = currentCoroutineContext().minusKey(Job.Key)
    return executeFn { reactiveTransaction -> mono(context) { withTransactionFn(reactiveTransaction) } }.map { value ->
      Optional.ofNullable(
        value
      )
    }
      .defaultIfEmpty(Optional.empty())
      .contextWrite {
        if (!it.hasKey(ConnectionMode.CONTEXT_KEY_NAME)) {
          it.put(ConnectionMode.CONTEXT_KEY_NAME, connectionMode)
        } else {
          //skip because the beginning of the transaction had more context for the proper ConnectionMode
          it
        }
      }.awaitLast().orElse(null)
  }

  override suspend fun <T> executeReadOnly(f: suspend (ReactiveTransaction) -> T): T {
    return execute(ConnectionMode.READ_ONLY, f)
  }

  override suspend fun <T> executeReadWrite(f: suspend (ReactiveTransaction) -> T): T {
    return execute(ConnectionMode.READ_WRITE, f)
  }
}
