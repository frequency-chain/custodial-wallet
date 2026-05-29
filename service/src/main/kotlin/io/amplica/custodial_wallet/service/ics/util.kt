package io.amplica.custodial_wallet.service.ics

import java.util.random.RandomGenerator

fun generateRandomByteArrayOfSize(randomGenerator: RandomGenerator, size: Int): ByteArray {
  val buffer = ByteArray(size)
  randomGenerator.nextBytes(buffer)
  return buffer
}
