import PasskeyWalletClient from "@/passkey/clients/passkey/PasskeyWalletClient"
import { AccountBalance } from "@/passkey/clients/passkey/dto/balances"
import {
  FrequencyBlock,
  FrequencyBlockHash,
  FrequencyBlockHashEncoding,
  FrequencySignedBlock,
} from "@/passkey/clients/passkey/dto/block"
import { TokenUnits } from "@/passkey/clients/passkey/dto/tokens"
import {
  convertEncodedBytesValueToU8a,
  convertPublicKeyEncodedValueToHex,
  convertPublicKeyEncodedValueToU8a,
  convertSignatureEncodedValueToU8a,
} from "@/passkey/clients/passkey/helpers/util"
import { ENABLED_ACCOUNT_TYPE } from "@/passkey/constants"
import { AccountType, Encoding } from "@/passkey/helpers/enums"
import { AuthenticatedPasskeyWalletData, EncodedBytes, PublicKey } from "@/passkey/helpers/interfaces"
import { base64urlToUtf8, convertHexToBase64URLString, stringToByteArray } from "@/passkey/helpers/util"
import { ApiPromise, WsProvider } from "@polkadot/api"
import { SubmittableExtrinsic } from "@polkadot/api/types"
import { KeyringPair } from "@polkadot/keyring/types"
import { AccountInfo, DispatchError, EventRecord } from "@polkadot/types/interfaces"
import { CommonPrimitivesCapacityUnclaimedRewardInfo } from "@polkadot/types/lookup"
import { Codec, ISubmittableResult } from "@polkadot/types/types"
import { cryptoWaitReady } from "@polkadot/util-crypto"

import { ErrorType, FrequencyError } from "@/errors"
import { FrequencyErrorId, substrateErrorToFrequencyErrorId } from "@/errors/FrequencyError"
import { UnclaimedReward } from "@/passkey/clients/passkey/dto/capacity"
import {
  PolkadotEvent,
  PolkadotEventIdentifier,
  PolkadotEventIdentifiers,
  PolkadotMethod,
  PolkadotSection,
} from "@/passkey/clients/passkey/dto/polkadot/events"
import { ClaimRewardsResponse, FrequencyResponseType, TransferResponse } from "@/passkey/clients/passkey/dto/response"
import { callToBase64Url, parsePolkadotEvent } from "@/passkey/clients/passkey/helpers/polkadot"
import { hexToBigInt } from "@polkadot/util"

interface PasskeyProxyPayload {
  passkeyPublicKey: number[]
  verifiablePasskeySignature: {
    signature: number[]
    authenticatorData: number[]
    clientDataJson: number[]
  }
  accountOwnershipProof: object
  passkeyCall: Codec
}

class PolkadotPasskeyWalletClient implements PasskeyWalletClient {
  private readonly providerUri: string
  private prefix: number
  private unit: string
  protected api: ApiPromise

  constructor(providerUri: string) {
    this.providerUri = providerUri
  }

  init = async () => {
    this.api = await this.loadApi()
    const chain = await this.api.rpc.system.properties()
    this.prefix = Number(chain.ss58Format.toString())
    this.unit = chain.tokenSymbol.toString()
  }

  async destroy() {
    await this.api.disconnect()
  }

  getCurrentBlock = async (
    frequencyBlockHash?: FrequencyBlockHash,
    hashEncoding: FrequencyBlockHashEncoding = FrequencyBlockHashEncoding.HEX,
  ): Promise<FrequencySignedBlock> => {
    const currentBlock =
      frequencyBlockHash != undefined
        ? await this.api.rpc.chain.getBlock(frequencyBlockHash.hash)
        : await this.api.rpc.chain.getBlock()

    const frequencyBlock: FrequencyBlock = {
      number: currentBlock.block.header.number.toNumber(),
    }

    let currentBlockHash: FrequencyBlockHash
    switch (hashEncoding) {
      case FrequencyBlockHashEncoding.UINT8ARRAY:
        currentBlockHash = {
          hash: currentBlock.hash.toU8a(),
          encoding: FrequencyBlockHashEncoding.UINT8ARRAY,
        }
        break

      default:
        currentBlockHash = {
          hash: currentBlock.hash.toHex(),
          encoding: FrequencyBlockHashEncoding.HEX,
        }
        break
    }

    return {
      block: frequencyBlock,
      hash: currentBlockHash,
    }
  }

