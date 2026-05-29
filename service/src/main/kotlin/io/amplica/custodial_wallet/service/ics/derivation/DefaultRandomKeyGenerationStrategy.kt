package io.amplica.custodial_wallet.service.ics.derivation

import io.amplica.custodial_wallet.service.ics.generateRandomByteArrayOfSize
import java.util.random.RandomGenerator

class DefaultRandomKeyGenerationStrategy(
  private val randomGenerator: RandomGenerator,
  private val size: Int,
) : RandomKeyGenerationStrategy {

  override fun generateRandomKey(): ByteArray {
    return generateRandomByteArrayOfSize(randomGenerator, size)
  }

}