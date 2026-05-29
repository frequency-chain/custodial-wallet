import { Encoding, EncodingFormat, PublicKeyType, SignatureAlgos } from "@/passkey/helpers/enums"

export interface PasskeyData {
  userHandle: string
  clientDataJSON: EncodedBytes
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  transports: any[]
  passkeyCompressedPublicKey: PublicKey
  credentialPublicKeySignature: Signature
  challenge: EncodedBytes
  credentialId: string
  accountPublicKey: PublicKey
  credentialSignatureOfAccountPublicKey?: Signature
  attestationObject: EncodedBytes
  clientExtensions: string
}

export interface Signature {
  algo: SignatureAlgos
  encoding: Encoding
  encodedValue: string
}

export interface EncodedBytes {
  encodedValue: string
  encoding: Encoding
}

export interface PublicKey {
  encodedValue: string
  encoding: Encoding
  format: EncodingFormat
  type: PublicKeyType
}

export interface AuthenticateCredentialData {
  credentialId: string
  clientDataJSON: EncodedBytes
  passkeyPayloadCallHex: EncodedBytes
  authenticatorData: EncodedBytes
  signature: Signature
}

export interface CredentialResponse {
  credentialId: string
  passkeyCompressedPublicKey: PublicKey
  accountPublicKey: PublicKey
  credentialPublicKeySignature: Signature
  credentialSignatureOfAccountPublicKey?: Signature
}

export interface CredentialsResponse {
  credentials: Array<CredentialResponse>
}

export interface AuthenticatedPasskeyWalletData {
  accountSignature: Signature
  passkeyPublicKey: PublicKey
  passkeySignature: Signature
  rawAuthenticatorData: EncodedBytes
  rawClientData: EncodedBytes
}

export enum Environment {
  MAIN = "main",
  TEST = "test",
  INTEGRATION = "integration",
  DEV = "dev",
}
