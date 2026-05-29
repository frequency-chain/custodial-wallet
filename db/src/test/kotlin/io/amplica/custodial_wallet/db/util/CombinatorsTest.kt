package io.amplica.custodial_wallet.db.util

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class CombinatorsTest {

  companion object {
    @JvmStatic
    fun collectPaginatedResultsTestParameters(): Stream<Arguments> {
      return Stream.of(
        Arguments.of(listOf("hello", "world")),
        Arguments.of(listOf(1, 2, 3)),
        Arguments.of(emptyList<String>()),
      )
    }
  }

  @ParameterizedTest
  @MethodSource("collectPaginatedResultsTestParameters")
  fun <T> collectPaginatedResultsTest(mockData: List<T>) {
    /** Slap-dash implementation for paginating a list */
    val paginatedRequest: suspend (offset: Int, limit: Int) -> List<T> = { offset: Int, limit: Int ->
      mockData.chunked(limit).getOrElse(offset / limit) { emptyList() }
    }

    runBlocking {
      val results = collectPaginatedResults(paginatedRequest, 2)
      Assertions.assertEquals(mockData, results)
    }
  }

}