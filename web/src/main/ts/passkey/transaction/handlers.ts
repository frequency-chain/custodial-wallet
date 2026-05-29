import EthereumPasskeyWalletClient from "@/passkey/clients/passkey/EthereumPasskeyWalletClient"
import PasskeyWalletClient from "@/passkey/clients/passkey/PasskeyWalletClient"
import PolkadotPasskeyWalletClient from "@/passkey/clients/passkey/PolkadotPasskeyWalletClient"
import { ENABLED_CHAIN_TYPE } from "@/passkey/constants"
import { ChainType, Encoding, EncodingFormat, PublicKeyType } from "@/passkey/helpers/enums"
import { getUnifiedAccountAddress } from "@/passkey/helpers/keys"
import { hexToU8a, u8aToHex } from "@polkadot/util"
import { PublicKeyCredentialDescriptorJSON } from "@simplewebauthn/types"
import {
  AuthenticateCredentialData,
  AuthenticatedPasskeyWalletData,
  CredentialResponse,
  CredentialsResponse,
  EncodedBytes,
  PublicKey,
} from "../helpers/interfaces"
import { convertBase64URLStringToHex, getCookie, showErrorMessage, showSuccessMessage } from "../helpers/util"
import { authenticateCredential, createAllowCredentialsList, getCredentialsResponse } from "./authenticate"

// TODO: Replace all `Error`s and `showErrorMessage` with throwing `ApplicationError`s

export const transferFundsToAnotherUser = async (
  transferAmount: number,
  receiverAccountPublicKey: string,
  frequencyAddress: string,
  origin: string,
) => {
  //Check if a receiver address was given
  if (receiverAccountPublicKey == undefined || receiverAccountPublicKey == "") {
    console.log("Enter a receiver account public key in hex")
    showErrorMessage(`Please enter a receiver account public key address in hex, and try again.`)
    return Error("No receiving address given")
  }
  try {
    let passkeyWalletClient: PasskeyWalletClient
    switch (ENABLED_CHAIN_TYPE) {
      case ChainType.POLKADOT:
        passkeyWalletClient = new PolkadotPasskeyWalletClient(frequencyAddress)
        break
      case ChainType.ETHEREUM:
        passkeyWalletClient = new EthereumPasskeyWalletClient(frequencyAddress)
        break
    }
    await passkeyWalletClient.init()

    //Preload given sender's passkey credentials that are saved to the custodial wallet
    //Necessary to create the challenge that the authenticator signs for the passkey proxy call
    const userCredentialsList = await getCredentialsResponse(origin)
    if (userCredentialsList.credentials.length < 1) {
      console.log("No credentials found")
      showErrorMessage("Please register a passkey before attempting a transaction")
      return Error("User Credentials not found")
    } else {
      const allowCredentialsList = createAllowCredentialsList(userCredentialsList)
      await authenticateAndProcessPasskeyProxyTransaction(
        allowCredentialsList,
        userCredentialsList.credentials,
        receiverAccountPublicKey,
        transferAmount,
        passkeyWalletClient,
      )
      console.log("Transfer succeeded")
      showSuccessMessage(`Transfer of ${transferAmount} complete. Congratulations!`)
    }
  } catch (e) {
    console.log("Transfer failed")
    console.error(JSON.stringify(e))
    showErrorMessage('Balance transfer failed, please press "Transfer" button to try again')
  }
}

export const getUserCredentials = async (host: string): Promise<CredentialsResponse> => {
  const userCredentials = await getCredentialsResponse(host)
  if (userCredentials.credentials.length < 1) {
    throw Error("User does not have any credentials registered!")
  }

  return userCredentials
}

// NOTE(Julian, 2024-12-18): This function assumes that when there are multiple passkey
// wallets for a user they all share the same blockchain account key.
export const getAccountPublicKeyHex = (userCredentialsList: CredentialResponse[]): string => {
  // De-duplicate the account public keys from the list of stored credential data
  const accountPublicKeyValues = new Set(
    userCredentialsList.map((it) => {
      return it.accountPublicKey.encodedValue
    }),
  )

  // Expect to end up with exactly one account public key
  if (accountPublicKeyValues.size === 0) {
    const sessionId = getCookie("SESSION_ID")
    throw new Error(`No account key found for user. sessionId=${sessionId}`)
  }
  if (accountPublicKeyValues.size > 1) {
    const sessionId = getCookie("SESSION_ID")
    throw new Error(`Multiple account keys found for user. sessionId=${sessionId}`)
  }

  const accountPublicKeyBase64Url = [...accountPublicKeyValues][0]
  return convertBase64URLStringToHex(accountPublicKeyBase64Url)
}

