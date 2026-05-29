import { TokenBalance } from "@/passkey/clients/passkey/dto/tokens"

export interface AccountBalance {
  balance: TokenBalance
}

export interface PasskeyBalance {
  balance: TokenBalance
}
