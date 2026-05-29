export interface ClaimRewardsResponse {
  type: FrequencyResponseType.ClaimRewards
  amount: bigint
}

export interface TransferResponse {
  type: FrequencyResponseType.Transfer
  amount: bigint
}

export type FrequencyResponse = ClaimRewardsResponse | TransferResponse

export enum FrequencyResponseType {
  ClaimRewards = "ClaimRewards",
  Transfer = "Transfer",
}
