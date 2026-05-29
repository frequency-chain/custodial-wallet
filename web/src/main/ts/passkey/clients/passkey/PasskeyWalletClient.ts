import { AccountBalance } from "@/passkey/clients/passkey/dto/balances"
import {
  FrequencyBlockHash,
  FrequencyBlockHashEncoding,
  FrequencySignedBlock,
} from "@/passkey/clients/passkey/dto/block"
import { UnclaimedReward } from "@/passkey/clients/passkey/dto/capacity"
import { ClaimRewardsResponse, TransferResponse } from "@/passkey/clients/passkey/dto/response"
import { AuthenticatedPasskeyWalletData, EncodedBytes, PublicKey } from "@/passkey/helpers/interfaces"

export interface PasskeyWalletClient {
  init(): Promise<void>

  getCurrentBlock(hash?: FrequencyBlockHash, hashEncoding?: FrequencyBlockHashEncoding): Promise<FrequencySignedBlock>
  getNonce(accountAddress: string): Promise<number>

  getFinalizedHead(hashEncoding?: FrequencyBlockHashEncoding): Promise<FrequencyBlockHash>

  // TODO(#1254): Update the return value to support a more rich interface
  getAccountWalletBalance(accountPublicKey: PublicKey): Promise<AccountBalance>

  createTransferBalanceChallenge(
    senderAccountPublicKey: PublicKey,
    receiverAccountPublicKey: PublicKey,
    transferAmount: number,
  ): Promise<EncodedBytes>

  transferBalance(
    passkeyData: AuthenticatedPasskeyWalletData,
    senderAccountPublicKey: PublicKey,
    receiverAccountPublicKey: PublicKey,
    transferAmount: number,
  ): Promise<TransferResponse>

  createProviderBoostChallenge(
    userAccountPublicKey: PublicKey,
    providerMsaId: bigint,
    amount: bigint,
  ): Promise<EncodedBytes>

  // Stakes some amount of tokens to the network and generates a comparatively small
  // amount of Capacity for the target, and gives periodic rewards to origin.
  providerBoost(
    passkeyData: AuthenticatedPasskeyWalletData,
    userAccountPublicKey: PublicKey,
    providerMsaId: bigint,
    amount: bigint,
  ): Promise<void>

  createUnstakeChallenge(userAccountPublicKey: PublicKey, providerMsaId: bigint, amount: bigint): Promise<EncodedBytes>

  // Schedules an amount of the stake to be unlocked.
  unstake(
    passkeyData: AuthenticatedPasskeyWalletData,
    userAccountPublicKey: PublicKey,
    providerMsaId: bigint,
    amount: bigint,
  ): Promise<void>

  createClaimRewardsChallenge(userAccountPublicKey: PublicKey): Promise<EncodedBytes>

  // Claim all outstanding Provider Boost rewards, up to `ProviderBoostHistoryLimit`
  // Reward Eras in the past. Accounts should check for unclaimed rewards before calling
  // this extrinsic to avoid needless transaction fees.
  claimRewards(
    passkeyData: AuthenticatedPasskeyWalletData,
    userAccountPublicKey: PublicKey,
  ): Promise<ClaimRewardsResponse>

  // Get the list of unclaimed rewards information for each eligible Reward Era
  listUnclaimedRewards(userAccountPublicKey: PublicKey): Promise<UnclaimedReward[]>
}

export default PasskeyWalletClient
