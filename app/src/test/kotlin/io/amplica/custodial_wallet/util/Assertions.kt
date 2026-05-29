package io.amplica.custodial_wallet.util

import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import io.netty.handler.codec.http.cookie.DefaultCookie
import org.junit.jupiter.api.Assertions

fun assertLoginResponseMatchesRequest(userLoginRequest: LoginRequest, userLoginResponse: LoginResponse) {
  Assertions.assertEquals(userLoginRequest.externalUserId, userLoginResponse.externalUserId)
  Assertions.assertEquals(userLoginRequest.publicKey.type, userLoginResponse.publicKey.type)
  Assertions.assertEquals(userLoginRequest.signature.algo, userLoginResponse.loginPayloadSignature.algo)
}

fun assertSessionIdCookie(sessionIdCookie: DefaultCookie?) {
  Assertions.assertNotNull(sessionIdCookie)
  if (sessionIdCookie != null) {
    Assertions.assertEquals(false, sessionIdCookie.isSecure)
    Assertions.assertEquals(false, sessionIdCookie.isHttpOnly)
  }
}

fun assertSignupResponseMatchesRequest(userSignupRequest: SignUpRequest, signUpResponse: SignUpResponse) {
  Assertions.assertEquals(userSignupRequest.externalUserId, signUpResponse.externalUserId)
  Assertions.assertEquals(userSignupRequest.publicKey.type, signUpResponse.publicKey.type)
  Assertions.assertEquals(userSignupRequest.signature.algo, signUpResponse.addProviderPayloadSignature.algo)
  Assertions.assertArrayEquals(
    userSignupRequest.userIdentifiers.toTypedArray(),
    signUpResponse.userIdentifiers.toTypedArray()
  )
}

fun assertGraphKeyExists(userSignupResponse: SignUpResponse) {
  Assertions.assertNotNull(userSignupResponse.graphKey)
  Assertions.assertNotNull(userSignupResponse.graphKey!!.payload.keyPair)
  Assertions.assertEquals(userSignupResponse.graphKey!!.payload.keyPair!!.type, KeyPairType.X25519)
}