  getNonce = async (accountAddress: string): Promise<number> => {
    const nonce = await this.api.call.accountNonceApi.accountNonce(accountAddress)
    return Number(nonce)
  }

  getFinalizedHead = async (
    hashEncoding: FrequencyBlockHashEncoding = FrequencyBlockHashEncoding.HEX,
  ): Promise<FrequencyBlockHash> => {
    const finalizedHead = await this.api.rpc.chain.getFinalizedHead()
    let finalizedBlockHash: FrequencyBlockHash
    switch (hashEncoding) {
      case FrequencyBlockHashEncoding.UINT8ARRAY:
        finalizedBlockHash = {
          hash: finalizedHead.hash.toU8a(),
          encoding: FrequencyBlockHashEncoding.UINT8ARRAY,
        }
        break

      default:
        finalizedBlockHash = {
          hash: finalizedHead.hash.toHex(),
          encoding: FrequencyBlockHashEncoding.HEX,
        }
        break
    }

    return finalizedBlockHash
  }

  getAccountWalletBalance = async (publicKey: PublicKey): Promise<AccountBalance> => {
    const publicKeyU8a = convertPublicKeyEncodedValueToU8a(publicKey)
    const userAccountWalletInfo = await this.api.query.system.account<AccountInfo>(publicKeyU8a)
    return {
      balance: {
        unit: TokenUnits.PLANCK,
        value: userAccountWalletInfo.data.free.toNumber(),
      },
    }
  }

  createTransferBalanceChallenge = async (
    senderAccountPublicKey: PublicKey,
    receiverAccountPublicKey: PublicKey,
    transferAmount: number,
  ): Promise<EncodedBytes> => {
    const passkeyCall = await this.createPasskeyProxiedTransferCall(
      receiverAccountPublicKey,
      transferAmount,
      senderAccountPublicKey,
    )

    return {
      encoding: Encoding.BASE64URL,
      encodedValue: callToBase64Url(passkeyCall),
    }
  }

  transferBalance = async (
    passkeyData: AuthenticatedPasskeyWalletData,
    senderAccountPublicKey: PublicKey,
    receiverAccountPublicKey: PublicKey,
    transferAmount: number,
  ): Promise<TransferResponse> => {
    const passkeyCall = await this.createPasskeyProxiedTransferCall(
      receiverAccountPublicKey,
      transferAmount,
      senderAccountPublicKey,
    )
    const passkeyPayloadExtrinsic = await this.createPasskeyPayloadExtrinsic(passkeyCall, passkeyData)

    const expectedEvent = PolkadotEventIdentifiers.Balances.Transfer

    const event = await this.sendUnsigned(passkeyPayloadExtrinsic, expectedEvent)
    if (event.section !== PolkadotSection.Balances || event.method !== PolkadotMethod.Transfer) {
      throw new Error("") // TODO
    }

    return { type: FrequencyResponseType.Transfer, amount: event.amount }
  }

  createProviderBoostChallenge = async (
    userAccountPublicKey: PublicKey,
    providerMsaId: bigint,
    amount: bigint,
  ): Promise<EncodedBytes> => {
    const providerBoostCall = await this.createPasskeyProxiedProviderBoostCall(
      userAccountPublicKey,
      providerMsaId,
      amount,
    )

    return {
      encoding: Encoding.BASE64URL,
      encodedValue: callToBase64Url(providerBoostCall),
    }
  }

  providerBoost = async (
    passkeyData: AuthenticatedPasskeyWalletData,
    userAccountPublicKey: PublicKey,
    providerMsaId: bigint,
    amount: bigint,
  ): Promise<void> => {
    const providerBoost = await this.createPasskeyProxiedProviderBoostCall(userAccountPublicKey, providerMsaId, amount)
    const passkeyPayloadExtrinsic = await this.createPasskeyPayloadExtrinsic(providerBoost, passkeyData)

    await this.sendUnsigned(passkeyPayloadExtrinsic, PolkadotEventIdentifiers.Capacity.ProviderBoosted)
  }

