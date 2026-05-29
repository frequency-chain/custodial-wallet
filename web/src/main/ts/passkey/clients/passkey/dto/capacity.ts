export interface UnclaimedReward {
  era: bigint
  expiresAtBlock: bigint
  stakedAmount: bigint
  eligibleAmount: bigint
  earnedAmount: bigint
}
