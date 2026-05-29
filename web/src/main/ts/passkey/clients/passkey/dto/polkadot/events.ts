// TODO: Should this all just be called Frequency ?
// Or maybe 'FrequencyPolkadot_` ?

export enum PolkadotSection {
  Capacity = "capacity",
  Balances = "balances",
  Msa = "msa",
}

export enum PolkadotMethod {
  MsaCreated = "MsaCreated",
  ProviderCreated = "ProviderCreated",
  ProviderBoosted = "ProviderBoosted",
  ProviderBoostRewardClaimed = "ProviderBoostRewardClaimed",
  Transfer = "Transfer",
  UnStaked = "UnStaked",
}

export interface PolkadotEventIdentifier {
  section: PolkadotSection
  method: PolkadotMethod
}

export const PolkadotEventIdentifiers = {
  Balances: {
    Transfer: {
      section: PolkadotSection.Balances,
      method: PolkadotMethod.Transfer,
    },
  },
  Capacity: {
    ProviderBoosted: {
      section: PolkadotSection.Capacity,
      method: PolkadotMethod.ProviderBoosted,
    },
    ProviderBoostRewardClaimed: {
      section: PolkadotSection.Capacity,
      method: PolkadotMethod.ProviderBoostRewardClaimed,
    },
    UnStaked: {
      section: PolkadotSection.Capacity,
      method: PolkadotMethod.UnStaked,
    },
  },
  Msa: {
    MsaCreated: {
      section: PolkadotSection.Msa,
      method: PolkadotMethod.MsaCreated,
    },
    ProviderCreated: {
      section: PolkadotSection.Msa,
      method: PolkadotMethod.ProviderCreated,
    },
  },
}

export type PolkadotEvent =
  | BalancesTransferEvent
  | CapacityProviderBoostedEvent
  | CapacityProviderBoostRewardClaimedEvent
  | CapacityUnstakedEvent
  | MsaMsaCreatedEvent
  | MsaProviderCreatedEvent

export interface BalancesTransferEvent {
  section: PolkadotSection.Balances
  method: PolkadotMethod.Transfer
  to: string
  from: string
  amount: bigint
}

export interface CapacityProviderBoostedEvent {
  section: PolkadotSection.Capacity
  method: PolkadotMethod.ProviderBoosted
  accountId: string
  providerMsaId: bigint
  amount: bigint
  capacity: bigint
}

export interface CapacityProviderBoostRewardClaimedEvent {
  section: PolkadotSection.Capacity
  method: PolkadotMethod.ProviderBoostRewardClaimed
  accountId: string
  rewardAmount: bigint
}

export interface CapacityUnstakedEvent {
  section: PolkadotSection.Capacity
  method: PolkadotMethod.UnStaked
  accountId: string
  providerMsaId: bigint
  amount: bigint
  capacity: bigint
}

export interface MsaMsaCreatedEvent {
  section: PolkadotSection.Msa
  method: PolkadotMethod.MsaCreated
  msaId: bigint
  accountId: string
}

export interface MsaProviderCreatedEvent {
  section: PolkadotSection.Msa
  method: PolkadotMethod.ProviderCreated
  msaId: bigint
}
