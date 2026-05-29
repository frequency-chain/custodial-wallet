import { AccountType } from "@/passkey/helpers/enums"
import { getUnifiedAccountAddress } from "@/passkey/helpers/keys"

export const convertToUnifiedAccountAddress = (publicKey: Uint8Array, accountType: AccountType): Uint8Array => {
  switch (accountType) {
    case AccountType.SR25519: {
      return publicKey
    }
    case AccountType.ETHEREUM:
    case AccountType.ETHEREUM_EIP_712: {
      return getUnifiedAccountAddress(publicKey)
    }
    default:
      throw new Error(`Unhandled case: ${accountType}`)
  }
}
