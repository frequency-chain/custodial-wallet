package io.amplica.custodial_wallet.client.redis.dto

import com.fasterxml.jackson.annotation.*
import io.amplica.custodial_wallet.util.key_creation.Encoding
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import io.amplica.custodial_wallet.util.key_creation.PublicKeyFormat


data class FrequencyEndpoint(val pallet: Pallet, val extrinsic: Extrinsic) {

  class Msa {
    companion object {
      val createSponsoredAccountWithDelegation =
        FrequencyEndpoint(Pallet.Msa, Extrinsic.CreateSponsoredAccountWithDelegation)
      val grantDelegation = FrequencyEndpoint(Pallet.Msa, Extrinsic.GrantDelegation)
    }
  }

  class Handles {
    companion object {
      val claimHandle = FrequencyEndpoint(Pallet.Handles, Extrinsic.ClaimHandle)
    }
  }

  class StatefulStorage {
    companion object {
      val applyItemActionsWithSignatureV2 =
        FrequencyEndpoint(Pallet.StatefulStorage, Extrinsic.ApplyItemActionsWithSignatureV2)
    }
  }

}

enum class Extrinsic(@JsonValue val method: String) {
  ClaimHandle("claimHandle"),
  CreateSponsoredAccountWithDelegation("createSponsoredAccountWithDelegation"),
  GrantDelegation("grantDelegation"),
  ApplyItemActionsWithSignatureV2("applyItemActionsWithSignatureV2"),
  UpsertPageWithSignatureV2("upsertPageWithSignatureV2"),
  DeletePageWithSignatureV2( "deletePageWithSignatureV2"),
}

enum class Pallet(@JsonValue val identifier: String) {
  Handles("handles"),
  Msa("msa"),
  StatefulStorage("statefulStorage"),
}

@Deprecated("Superseded by CredentialSubject.KeyPair")
data class FrequencyKeyPairDto(
  val encodedPublicKeyValue: String,
  val encodedPrivateKeyValue: String,
  val encoding: Encoding,
  val format: PublicKeyFormat,
  val type: KeyPairType,
  val keyType: FrequencyKeyType,
)

enum class FrequencyKeyType(@JsonValue val value: String) {
  PublicKeyKeyAgreement("dsnp.public-key-key-agreement"),
}
