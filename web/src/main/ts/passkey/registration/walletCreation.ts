import { ENABLED_ACCOUNT_TYPE } from "@/passkey/constants"
import { AccountType, Encoding, SignatureAlgos } from "@/passkey/helpers/enums"
import { Environment, Signature } from "@/passkey/helpers/interfaces"
import { createPasskeyPublicKey, HexString, sign } from "@frequency-chain/ethereum-utils"
import { ChainType } from "@frequency-chain/ethereum-utils/payloads"
import { Keyring } from "@polkadot/keyring"
import { hexToU8a, isHex, u8aToHex, u8aWrapBytes } from "@polkadot/util"
import { cryptoWaitReady, mnemonicGenerate } from "@polkadot/util-crypto"
import { ethers } from "ethers"

const sr25519Keyring = new Keyring({ type: "sr25519" })
const defaultBip44Path: string = "m/44'/60'/0'/0/0"

let isReady = false
let SEED = ""

cryptoWaitReady().then(() => {
  isReady = true
})

export const generateSeedPhrase = async (): Promise<string> => {
  return cryptoWaitReady().then(() => {
    isReady = true
    switch (ENABLED_ACCOUNT_TYPE) {
      case AccountType.SR25519: {
        SEED = mnemonicGenerate()
        break
      }
      case AccountType.ETHEREUM:
      case AccountType.ETHEREUM_EIP_712: {
        // Calculate required entropy bytes based on word count = (wordcount * 4 / 3)
        const bytes = ethers.utils.randomBytes(16)
        SEED = ethers.utils.entropyToMnemonic(bytes)
        break
      }
      default:
        throw new Error(`Unhandled case: ${ENABLED_ACCOUNT_TYPE}`)
    }
    return SEED
  })
}

export const checkAndStoreSeedPhrase = async (
  seedPhrase: string,
  providedPublicKeyHex: HexString,
): Promise<boolean> => {
  return cryptoWaitReady().then(() => {
    isReady = true
    SEED = seedPhrase
    // We don't know which wallet type they might have used. We need to assume
    // any and try and generate and match the public key against any.

    //AccountType.SR25519
    const sr25519GeneratedKeyPair = sr25519Keyring.createFromUri(SEED)
    const sr25519GeneratedPublicKeyHex = u8aToHex(sr25519GeneratedKeyPair.publicKey)
    if (providedPublicKeyHex === sr25519GeneratedPublicKeyHex) return true

    //AccountType.ETHEREUM
    //AccountType.ETHEREUM_EIP_712
    const ethereumPublicKeyHex = getEthereumWallet().publicKey
    if (ethereumPublicKeyHex == providedPublicKeyHex) return true

    //Add more wallet seed phrase types here to compare to if needed.
    return false
  })
}

export const getEthereumWallet = (): ethers.utils.HDNode => {
  const wallet = ethers.utils.HDNode.fromMnemonic(SEED)
  return wallet.derivePath(defaultBip44Path)
}

export const generateAndReturnPublicKey = async (): Promise<string> => {
  if (!isReady) {
    throw new Error("Library is not loaded yet!")
  }

  switch (ENABLED_ACCOUNT_TYPE) {
    case AccountType.SR25519: {
      const pair = sr25519Keyring.createFromUri(SEED)
      return u8aToHex(pair.publicKey)
    }
    case AccountType.ETHEREUM:
    case AccountType.ETHEREUM_EIP_712: {
      const wallet = getEthereumWallet()
      return wallet.publicKey
    }
    default:
      throw new Error(`Unhandled case: ${ENABLED_ACCOUNT_TYPE}`)
  }
}

export const signPasskeyPublicKey = async (
  compressedPublicKeyHex: string,
  environment: Environment,
): Promise<Signature> => {
  if (!isHex(compressedPublicKeyHex)) {
    throw new Error("Input is not a valid hex!")
  }

  const payloadBytes = hexToU8a(compressedPublicKeyHex)

  switch (ENABLED_ACCOUNT_TYPE) {
    case AccountType.SR25519: {
      const pair = sr25519Keyring.createFromUri(SEED)
      const wrappedPayload = u8aWrapBytes(payloadBytes)
      const signedCompressedPublicKey = u8aToHex(pair.sign(wrappedPayload))

      return {
        algo: SignatureAlgos.SR25519,
        encoding: Encoding.BASE16,
        encodedValue: signedCompressedPublicKey,
      }
    }

    case AccountType.ETHEREUM: {
      const wallet = ethers.Wallet.fromMnemonic(SEED, defaultBip44Path)
      const signedCompressedPublicKey = await wallet.signMessage(payloadBytes)

      return {
        algo: SignatureAlgos.SECP256K1,
        encoding: Encoding.BASE16,
        encodedValue: signedCompressedPublicKey,
      }
    }

    case AccountType.ETHEREUM_EIP_712: {
      const privateKey = ethers.Wallet.fromMnemonic(SEED, defaultBip44Path).privateKey as HexString
      const EcdsaSignature = await sign(privateKey, createPasskeyPublicKey(payloadBytes), toChainType(environment))
      return {
        algo: SignatureAlgos.SECP256K1,
        encoding: Encoding.BASE16,
        encodedValue: EcdsaSignature.Ecdsa,
      }
    }

    default:
      throw new Error(`Unhandled case: ${ENABLED_ACCOUNT_TYPE}`)
  }
}

export const triggerSeedPhraseDownload = () => {
  if (SEED.length === 0) {
    throw new Error("Keypair is not generated!")
  }

  let selectedKeyType: string
  let address: string
  switch (ENABLED_ACCOUNT_TYPE) {
    case AccountType.SR25519: {
      const pair = sr25519Keyring.createFromUri(SEED)
      address = pair.address
      selectedKeyType = "sr25519"
      break
    }
    case AccountType.ETHEREUM:
    case AccountType.ETHEREUM_EIP_712: {
      const wallet = getEthereumWallet()
      address = wallet.address
      selectedKeyType = "ethereum"
      break
    }
    default:
      throw new Error(`Unhandled case: ${ENABLED_ACCOUNT_TYPE}`)
  }

  const seedData = {
    seed: SEED,
    whenCreated: new Date().getTime(),
    encoding: {
      content: ["plaintext"],
    },
    keyType: selectedKeyType,
    address: address,
  }

  return JSON.stringify(seedData)
}

function toChainType(environment: Environment) {
  let eip712Domain: ChainType
  switch (environment) {
    case Environment.MAIN:
      eip712Domain = "Mainnet-Frequency"
      break
    case Environment.TEST:
    case Environment.INTEGRATION:
    case Environment.DEV:
      eip712Domain = "Paseo-Testnet-Frequency"
      break
  }

  return eip712Domain
}
