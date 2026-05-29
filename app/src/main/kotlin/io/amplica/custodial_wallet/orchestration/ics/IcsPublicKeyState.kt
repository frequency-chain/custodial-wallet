package io.amplica.custodial_wallet.orchestration.ics

sealed interface IcsPublicKeyState {
  data class Registered(val keyId: Int) : IcsPublicKeyState
  data class Unregistered(val nextAvailableKeyId: Int) : IcsPublicKeyState
}