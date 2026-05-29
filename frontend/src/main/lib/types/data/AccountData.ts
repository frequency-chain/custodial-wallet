import { Dictionary } from "lodash"

export enum UserDetailType {
  EMAIL = "EMAIL",
  PHONE_NUMBER = "PHONE_NUMBER",
}

export interface UserDetailData {
  value: string
  type: UserDetailType
  priority: number
}

export interface ProviderData {
  msaId: number
  name: string
  permissions: string[] // I18n message keys
}

export interface AccountData {
  providers: ProviderData[]
  userDetails: Dictionary<UserDetailData[]>
  handle: string
  userPublicKeyHex: string
  userAccountId: number
  hasPassword: boolean
  hasPasskeyWallet: boolean
  msaId: number
  // TODO ...
}
