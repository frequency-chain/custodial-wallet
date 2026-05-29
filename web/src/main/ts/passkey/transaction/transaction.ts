import { IFRAME_ERROR_HANDLER, runCatchingAsync } from "@/errors"
import EthereumPasskeyWalletClient from "@/passkey/clients/passkey/EthereumPasskeyWalletClient"
import PasskeyWalletClient from "@/passkey/clients/passkey/PasskeyWalletClient"
import PolkadotPasskeyWalletClient from "@/passkey/clients/passkey/PolkadotPasskeyWalletClient"
import { ENABLED_CHAIN_TYPE } from "@/passkey/constants"
import { ChainType } from "@/passkey/helpers/enums"
import { createAllowCredentialsList } from "@/passkey/transaction/authenticate"
import { fundUserAccount } from "@/passkey/transaction/testHandlers"
import {
  authenticatePasskey,
  getUnifiedAccountPublicKey,
  getUserCredentials,
  transferFundsToAnotherUser,
} from "./handlers"

export const getPasskeyWalletClientProvider = (frequencyAddress: string) => {
  let client: PasskeyWalletClient | null = null

  return async (): Promise<PasskeyWalletClient> => {
    if (!client) {
      switch (ENABLED_CHAIN_TYPE) {
        case ChainType.POLKADOT:
          client = new PolkadotPasskeyWalletClient(frequencyAddress)
          break
        case ChainType.ETHEREUM:
          client = new EthereumPasskeyWalletClient(frequencyAddress)
          break
      }
      await client.init()
    }

    return client
  }
}

export function startTransactionProcess(frequencyAddress: string) {
  const host = `${$(location).attr("protocol")}//${$(location).attr("host")}`

  const fundBtn: HTMLButtonElement = <HTMLButtonElement>document.getElementById("fundBtn")
  fundBtn.addEventListener("click", () =>
    runCatchingAsync(async () => {
      const fundAmount: number = parseFloat((<HTMLInputElement>document.getElementById("fundAmount")).value)
        ? parseFloat((<HTMLInputElement>document.getElementById("fundAmount")).value)
        : parseFloat((<HTMLInputElement>document.getElementById("fundAmount")).placeholder)

      await fundUserAccount(fundAmount, frequencyAddress, host)
    }, IFRAME_ERROR_HANDLER),
  )
  fundBtn.disabled = false

  const transferBtn = <HTMLButtonElement>document.getElementById("transferBtn")
  transferBtn.addEventListener("click", () =>
    runCatchingAsync(async () => {
      const transferAmount: number = parseFloat((<HTMLInputElement>document.getElementById("transferAmount")).value)
        ? parseFloat((<HTMLInputElement>document.getElementById("transferAmount")).value)
        : parseFloat((<HTMLInputElement>document.getElementById("transferAmount")).placeholder)
      const receiverAccountPublicKey: string = (<HTMLInputElement>document.getElementById("receiverAddress")).value

      await transferFundsToAnotherUser(transferAmount, receiverAccountPublicKey, frequencyAddress, host)
    }, IFRAME_ERROR_HANDLER),
  )
  transferBtn.disabled = false
}

export const boostProvider = async (
  passkeyWalletClientProvider: () => Promise<PasskeyWalletClient>,
  providerMsaId: bigint,
  amount: bigint,
) => {
  const host = `${$(location).attr("protocol")}//${$(location).attr("host")}`
  const userCredentials = await getUserCredentials(host)
  const unifiedAccountPublicKey = getUnifiedAccountPublicKey(userCredentials.credentials)

  const allowCredentialsList = createAllowCredentialsList(userCredentials)

  const client = await passkeyWalletClientProvider()

  const challenge = await client.createProviderBoostChallenge(unifiedAccountPublicKey, providerMsaId, amount)
  const passkeyData = await authenticatePasskey(host, challenge, allowCredentialsList, userCredentials.credentials)

  await client.providerBoost(passkeyData, unifiedAccountPublicKey, providerMsaId, amount)
}

export const unstakeFromProvider = async (
  passkeyWalletClientProvider: () => Promise<PasskeyWalletClient>,
  providerMsaId: bigint,
  amount: bigint,
) => {
  const host = `${$(location).attr("protocol")}//${$(location).attr("host")}`
  const userCredentials = await getUserCredentials(host)
  const unifiedAccountPublicKey = getUnifiedAccountPublicKey(userCredentials.credentials)

  const allowCredentialsList = createAllowCredentialsList(userCredentials)

  const client = await passkeyWalletClientProvider()

  const challenge = await client.createUnstakeChallenge(unifiedAccountPublicKey, providerMsaId, amount)
  const passkeyData = await authenticatePasskey(host, challenge, allowCredentialsList, userCredentials.credentials)

  await client.unstake(passkeyData, unifiedAccountPublicKey, providerMsaId, amount)
}

export const claimRewards = async (passkeyWalletClientProvider: () => Promise<PasskeyWalletClient>) => {
  const host = `${$(location).attr("protocol")}//${$(location).attr("host")}`
  const userCredentials = await getUserCredentials(host)
  const unifiedAccountPublicKey = getUnifiedAccountPublicKey(userCredentials.credentials)

  const allowCredentialsList = createAllowCredentialsList(userCredentials)

  const client = await passkeyWalletClientProvider()

  const challenge = await client.createClaimRewardsChallenge(unifiedAccountPublicKey)
  const passkeyData = await authenticatePasskey(host, challenge, allowCredentialsList, userCredentials.credentials)

  const event = await client.claimRewards(passkeyData, unifiedAccountPublicKey)
  console.log({ rewardAmount: event.amount })
}

export const checkBoostStatus = async (passkeyWalletClientProvider: () => Promise<PasskeyWalletClient>) => {
  const host = `${$(location).attr("protocol")}//${$(location).attr("host")}`
  const userCredentials = await getUserCredentials(host)

  const unifiedAccountPublicKey = getUnifiedAccountPublicKey(userCredentials.credentials)

  const client = await passkeyWalletClientProvider()

  const balance = await client.getAccountWalletBalance(unifiedAccountPublicKey)
  const rewards = await client.listUnclaimedRewards(unifiedAccountPublicKey)

  console.log({
    unifiedAccountPublicKeyHex: unifiedAccountPublicKey.encodedValue,
    balance,
    rewards,
  })
}