export const getUnifiedAccountPublicKey = (credentials: CredentialResponse[]): PublicKey => {
  const accountPublicKeyHex = getAccountPublicKeyHex(credentials)
  const accountPublicKeyBytes = hexToU8a(accountPublicKeyHex)

  const unifiedAccountPublicKeyBytes = getUnifiedAccountAddress(accountPublicKeyBytes)
  const unifiedAccountPublicKeyHex = u8aToHex(unifiedAccountPublicKeyBytes)

  const unifiedAccountPublicKey: PublicKey = {
    encodedValue: unifiedAccountPublicKeyHex,
    encoding: Encoding.BASE16,
    format: EncodingFormat.COMPRESSED_HEX,
    type: PublicKeyType.SECP256K1,
  }

  return unifiedAccountPublicKey
}

export const authenticatePasskey = async (
  host: string,
  challenge: EncodedBytes,
  allowCredentialsList: PublicKeyCredentialDescriptorJSON[],
  credentials: CredentialResponse[],
): Promise<AuthenticatedPasskeyWalletData> => {
  const credential: AuthenticateCredentialData = await authenticateCredential(host, challenge, allowCredentialsList)
  const credentialId = credential.credentialId

  //Find the data corresponding to the passkey the use chose to authenticate with
  const properCredential = credentials.find((it) => {
    return it.credentialId == credentialId
  })

  if (properCredential === null || properCredential === undefined) {
    throw new Error("Failed to find 'properCredential'!")
  }

  const rawAuthenticatorData = credential.authenticatorData
  const rawClientData = credential.clientDataJSON

  return {
    accountSignature: properCredential.credentialPublicKeySignature,
    passkeyPublicKey: properCredential.passkeyCompressedPublicKey,
    passkeySignature: credential.signature,
    rawAuthenticatorData,
    rawClientData,
  }
}

const authenticateAndProcessPasskeyProxyTransaction = async (
  allowCredentialsList: PublicKeyCredentialDescriptorJSON[],
  userCredentialsList: CredentialResponse[],
  receiverAccountPublicKeyHex: string,
  transferAmount: number,
  passkeyWalletClient: PasskeyWalletClient,
) => {
  const receiverAccountPublicKey: PublicKey = {
    encodedValue: receiverAccountPublicKeyHex,
    encoding: Encoding.BASE16,
    format: EncodingFormat.COMPRESSED_HEX,
    type: PublicKeyType.SECP256K1,
  }

  //Get account public key
  const accountPublicKeyHex = getAccountPublicKeyHex(userCredentialsList)
  const accountPublicKeyBytes = hexToU8a(accountPublicKeyHex)

  const unifiedAccountPublicKeyBytes = getUnifiedAccountAddress(accountPublicKeyBytes)
  const unifiedAccountPublicKeyHex = u8aToHex(unifiedAccountPublicKeyBytes)

  const unifiedAccountPublicKey: PublicKey = {
    encodedValue: unifiedAccountPublicKeyHex,
    encoding: Encoding.BASE16,
    format: EncodingFormat.COMPRESSED_HEX,
    type: PublicKeyType.SECP256K1,
  }

  //Create the passkey call string for challenge
  const challenge = await passkeyWalletClient.createTransferBalanceChallenge(
    unifiedAccountPublicKey,
    receiverAccountPublicKey,
    transferAmount,
  )

  //Authenticate passkey and with that sign the passkey call, proper authenticator data and client data
  const credential: AuthenticateCredentialData = await authenticateCredential(origin, challenge, allowCredentialsList)
  const credentialId = credential.credentialId

  //Find the data corresponding to the passkey the use chose to authenticate with
  const properCredential = userCredentialsList.find((it) => {
    return it.credentialId == credentialId
  })

  if (properCredential === null || properCredential === undefined) {
    throw new Error("Failed to find 'properCredential'!")
  }

  const rawAuthenticatorData = credential.authenticatorData
  const rawClientData = credential.clientDataJSON
  const passkeyData: AuthenticatedPasskeyWalletData = {
    accountSignature: properCredential.credentialPublicKeySignature,
    passkeyPublicKey: properCredential.passkeyCompressedPublicKey,
    passkeySignature: credential.signature,
    rawAuthenticatorData,
    rawClientData,
  }

  await passkeyWalletClient.transferBalance(
    passkeyData,
    unifiedAccountPublicKey,
    receiverAccountPublicKey,
    transferAmount,
  )
}
