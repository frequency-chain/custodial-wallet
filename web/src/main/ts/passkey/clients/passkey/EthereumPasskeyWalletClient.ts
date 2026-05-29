/* eslint-disable */
// TODO: Enable eslint when implementing

import PasskeyWalletClient from "@/passkey/clients/passkey/PasskeyWalletClient"
import { AccountBalance } from "@/passkey/clients/passkey/dto/balances"
import { FrequencyBlockHash, FrequencySignedBlock } from "@/passkey/clients/passkey/dto/block"
import { UnclaimedReward } from "@/passkey/clients/passkey/dto/capacity"
import { ClaimRewardsResponse, TransferResponse } from "@/passkey/clients/passkey/dto/response"
import { AuthenticatedPasskeyWalletData, EncodedBytes, PublicKey } from "@/passkey/helpers/interfaces"

class EthereumPasskeyWalletClient implements PasskeyWalletClient {
  private readonly providerUri: string

  constructor(providerUri: string) {
    this.providerUri = providerUri
  }

  //TODO: Implement the real methods of all this

  init(): Promise<void> {
    return Promise.reject("Not implemented")
  }

  getAccountWalletBalance(accountPublicKey: PublicKey): Promise<AccountBalance> {
    return Promise.reject("Not implemented")
  }

  getCurrentBlock(): Promise<FrequencySignedBlock> {
    return Promise.reject("Not implemented")
  }

  getFinalizedHead(): Promise<FrequencyBlockHash> {
    return Promise.reject("Not implemented")
  }

  getNonce(accountAddress: string): Promise<number> {
    return Promise.reject("Not implemented")
  }

  createTransferBalanceChallenge(
    senderAccountPublicKeyHex: PublicKey,
    receiverAccountPublicKey: PublicKey,
    transferAmount: number,
  ): Promise<EncodedBytes> {
    return Promise.reject("Not implemented")
  }

  transferBalance(
    authenticatedPasskeyWalletData: AuthenticatedPasskeyWalletData,
    senderAccountPublicKey: PublicKey,
    receiverAccountPublicKey: PublicKey,
    transferAmount: number,
  ): Promise<TransferResponse> {
    return Promise.reject("Not implemented")
  }

  createProviderBoostChallenge(
    userAccountPublicKey: PublicKey,
    providerMsaId: bigint,
    amount: bigint,
  ): Promise<EncodedBytes> {
    return Promise.reject("Not implemented")
  }

  providerBoost(
    passkeyData: AuthenticatedPasskeyWalletData,
    userAccountPublicKey: PublicKey,
    providerMsaId: bigint,
    amount: bigint,
  ): Promise<void> {
    return Promise.reject("Not implemented")
  }

  createUnstakeChallenge(
    userAccountPublicKey: PublicKey,
    providerMsaId: bigint,
    amount: bigint,
  ): Promise<EncodedBytes> {
    return Promise.reject("Not implemented")
  }

  unstake(
    passkeyData: AuthenticatedPasskeyWalletData,
    userAccountPublicKey: PublicKey,
    providerMsaId: bigint,
    amount: bigint,
  ): Promise<void> {
    return Promise.reject("Not implemented")
  }

  createClaimRewardsChallenge(userAccountPublicKey: PublicKey): Promise<EncodedBytes> {
    return Promise.reject("Not implemented")
  }

  claimRewards(
    passkeyData: AuthenticatedPasskeyWalletData,
    userAccountPublicKey: PublicKey,
  ): Promise<ClaimRewardsResponse> {
    return Promise.reject("Not implemented")
  }

  listUnclaimedRewards(userAccountPublicKey: PublicKey): Promise<UnclaimedReward[]> {
    return Promise.reject("Not implemented")
  }
}

export default EthereumPasskeyWalletClient