  createUnstakeChallenge = async (
    userAccountPublicKey: PublicKey,
    providerMsaId: bigint,
    amount: bigint,
  ): Promise<EncodedBytes> => {
    const unstakeCall = await this.createPasskeyProxiedUnstakeCall(userAccountPublicKey, providerMsaId, amount)

    return {
      encoding: Encoding.BASE64URL,
      encodedValue: callToBase64Url(unstakeCall),
    }
  }

  unstake = async (
    passkeyData: AuthenticatedPasskeyWalletData,
    userAccountPublicKey: PublicKey,
    providerMsaId: bigint,
    amount: bigint,
  ): Promise<void> => {
    const unstakeCall = await this.createPasskeyProxiedUnstakeCall(userAccountPublicKey, providerMsaId, amount)

    const passkeyPayloadExtrinsic = await this.createPasskeyPayloadExtrinsic(unstakeCall, passkeyData)
    await this.sendUnsigned(passkeyPayloadExtrinsic, PolkadotEventIdentifiers.Capacity.UnStaked)
  }

  createClaimRewardsChallenge = async (userAccountPublicKey: PublicKey): Promise<EncodedBytes> => {
    const claimRewardsCall = await this.createPasskeyProxiedClaimStakingRewardsCall(userAccountPublicKey)

    return {
      encoding: Encoding.BASE64URL,
      encodedValue: callToBase64Url(claimRewardsCall),
    }
  }

  claimRewards = async (
    passkeyData: AuthenticatedPasskeyWalletData,
    userAccountPublicKey: PublicKey,
  ): Promise<ClaimRewardsResponse> => {
    const claimRewardsCall = await this.createPasskeyProxiedClaimStakingRewardsCall(userAccountPublicKey)

    const passkeyPayloadExtrinsic = await this.createPasskeyPayloadExtrinsic(claimRewardsCall, passkeyData)

    const expectedEvent = PolkadotEventIdentifiers.Capacity.ProviderBoostRewardClaimed
    const event = await this.sendUnsigned(passkeyPayloadExtrinsic, expectedEvent)

    if (event.section !== PolkadotSection.Capacity || event.method !== PolkadotMethod.ProviderBoostRewardClaimed) {
      throw new Error("") // TODO
    }

    return {
      type: FrequencyResponseType.ClaimRewards,
      amount: event.rewardAmount,
    }
  }

  listUnclaimedRewards = async (userAccountPublicKey: PublicKey): Promise<UnclaimedReward[]> => {
    const results = await this.api.call.capacityRuntimeApi.listUnclaimedRewards<
      Iterable<CommonPrimitivesCapacityUnclaimedRewardInfo>
    >(convertPublicKeyEncodedValueToHex(userAccountPublicKey))

    const unclaimedRewards = [...results].map((element) => {
      return {
        era: hexToBigInt(element.rewardEra.toHex()),
        expiresAtBlock: hexToBigInt(element.expiresAtBlock.toHex()),
        stakedAmount: hexToBigInt(element.stakedAmount.toHex()),
        eligibleAmount: hexToBigInt(element.eligibleAmount.toHex()),
        earnedAmount: hexToBigInt(element.earnedAmount.toHex()),
      }
    })

    return unclaimedRewards
  }

  private loadApi = async () => {
    await cryptoWaitReady()
    const singletonProvider = new WsProvider(this.providerUri)
    return await ApiPromise.create({
      provider: singletonProvider,
      throwOnConnect: true,
    })
  }

  private createPasskeyProxiedTransferCall = async (
    receiverAccountPublicKey: PublicKey,
    transferAmount: number,
    senderAccountPublicKey: PublicKey,
  ): Promise<Codec> => {
    const receiverAccountPublicKeyU8a = convertPublicKeyEncodedValueToU8a(receiverAccountPublicKey)
    const transferCall = this.api.tx.balances.transferAllowDeath(receiverAccountPublicKeyU8a, transferAmount)

    return await this.createPasskeyProxyCall(transferCall, senderAccountPublicKey)
  }

