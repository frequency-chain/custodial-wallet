export interface FrequencyBlock {
  number: number
  //Should extrinsics list be added or is that too substrate specific
}

export interface FrequencySignedBlock {
  block: FrequencyBlock
  hash: FrequencyBlockHash
}

export interface FrequencyBlockHash {
  hash: string | Uint8Array
  encoding: FrequencyBlockHashEncoding
}

export enum FrequencyBlockHashEncoding {
  HEX = "hex",
  UINT8ARRAY = "UINT8ARRAY",
}
