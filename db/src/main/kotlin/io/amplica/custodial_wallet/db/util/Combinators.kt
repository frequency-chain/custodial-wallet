package io.amplica.custodial_wallet.db.util


/**
 * Calls a paginated request as many times as needed until all results have been fetched
 */
tailrec suspend fun <T> collectPaginatedResults(
  paginatedRequest: suspend (offset: Int, limit: Int) -> List<T>,
  pageSize: Int,
  offset: Int = 0,
  previousResults: List<T> = emptyList()
): List<T> {
  val thisPage = paginatedRequest(offset, pageSize)

  return if (thisPage.size < pageSize) {
    previousResults.plus(thisPage)
  } else {
    val updatedResults = previousResults.plus(thisPage)
    collectPaginatedResults(paginatedRequest, pageSize, offset + pageSize, updatedResults)
  }
}
