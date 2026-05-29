import { options } from "@frequency-chain/api-augment"
import { ApiPromise, Keyring, WsProvider } from "@polkadot/api"
import { KeyringPair } from "@polkadot/keyring/types"

export async function connect(providerUrl: string): Promise<ApiPromise> {
  const provider = new WsProvider(providerUrl)
  const apiObservable = new ApiPromise({ provider, ...options })
  return apiObservable.isReady
}

export function createKeys(uri: string): KeyringPair {
  const keyring = new Keyring({ type: "sr25519" })
  const keys = keyring.addFromUri(uri)
  return keys
}
