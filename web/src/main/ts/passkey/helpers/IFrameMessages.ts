import { RuntimeError } from "@/errors/RuntimeError"
import { PublicKey, Signature } from "@/passkey/helpers/interfaces"
import { HexString } from "@frequency-chain/ethereum-utils"

export enum IFrameMessageType {
  StartRegistration = "StartRegistrationMessage",
  FinishRegistration = "FinishRegistrationMessage",
  Error = "ErrorMessage",
  AccountPublicKey = "AccountPublicKey",
  SignPasskeyPublicKey = "SignPasskeyPublicKey",
  StartRecovery = "StartRecoveryMessage",
  FinishRecovery = "FinishRecoveryMessage",
}

export type iFrameMessage =
  | StartRegistrationMessage
  | FinishRegistrationMessage
  | ErrorMessage
  | AccountPublicKeyMessage
  | SignPasskeyPublicKeyMessage
  | StartRecoveryMessage
  | FinishRecoveryMessage

export interface StartRegistrationMessage {
  type: IFrameMessageType.StartRegistration
}

export interface FinishRegistrationMessage {
  type: IFrameMessageType.FinishRegistration
  data: Signature
}

export interface ErrorMessage {
  type: IFrameMessageType.Error
  error: RuntimeError
}

export interface AccountPublicKeyMessage {
  type: IFrameMessageType.AccountPublicKey
  data: string
}

export interface SignPasskeyPublicKeyMessage {
  type: IFrameMessageType.SignPasskeyPublicKey
  data: PublicKey
}

export interface StartRecoveryMessage {
  type: IFrameMessageType.StartRecovery
  data: HexString
}

export interface FinishRecoveryMessage {
  type: IFrameMessageType.FinishRecovery
  data: boolean
}
