import { PolkadotEventIdentifiers } from "@/passkey/clients/passkey/dto/polkadot/events"
import {
  convertPublicKeyEncodedValueToHex,
  convertPublicKeyEncodedValueToU8a,
} from "@/passkey/clients/passkey/helpers/util"
import PolkadotPasskeyWalletClient from "@/passkey/clients/passkey/PolkadotPasskeyWalletClient"
import { PublicKey } from "@/passkey/helpers/interfaces"
import { Keyring } from "@polkadot/api"
import type { u32 } from "@polkadot/types-codec"
import { PalletCapacityRewardEraInfo } from "@polkadot/types/lookup"

class TestPolkadotPasskeyWalletClient extends PolkadotPasskeyWalletClient {
  constructor(providerUri: string) {
    super(providerUri)
  }

  transferAccountBalanceFromAlice = async (receiverAccountPublicKey: PublicKey, transferBalanceAmount: number) => {
    const aliceKeyring = new Keyring({ type: "sr25519" })
    const aliceKeyPair = aliceKeyring.addFromUri("//Alice")
    const receiverAccountPublicKeyU8a = convertPublicKeyEncodedValueToU8a(receiverAccountPublicKey)
    const transferCall = this.api.tx.balances.transferAllowDeath(receiverAccountPublicKeyU8a, transferBalanceAmount)

    return this.sendSigned(transferCall, aliceKeyPair, PolkadotEventIdentifiers.Balances.Transfer)
  }

  createMsa = async () => {
    const aliceKeyring = new Keyring({ type: "sr25519" })
    const aliceKeyPair = aliceKeyring.addFromUri("//Alice")
    const createMsaExtrinsic = this.api.tx.msa.create()
    const msaCreatedEvent = PolkadotEventIdentifiers.Msa.MsaCreated

    return this.sendSigned(createMsaExtrinsic, aliceKeyPair, msaCreatedEvent)
  }

  createProvider = async () => {
    const aliceKeyring = new Keyring({ type: "sr25519" })
    const aliceKeyPair = aliceKeyring.addFromUri("//Alice")
    const providerName = "Alice Provider"

    const createMsaExtrinsic = this.api.tx.msa.createProvider(providerName)
    const providerCreatedEvent = PolkadotEventIdentifiers.Msa.ProviderCreated

    return this.sendSigned(createMsaExtrinsic, aliceKeyPair, providerCreatedEvent)
  }

  getNextRewardEraBlock = async (): Promise<number> => {
    const eraInfo = await this.api.query.capacity.currentEraInfo()
    const actualEraLength = (this.api.consts.capacity.eraLength as u32).toNumber()
    return actualEraLength + (eraInfo as PalletCapacityRewardEraInfo).startedAt.toNumber() + 1
  }

  advanceToBlock = async (targetBlockNumber: number) => {
    let currentBlock = (await this.getCurrentBlock()).block.number
    while (currentBlock < targetBlockNumber) {
      await this.api.rpc.engine.createBlock(true, true)
      currentBlock = (await this.getCurrentBlock()).block.number
    }
  }

  // POC of provider-boosting lookup call
  fetchLedgerForProvider = async (publicKey: PublicKey, msaId: bigint | null) => {
    return this.api.query.capacity.stakingTargetLedger(convertPublicKeyEncodedValueToHex(publicKey), msaId)
  }

  // POC of provider-boosting lookup call
  fetchLedgerForAllProviders = async (publicKey: PublicKey) => {
    const ledgerEntries = await this.api.query.capacity.stakingTargetLedger.entries(
      convertPublicKeyEncodedValueToHex(publicKey),
    )

    ledgerEntries.forEach(([key, entry]) => {
      console.log({ key: key.args.map((k) => k.toHuman()), value: entry.toHuman() })
    })
  }

  // POC of provider-boosting lookup call
  fetchLedger = async (publicKey: PublicKey) => {
    return this.api.query.capacity.stakingAccountLedger(convertPublicKeyEncodedValueToHex(publicKey))
  }

  // POC of provider-boosting lookup call
  getHistories = async (publicKey: PublicKey) => {
    return this.api.query.capacity.providerBoostHistories(convertPublicKeyEncodedValueToHex(publicKey))
  }
}

export default TestPolkadotPasskeyWalletClient
