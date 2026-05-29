import { ENABLED_ACCOUNT_TYPE } from "@/passkey/constants"
import { AccountType } from "@/passkey/helpers/enums"
import { hexToU8a } from "@polkadot/util"
import { ethereumEncode } from "@polkadot/util-crypto"

/**
 * Returns ethereum style public key with suffixed 0xee example: 0x19a701d23f0ee1748b5d5f883cb833943096c6c4eeeeeeeeeeeeeeeeeeeeeeee
 * @param publicKey
 */
export const getUnifiedAccountAddress = (publicKey: Uint8Array): Uint8Array => {
  switch (ENABLED_ACCOUNT_TYPE) {
    case AccountType.SR25519: {
      return publicKey
    }
    case AccountType.ETHEREUM:
    case AccountType.ETHEREUM_EIP_712: {
      const publicKeyBytes = hexToU8a(ethereumEncode(publicKey))
      const result = new Uint8Array(32).fill(0xee)
      result.set(publicKeyBytes, 0)
      return result
    }
    default:
      throw new Error(`Unhandled case: ${ENABLED_ACCOUNT_TYPE}`)
  }
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const credentialPublicKeyCborToCompressedKey = (decoded: any): Uint8Array => {
  //Public key x coordinate
  const x = decoded[-2]
  //Public key y coordinate
  const y = decoded[-3]
  const tag = (y[y.length - 1] & 1) === 1 ? 3 : 2
  const result = new Uint8Array(33)

  // Set the first byte to the tag
  result[0] = tag

  // Copy up to 32 bytes from the source array
  const bytesToCopy = Math.min(x.length, 32)
  result.set(x.slice(0, bytesToCopy), 1)
  return result
}
