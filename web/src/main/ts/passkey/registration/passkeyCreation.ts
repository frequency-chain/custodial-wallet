import { credentialPublicKeyCborToCompressedKey } from "@/passkey/helpers/keys"
import { u8aToHex } from "@polkadot/util"
import { base64URLStringToBuffer, startRegistration } from "@simplewebauthn/browser"
import { PublicKeyCredentialCreationOptionsJSON, RegistrationResponseJSON } from "@simplewebauthn/types"
import { decode } from "cbor-x"
import { ENABLED_ACCOUNT_TYPE } from "../constants"
import {
  AccountType,
  Encoding,
  EncodingFormat,
  PasskeyPublicKeyAlgos,
  PublicKeyType,
  SignatureAlgos,
} from "../helpers/enums"
import { PasskeyData, PublicKey, Signature } from "../helpers/interfaces"
import { convertHexToBase64URLString } from "../helpers/util"

let newPasskeyCredentials: RegistrationResponseJSON
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let attestationObject: any
let compressedPasskeyPublicKey: Uint8Array
let passkeyUsername: string
let accountPublicKeyHex: string
let accountPublicKeyBase64Url: string

export const createNewPasskey = async (
  origin: string,
  username: string,
  userId: string,
  generatedPublicKeyHex: string,
): Promise<PublicKey> => {
  accountPublicKeyHex = generatedPublicKeyHex
  passkeyUsername = username
  accountPublicKeyBase64Url = convertHexToBase64URLString(accountPublicKeyHex)
  const optionsJSON = createOptionsJson(origin, userId)

  newPasskeyCredentials = await registerPasskeyToAuthenticator(optionsJSON)

  attestationObject = decode(new Uint8Array(base64URLStringToBuffer(newPasskeyCredentials.response.attestationObject)))
  const authData = attestationObject.authData
  const rawPasskeyPublicKey = getRawPublicKey(authData)
  compressedPasskeyPublicKey = credentialPublicKeyCborToCompressedKey(rawPasskeyPublicKey)
  const compressedPasskeyPublicKeyHex = u8aToHex(compressedPasskeyPublicKey)
  return {
    encodedValue: compressedPasskeyPublicKeyHex,
    type: PublicKeyType.P256,
    encoding: Encoding.BASE16,
    format: EncodingFormat.COMPRESSED_HEX,
  }
}

export const createPasskeyData = async (passkeyPublicKeySignature: Signature): Promise<PasskeyData> => {
  let passkeySignatureHex = "0x"
  if (attestationObject !== undefined || attestationObject !== null) {
    const passkeySignature: Uint8Array = attestationObject.attStmt.sig
    passkeySignatureHex = u8aToHex(passkeySignature)
  }

  const pubKeyBase64Url: string = newPasskeyCredentials.response.publicKey
    ? newPasskeyCredentials.response.publicKey
    : "none"
  if (pubKeyBase64Url === "none") {
    throw Error("Failed to find public key in passkey credentials")
  }
  const passkeyPubKeyHex = u8aToHex(compressedPasskeyPublicKey)
  const credentialSignatureOfAccountPublicKey: Signature = {
    algo: SignatureAlgos.P256,
    encoding: Encoding.BASE16,
    encodedValue: passkeySignatureHex,
  }

  let generatedKeyPairType: PublicKeyType
  switch (ENABLED_ACCOUNT_TYPE) {
    case AccountType.SR25519: {
      generatedKeyPairType = PublicKeyType.SR25519
      break
    }
    case AccountType.ETHEREUM:
    case AccountType.ETHEREUM_EIP_712: {
      generatedKeyPairType = PublicKeyType.SECP256K1
      break
    }
    default:
      throw new Error(`Unhandled case: ${ENABLED_ACCOUNT_TYPE}`)
  }

  return {
    userHandle: passkeyUsername,
    clientDataJSON: {
      encodedValue: newPasskeyCredentials.response.clientDataJSON,
      encoding: Encoding.BASE64URL,
    },
    attestationObject: {
      encodedValue: newPasskeyCredentials.response.attestationObject,
      encoding: Encoding.BASE64URL,
    },
    clientExtensions: JSON.stringify(newPasskeyCredentials.clientExtensionResults),
    transports: [],
    challenge: {
      encodedValue: accountPublicKeyBase64Url,
      encoding: Encoding.BASE64URL,
    },
    credentialId: newPasskeyCredentials.id,
    passkeyCompressedPublicKey: {
      encodedValue: passkeyPubKeyHex,
      encoding: Encoding.BASE16,
      format: EncodingFormat.BARE,
      type: PublicKeyType.P256,
    },
    accountPublicKey: {
      encodedValue: accountPublicKeyHex,
      encoding: Encoding.BASE16,
      format: EncodingFormat.COMPRESSED_HEX,
      type: generatedKeyPairType,
    },
    credentialPublicKeySignature: passkeyPublicKeySignature,
    credentialSignatureOfAccountPublicKey,
  }
}

const createOptionsJson = (origin: string, userId: string): PublicKeyCredentialCreationOptionsJSON => {
  const hostname = new URL(origin).hostname
  return {
    rp: {
      id: hostname,
      name: "Passkey Registration",
    },
    user: {
      id: userId,
      name: passkeyUsername,
      displayName: passkeyUsername,
    },
    challenge: accountPublicKeyBase64Url,
    pubKeyCredParams: [
      {
        alg: PasskeyPublicKeyAlgos.ES256,
        type: "public-key",
      },
    ],
    timeout: 60000,
    excludeCredentials: [],
    authenticatorSelection: {
      residentKey: "preferred",
    },
    attestation: "direct",
    extensions: {},
  }
}

const registerPasskeyToAuthenticator = async (
  options: PublicKeyCredentialCreationOptionsJSON,
): Promise<RegistrationResponseJSON> => {
  return await startRegistration(options)
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const getRawPublicKey = (authData: any) => {
  // get the length of the credential ID
  const dataView = new DataView(new ArrayBuffer(2))
  const idLenBytes = authData.slice(53, 55)
  idLenBytes.forEach((value: number, index: number) => dataView.setUint8(index, value))
  const credentialIdLength = dataView.getUint16(0)

  // get the public key object
  const publicKeyBytes = authData.slice(55 + credentialIdLength)

  // the publicKeyBytes are encoded again as CBOR
  return decode(publicKeyBytes)
}
