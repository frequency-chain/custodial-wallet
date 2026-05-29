package io.amplica.custodial_wallet.client.redis.dto


data class SessionInfo (
  var tosAgreement: Boolean,
  var callbackUrl: String?,
  val resendTimeInMillis: Long,
  val resendCount: Int,
  var incorrectTokenRetries: Int
){
  companion object{
    fun updateIncorrectTokenRetries(sessionInfo: SessionInfo, updatedIncorrectTokenRetries: Int): SessionInfo {
      return sessionInfo.copy(incorrectTokenRetries = updatedIncorrectTokenRetries)
    }
  }
}