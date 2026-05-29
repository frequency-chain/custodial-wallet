package io.amplica.custodial_wallet.util

import kotlinx.coroutines.reactor.mono
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.transaction.ReactiveTransaction
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Mono

fun createTransactionalOperatorDouble(): TransactionalOperator {
  val reactiveTransactionManager: ReactiveTransactionManager = mock()
  val transactionalOperator = TransactionalOperator.create(reactiveTransactionManager)
  val reactiveTransaction: ReactiveTransaction = mock()
  whenever(reactiveTransactionManager.getReactiveTransaction(any())).doAnswer { mono { reactiveTransaction } }
  whenever(reactiveTransactionManager.commit(any())).doAnswer { Mono.empty() }
  whenever(reactiveTransactionManager.rollback(any())).doAnswer { Mono.empty() }

  return transactionalOperator
}