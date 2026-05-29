import { Encoding } from "@/passkey/helpers/enums"
import { EncodedBytes, PublicKey, Signature } from "@/passkey/helpers/interfaces"
import { convertBase64URLStringToHex } from "@/passkey/helpers/util"
import { hexToU8a } from "@polkadot/util"

export const convertEncodedBytesValueToU8a = (encodedBytes: EncodedBytes): Uint8Array => {
  let encodedBytesValue = encodedBytes.encodedValue
  switch (encodedBytes.encoding) {
    case Encoding.BASE64URL:
      encodedBytesValue = convertBase64URLStringToHex(encodedBytes.encodedValue)
      break
    case Encoding.BASE16:
      //Already encoded properly
      break
    default:
      throw new Error("Given encoding not supported at this time")
  }
  return hexToU8a(encodedBytesValue)
}

export const convertSignatureEncodedValueToU8a = (signature: Signature): Uint8Array => {
  let signatureValue: string = signature.encodedValue
  switch (signature.encoding) {
    case Encoding.BASE64URL:
      signatureValue = convertBase64URLStringToHex(signature.encodedValue)
      break
    case Encoding.BASE16:
      //Already encoded properly
      break
    default:
      throw new Error("Signature encoding not supported at this time")
  }
  return hexToU8a(signatureValue)
}

export const convertPublicKeyEncodedValueToHex = (publicKey: PublicKey): string => {
  let publicKeyValue = publicKey.encodedValue
  switch (publicKey.encoding) {
    case Encoding.BASE64URL:
      publicKeyValue = convertBase64URLStringToHex(publicKey.encodedValue)
      break
    case Encoding.BASE16:
      //Already properly encoded
      break
    default:
      throw new Error("Signature encoding not supported at this time")
  }

  return publicKeyValue
}

export const convertPublicKeyEncodedValueToU8a = (publicKey: PublicKey): Uint8Array => {
  return hexToU8a(convertPublicKeyEncodedValueToHex(publicKey))
}
