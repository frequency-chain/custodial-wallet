import { ErrorType } from "@/errors"
import { FrequencyErrorId } from "@/errors/FrequencyError"
import {
  AUTHENTICATED_PASSKEY_DATA_1,
  PASSKEY_DATA_1,
  PASSKEY_DATA_2,
} from "@/passkey/clients/passkey/test/helpers/testData"
import { convertToUnifiedAccountAddress } from "@/passkey/clients/passkey/test/helpers/util"
import TestPolkadotPasskeyWalletClient from "@/passkey/clients/passkey/TestPolkadotPasskeyWalletClient"
import { AccountType, Encoding, EncodingFormat, PublicKeyType } from "@/passkey/helpers/enums"
import { AuthenticatedPasskeyWalletData, PublicKey } from "@/passkey/helpers/interfaces"
import { Keyring } from "@polkadot/api"
import { KeyringPair } from "@polkadot/keyring/types"
import { hexToU8a, u8aToHex } from "@polkadot/util"
import { GenericContainer, StartedTestContainer, Wait } from "testcontainers"
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from "vitest"
import { FrequencyResponseType } from "../dto/response"

export const CENTS = BigInt(1000000)
export const DOLLARS = BigInt(100) * CENTS

const setupUnifiedPublicKey = (publicKeyValue: string) => {
  const unifiedAccountAddressBytes = convertToUnifiedAccountAddress(
    hexToU8a(publicKeyValue),
    AccountType.ETHEREUM_EIP_712,
  )
  const unifiedPublicKey: PublicKey = {
    encodedValue: u8aToHex(unifiedAccountAddressBytes),
    encoding: Encoding.BASE16,
    format: EncodingFormat.COMPRESSED_HEX,
    type: PublicKeyType.SECP256K1,
  }

  return unifiedPublicKey
}