  private createPasskeyProxiedProviderBoostCall = async (
    userAccountPublicKey: PublicKey,
    targetMsaId: bigint,
    amount: bigint,
  ): Promise<Codec> => {
    const providerBoostCall = this.api.tx.capacity.providerBoost(targetMsaId, amount)

    return await this.createPasskeyProxyCall(providerBoostCall, userAccountPublicKey)
  }

  private createPasskeyProxiedUnstakeCall = async (
    userAccountPublicKey: PublicKey,
    targetMsaId: bigint,
    amount: bigint,
  ): Promise<Codec> => {
    const unstakeCall = this.api.tx.capacity.unstake(targetMsaId, amount)

    return await this.createPasskeyProxyCall(unstakeCall, userAccountPublicKey)
  }

  private createPasskeyProxiedClaimStakingRewardsCall = async (userAccountPublicKey: PublicKey): Promise<Codec> => {
    const claimCall = this.api.tx.capacity.claimStakingRewards()

    return await this.createPasskeyProxyCall(claimCall, userAccountPublicKey)
  }

  private createPasskeyProxyCall = async (
    extrinsic: SubmittableExtrinsic<"promise", ISubmittableResult>,
    userAccountPublicKey: PublicKey,
  ): Promise<Codec> => {
    const accountNonce = await this.getNonce(convertPublicKeyEncodedValueToHex(userAccountPublicKey))

    // Create the passkey call that wraps over the extrinsic call
    const call = this.api.registry.createType("Call", extrinsic)
    const passkeyCall = this.api.createType("PalletPasskeyPasskeyCallV2", {
      accountId: userAccountPublicKey.encodedValue,
      accountNonce,
      call,
    })

    return passkeyCall
  }

  private createPasskeyPayloadExtrinsic = async (
    passkeyCall: Codec,
    passkeyData: AuthenticatedPasskeyWalletData,
  ): Promise<SubmittableExtrinsic<"promise", ISubmittableResult>> => {
    const authenticatorDataBytes = convertEncodedBytesValueToU8a(passkeyData.rawAuthenticatorData)

    // inject challenge inside clientData
    const clientDataJson = this.injectChallengeReplacer(callToBase64Url(passkeyCall), passkeyData.rawClientData)
    const passkeyPublicKeyBytes = convertPublicKeyEncodedValueToU8a(passkeyData.passkeyPublicKey)
    const accountSignatureU8a = convertSignatureEncodedValueToU8a(passkeyData.accountSignature)
    const passkeySignatureU8a = convertSignatureEncodedValueToU8a(passkeyData.passkeySignature)

    let accountOwnershipProof: object
    switch (ENABLED_ACCOUNT_TYPE) {
      case AccountType.SR25519: {
        accountOwnershipProof = {
          Sr25519: accountSignatureU8a,
        }
        break
      }
      case AccountType.ETHEREUM:
      case AccountType.ETHEREUM_EIP_712: {
        accountOwnershipProof = {
          Ecdsa: accountSignatureU8a,
        }
        break
      }
      default:
        throw new Error(`Unhandled case: ${ENABLED_ACCOUNT_TYPE}`)
    }

    const passkeyProxyPayload: PasskeyProxyPayload = {
      passkeyPublicKey: Array.from(passkeyPublicKeyBytes),
      verifiablePasskeySignature: {
        signature: Array.from(passkeySignatureU8a),
        authenticatorData: Array.from(authenticatorDataBytes),
        clientDataJson: stringToByteArray(clientDataJson),
      },
      accountOwnershipProof,
      passkeyCall,
    }

    const passkeyProxyExtrinsic = this.api.tx.passkey.proxyV2(passkeyProxyPayload)

    return passkeyProxyExtrinsic
  }

