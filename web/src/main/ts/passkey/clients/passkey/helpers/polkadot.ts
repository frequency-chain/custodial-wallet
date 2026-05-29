import { base64urlEncode } from "@/passkey/helpers/util"
import { sha256 } from "@noble/hashes/sha256"
import { EventRecord } from "@polkadot/types/interfaces"
import { Codec } from "@polkadot/types/types"
import { hexToBigInt } from "@polkadot/util"
import { PolkadotEvent, PolkadotMethod, PolkadotSection } from "../dto/polkadot/events"

export const callToBase64Url = (call: Codec): string => {
  return base64urlEncode(sha256(call.toU8a()))
}

export const parsePolkadotEvent = (eventRecord: EventRecord): PolkadotEvent => {
  const method = eventRecord.event.method

  switch (eventRecord.event.section) {
    case PolkadotSection.Balances: {
      if (method === PolkadotMethod.Transfer) {
        const [from, to, amount] = eventRecord.event.data
        return {
          section: PolkadotSection.Balances,
          method: PolkadotMethod.Transfer,
          to: to.toString(),
          from: from.toString(),
          amount: hexToBigInt(amount.toHex()),
        }
      }
    }

    case PolkadotSection.Capacity: {
      if (method === PolkadotMethod.ProviderBoosted) {
        const [account, target, amount, capacity] = eventRecord.event.data
        return {
          section: PolkadotSection.Capacity,
          method: PolkadotMethod.ProviderBoosted,
          accountId: account.toString(),
          providerMsaId: hexToBigInt(target.toHex()),
          amount: hexToBigInt(amount.toHex()),
          capacity: hexToBigInt(capacity.toHex()),
        }
      } else if (method === PolkadotMethod.ProviderBoostRewardClaimed) {
        const [account, rewardAmount] = eventRecord.event.data
        return {
          section: PolkadotSection.Capacity,
          method: PolkadotMethod.ProviderBoostRewardClaimed,
          accountId: account.toString(),
          rewardAmount: hexToBigInt(rewardAmount.toHex()),
        }
      } else if (method === PolkadotMethod.UnStaked) {
        const [account, target, amount, capacity] = eventRecord.event.data
        return {
          section: PolkadotSection.Capacity,
          method: PolkadotMethod.UnStaked,
          accountId: account.toString(),
          providerMsaId: hexToBigInt(target.toHex()),
          amount: hexToBigInt(amount.toHex()),
          capacity: hexToBigInt(capacity.toHex()),
        }
      }
    }

    case PolkadotSection.Msa: {
      if (method === PolkadotMethod.MsaCreated) {
        const [msaId, key] = eventRecord.event.data
        return {
          section: PolkadotSection.Msa,
          method: PolkadotMethod.MsaCreated,
          msaId: hexToBigInt(msaId.toHex()),
          accountId: key.toString(),
        }
      } else if (method === PolkadotMethod.ProviderCreated) {
        const [providerId] = eventRecord.event.data
        return {
          section: PolkadotSection.Msa,
          method: PolkadotMethod.ProviderCreated,
          msaId: hexToBigInt(providerId.toHex()),
        }
      }
    }
  }

  throw new Error(`Unrecognized polkadot event: ${eventRecord}`)
}