describe("PolkadotPasskeyWalletClient", () => {
  const aliceKeyring = new Keyring({ type: "sr25519" })

  let startedTestContainer: StartedTestContainer
  let wsProviderEndpoint: string
  let passkeyWalletClient: TestPolkadotPasskeyWalletClient
  let aliceKeyPair: KeyringPair

  beforeAll(async () => {
    startedTestContainer = await new GenericContainer("frequencychain/standalone-node:v1.17.0-rc5")
      .withExposedPorts(30333, 9993, 9944)
      .withWaitStrategy(Wait.forLogMessage(/.*Running JSON-RPC server.*/, 1))
      .withStartupTimeout(90000)
      .start()

    wsProviderEndpoint = `ws://${startedTestContainer.getHost()}:${startedTestContainer.getMappedPort(9944)}`

    passkeyWalletClient = new TestPolkadotPasskeyWalletClient(wsProviderEndpoint)
    await passkeyWalletClient.init()

    aliceKeyPair = aliceKeyring.addFromUri("//Alice")

    await passkeyWalletClient.createMsa()
    await passkeyWalletClient.createProvider()
  }, 30 * 1000) // 30s timeout

  afterAll(async () => {
    await passkeyWalletClient.destroy()
    await startedTestContainer.stop()
  })

  describe("Transactions", () => {
    it("Transferring funds from a passkey lacking enough funds throws Error", async () => {
      const senderPublicKey = setupUnifiedPublicKey(PASSKEY_DATA_1.accountPublicKey.encodedValue)
      const receiverPublicKey = setupUnifiedPublicKey(PASSKEY_DATA_2.accountPublicKey.encodedValue)

      const currentBalance = await passkeyWalletClient.getAccountWalletBalance(senderPublicKey)
      const largerThanAvailableTransferAmount = currentBalance.balance.value + 10

      const authenticatedPasskeyWalletData: AuthenticatedPasskeyWalletData = {
        accountSignature: PASSKEY_DATA_1.credentialPublicKeySignature,
        passkeyPublicKey: PASSKEY_DATA_1.passkeyCompressedPublicKey,
        passkeySignature: AUTHENTICATED_PASSKEY_DATA_1.signature,
        rawAuthenticatorData: AUTHENTICATED_PASSKEY_DATA_1.authenticatorData,
        rawClientData: AUTHENTICATED_PASSKEY_DATA_1.clientDataJSON,
      }

      await expect(
        passkeyWalletClient.transferBalance(
          authenticatedPasskeyWalletData,
          senderPublicKey,
          receiverPublicKey,
          largerThanAvailableTransferAmount,
        ),
      ).rejects.toMatchObject({
        type: ErrorType.Frequency,
        description: "1010: Invalid Transaction: Inability to pay some fees , e.g. account balance too low",
        frequencyErrorId: FrequencyErrorId.Unknown,
      })
    })

    it("Fund a passkey through Alice, then check that nonce incremented and balance updated", async () => {
      const testUnifiedAccountAddressBytes = convertToUnifiedAccountAddress(
        hexToU8a(PASSKEY_DATA_1.accountPublicKey.encodedValue),
        AccountType.ETHEREUM_EIP_712,
      )
      const testPublicKey: PublicKey = {
        encodedValue: u8aToHex(testUnifiedAccountAddressBytes),
        encoding: Encoding.BASE16,
        format: EncodingFormat.COMPRESSED_HEX,
        type: PublicKeyType.SECP256K1,
      }

      const previousNonce = await passkeyWalletClient.getNonce(aliceKeyPair.address)
      const previousBalance = await passkeyWalletClient.getAccountWalletBalance(testPublicKey)
      await passkeyWalletClient.transferAccountBalanceFromAlice(testPublicKey, 1000000)

      const newNonce = await passkeyWalletClient.getNonce(aliceKeyPair.address)
      const newBalance = await passkeyWalletClient.getAccountWalletBalance(testPublicKey)

      expect(newNonce).toStrictEqual(previousNonce + 1)
      expect(newBalance.balance.value).not.toBe(previousBalance.balance.value)
      expect(newBalance.balance.value).toStrictEqual(1000000)
    })

    it("Fund a passkey through Alice, and check that blocks incremented", async () => {
      const testPublicKey: PublicKey = setupUnifiedPublicKey(PASSKEY_DATA_1.accountPublicKey.encodedValue)

      const previousBlock = await passkeyWalletClient.getCurrentBlock()
      await passkeyWalletClient.transferAccountBalanceFromAlice(testPublicKey, 1000000)
      const currentBlock = await passkeyWalletClient.getCurrentBlock()

      expect(currentBlock.block.number).toStrictEqual(previousBlock.block.number + 1)
    })

    it("Fund two passkeys through Alice, then transfer funds from one passkey account to another", async () => {
      const senderPublicKey = setupUnifiedPublicKey(PASSKEY_DATA_1.accountPublicKey.encodedValue)
      const receiverPublicKey = setupUnifiedPublicKey(PASSKEY_DATA_2.accountPublicKey.encodedValue)

      await passkeyWalletClient.transferAccountBalanceFromAlice(senderPublicKey, 10000000000)
      await passkeyWalletClient.transferAccountBalanceFromAlice(receiverPublicKey, 10000000000)

      const previousReceiverBalance = await passkeyWalletClient.getAccountWalletBalance(receiverPublicKey)

      const challenge = await passkeyWalletClient.createTransferBalanceChallenge(
        senderPublicKey,
        receiverPublicKey,
        500000,
      )
      const expectedChallengeResult = AUTHENTICATED_PASSKEY_DATA_1.passkeyPayloadCallHex.encodedValue
      expect(challenge.encoding).toStrictEqual(Encoding.BASE64URL)
      expect(challenge.encodedValue).toStrictEqual(expectedChallengeResult)

      const authenticatedPasskeyWalletData: AuthenticatedPasskeyWalletData = {
        accountSignature: PASSKEY_DATA_1.credentialPublicKeySignature,
        passkeyPublicKey: PASSKEY_DATA_1.passkeyCompressedPublicKey,
        passkeySignature: AUTHENTICATED_PASSKEY_DATA_1.signature,
        rawAuthenticatorData: AUTHENTICATED_PASSKEY_DATA_1.authenticatorData,
        rawClientData: AUTHENTICATED_PASSKEY_DATA_1.clientDataJSON,
      }

      const passkeyTransferSuccess = await passkeyWalletClient.transferBalance(
        authenticatedPasskeyWalletData,
        senderPublicKey,
        receiverPublicKey,
        500000,
      )

      expect(passkeyTransferSuccess.type).toStrictEqual(FrequencyResponseType.Transfer)

      const newSenderBalance = await passkeyWalletClient.getAccountWalletBalance(senderPublicKey)
      const newReceiverBalance = await passkeyWalletClient.getAccountWalletBalance(receiverPublicKey)

      expect(newSenderBalance.balance.value).not.toStrictEqual(newReceiverBalance.balance.value)
      expect(newReceiverBalance.balance.value).toStrictEqual(previousReceiverBalance.balance.value + 500000)
    })

    it("Test module error mapping with msa", async () => {
      await expect(passkeyWalletClient.createMsa()).rejects.toMatchObject({
        type: ErrorType.Frequency,
        frequencyErrorId: "KeyAlreadyRegistered",
        description: "Tried to add a key that was already registered to an MSA",
      })
    })
  })

  describe("Provider Boosting", () => {
    const user1PublicKey = setupUnifiedPublicKey(PASSKEY_DATA_1.accountPublicKey.encodedValue)
    const user2PublicKey = setupUnifiedPublicKey(PASSKEY_DATA_2.accountPublicKey.encodedValue)

    const userPublicKey = setupUnifiedPublicKey(PASSKEY_DATA_1.accountPublicKey.encodedValue)
    const authenticatedPasskeyWalletData: AuthenticatedPasskeyWalletData = {
      accountSignature: PASSKEY_DATA_1.credentialPublicKeySignature,
      passkeyPublicKey: PASSKEY_DATA_1.passkeyCompressedPublicKey,
      passkeySignature: AUTHENTICATED_PASSKEY_DATA_1.signature,
      rawAuthenticatorData: AUTHENTICATED_PASSKEY_DATA_1.authenticatorData,
      rawClientData: AUTHENTICATED_PASSKEY_DATA_1.clientDataJSON,
    }

    beforeEach(async () => {
      // Fund user accounts
      await passkeyWalletClient.transferAccountBalanceFromAlice(user1PublicKey, 100 * Number(DOLLARS))
      await passkeyWalletClient.transferAccountBalanceFromAlice(user2PublicKey, 100 * Number(DOLLARS))
    })

    it("Errors when boosting less than the minimum amount", async () => {
      await expect(
        passkeyWalletClient.providerBoost(authenticatedPasskeyWalletData, userPublicKey, BigInt(1), BigInt(100)),
      ).rejects.toMatchObject({
        type: ErrorType.Frequency,
        frequencyErrorId: "StakingAmountBelowMinimum",
        description: "Staker is attempting to stake an amount below the minimum amount.",
      })
    })

    describe("Boosting a provider", async () => {
      const STAKE_AMOUNT = BigInt(1) * DOLLARS

      beforeEach(async () => {
        await passkeyWalletClient.providerBoost(authenticatedPasskeyWalletData, userPublicKey, BigInt(1), STAKE_AMOUNT)
      })

      afterEach(async () => {
        try {
          await passkeyWalletClient.claimRewards(authenticatedPasskeyWalletData, userPublicKey)
        } catch {
          console.debug("No rewards to claim--no cleanup necessary.")
        }

        try {
          await passkeyWalletClient.unstake(
            authenticatedPasskeyWalletData,
            userPublicKey,
            BigInt(1),
            BigInt(1) * DOLLARS,
          )
        } catch {
          console.debug("No amount staked--no cleanup necessary.")
        }

        const endOfCurrentEra = await passkeyWalletClient.getNextRewardEraBlock()
        await passkeyWalletClient.advanceToBlock(endOfCurrentEra)
      })

      it("yields rewards", async () => {
        const endOfBoostingEra = await passkeyWalletClient.getNextRewardEraBlock()
        await passkeyWalletClient.advanceToBlock(endOfBoostingEra)

        const endOfStakedEra = await passkeyWalletClient.getNextRewardEraBlock()
        await passkeyWalletClient.advanceToBlock(endOfStakedEra)

        const rewards = await passkeyWalletClient.listUnclaimedRewards(userPublicKey)

        expect(rewards.length).toStrictEqual(2)

        const [firstReward, secondReward] = rewards

        expect(firstReward.stakedAmount).toStrictEqual(STAKE_AMOUNT)
        expect(firstReward.eligibleAmount).toStrictEqual(BigInt(0))
        expect(firstReward.earnedAmount).toStrictEqual(BigInt(0))

        expect(secondReward.stakedAmount).toStrictEqual(STAKE_AMOUNT)
        expect(secondReward.eligibleAmount).toStrictEqual(STAKE_AMOUNT)
        expect(secondReward.earnedAmount > BigInt(0)).toBe(true)
      })

      it("can be unstaked", async () => {
        const endOfBoostingEra = await passkeyWalletClient.getNextRewardEraBlock()
        await passkeyWalletClient.advanceToBlock(endOfBoostingEra)

        const endOfStakedEra = await passkeyWalletClient.getNextRewardEraBlock()
        await passkeyWalletClient.advanceToBlock(endOfStakedEra)

        // Need to claim before unstaking
        await passkeyWalletClient.claimRewards(authenticatedPasskeyWalletData, userPublicKey)

        await passkeyWalletClient.unstake(authenticatedPasskeyWalletData, userPublicKey, BigInt(1), BigInt(1) * DOLLARS)
      })
    })
  })
})