  private injectChallengeReplacer = (passkeyCallBase64Url: string, rawClientData: EncodedBytes): string => {
    const challengeReplacer = "#rplc#"
    // inject challenge inside clientData
    let base64UrlValue = rawClientData.encodedValue
    switch (rawClientData.encoding) {
      case Encoding.BASE16:
        base64UrlValue = convertHexToBase64URLString(rawClientData.encodedValue)
        break
      case Encoding.BASE64URL:
        //Already encoded properly
        break
      default:
        throw new Error(`Raw Client Data not supported at this time: ${rawClientData.encoding}`)
    }
    return base64urlToUtf8(base64UrlValue).replace(challengeReplacer, passkeyCallBase64Url)
  }

  protected sendExtrinsic = async (
    extrinsic: SubmittableExtrinsic<"promise", ISubmittableResult>,
    expectedEvent: PolkadotEventIdentifier,
    keyPair: KeyringPair | null = null,
  ): Promise<PolkadotEvent> => {
    let unsubscribe = () => {}

    // eslint-disable-next-line no-async-promise-executor
    return new Promise<PolkadotEvent>(async (resolve, reject) => {
      const extrinsicEventHandler = (result: ISubmittableResult) => {
        if (result.dispatchError) {
          const error = this.dispatchErrorToFrequencyError(result.dispatchError)

          reject(error)
        }

        if (result.status.isFinalized) {
          const foundEvent = this.findSpecificEvent(result.events, expectedEvent)

          if (foundEvent !== null) {
            resolve(foundEvent)
          } else {
            const error: FrequencyError = {
              type: ErrorType.Frequency,
              description: `Expected event ${expectedEvent.section}.${expectedEvent.method} not found`,
              frequencyErrorId: FrequencyErrorId.Unknown,
            }
            reject(error)
          }
        }
      }

      try {
        if (keyPair !== null) {
          unsubscribe = await extrinsic.signAndSend(keyPair, extrinsicEventHandler)
        } else {
          unsubscribe = await extrinsic.send(extrinsicEventHandler)
        }
      } catch (e: unknown) {
        const message = e !== null && typeof e == "object" && "message" in e ? String(e.message) : null
        const error: FrequencyError = {
          type: ErrorType.Frequency,
          description: message ?? "An error occurred attempting to submit an extrinsic",
          frequencyErrorId: FrequencyErrorId.Unknown,
        }
        reject(error)
      }
    }).finally(() => {
      unsubscribe()
    })
  }

  protected sendSigned = async (
    extrinsic: SubmittableExtrinsic<"promise", ISubmittableResult>,
    keyPair: KeyringPair,
    expectedEvent: PolkadotEventIdentifier,
  ): Promise<PolkadotEvent> => {
    return await this.sendExtrinsic(extrinsic, expectedEvent, keyPair)
  }

  protected sendUnsigned = async (
    extrinsic: SubmittableExtrinsic<"promise", ISubmittableResult>,
    expectedEvent: PolkadotEventIdentifier,
  ): Promise<PolkadotEvent> => {
    return await this.sendExtrinsic(extrinsic, expectedEvent)
  }

  protected findSpecificEvent = (
    eventList: EventRecord[],
    specificEvent: PolkadotEventIdentifier,
  ): PolkadotEvent | null => {
    const selectedEvent = eventList.find(({ event }) => {
      return event.section == specificEvent.section && event.method == specificEvent.method
    })
    if (selectedEvent !== undefined) {
      return parsePolkadotEvent(selectedEvent)
    } else {
      return null
    }
  }

  private dispatchErrorToFrequencyError = (dispatchError: DispatchError): FrequencyError => {
    if (dispatchError.isModule) {
      // for module errors, we have the section indexed, lookup
      const { docs, name, section } = this.api.registry.findMetaError(dispatchError.asModule)
      return {
        type: ErrorType.Frequency,
        description: docs.join(" "),
        frequencyErrorId: substrateErrorToFrequencyErrorId(section, name),
      }
    } else {
      // Other, CannotLookup, BadOrigin, no extra info
      return {
        type: ErrorType.Frequency,
        description: dispatchError.toString(),
        frequencyErrorId: FrequencyErrorId.Unknown,
      }
    }
  }
}

export default PolkadotPasskeyWalletClient
