import { ApiPromise } from "@polkadot/api"
import { KeyringPair } from "@polkadot/keyring/types"
import { AnyJson } from "@polkadot/types/types"
import { IU8a } from "@polkadot/types/types/interfaces"

export interface GetMsaResult {
  result: AnyJson
  blockHash?: IU8a
}

export const getMsa = async (polkadotApi: ApiPromise, keyringPair: KeyringPair): Promise<GetMsaResult> => {
  console.log(keyringPair.publicKey)
  const msaQueryResult = await polkadotApi.query.msa.publicKeyToMsaId(keyringPair.publicKey)

  const msaId = msaQueryResult.toJSON()
  const blockHash = msaQueryResult.createdAtHash
  return {
    result: msaId,
    blockHash,
  }
}
