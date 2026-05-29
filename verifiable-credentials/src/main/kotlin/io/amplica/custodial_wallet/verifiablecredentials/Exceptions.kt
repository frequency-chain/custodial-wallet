package io.amplica.custodial_wallet.verifiablecredentials

class ProofGenerationException(
  message: String?,
  cause: Throwable?,
  enableSuppression: Boolean,
  writableStackTrace: Boolean
) : RuntimeException(message, cause, enableSuppression, writableStackTrace) {
  constructor(message: String?) : this(message, null)
  constructor(message: String?, cause: Throwable?) : this(message, cause, false, true)
  constructor(cause: Throwable?) : this(null, cause, false, true)
}
