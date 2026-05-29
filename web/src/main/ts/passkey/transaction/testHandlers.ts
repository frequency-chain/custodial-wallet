import EthereumPasskeyWalletClient from "@/passkey/clients/passkey/EthereumPasskeyWalletClient"
import PasskeyWalletClient from "@/passkey/clients/passkey/PasskeyWalletClient"
import PolkadotPasskeyWalletClient from "@/passkey/clients/passkey/PolkadotPasskeyWalletClient"
import TestPolkadotPasskeyWalletClient from "@/passkey/clients/passkey/TestPolkadotPasskeyWalletClient"
import { ENABLED_CHAIN_TYPE } from "@/passkey/constants"
import { ChainType, Encoding, EncodingFormat, PublicKeyType } from "@/passkey/helpers/enums"
import { AuthenticateCredentialData, CredentialResponse, PublicKey } from "@/passkey/helpers/interfaces"
import { getUnifiedAccountAddress } from "@/passkey/helpers/keys"
import { convertBase64URLStringToHex, showErrorMessage, showSuccessMessage } from "@/passkey/helpers/util"
import { authenticateCredential, getCredentialResponse } from "@/passkey/transaction/authenticate"
import { hexToU8a, u8aToHex } from "@polkadot/util"
import { v4 as uuidv4 } from "uuid"

//This is really for the purpose of playwright testing and won't be used in prod
export const fundUserAccount = async (fundAmount: number, frequencyAddress: string, origin: string) => {
  try {
    const challenge = { encoding: Encoding.BASE16, encodedValue: uuidv4() }
    const credential: AuthenticateCredentialData = await authenticateCredential(origin, challenge)
    const credentialId = credential.credentialId
    const credentialResponse: CredentialResponse = await getCredentialResponse(credentialId, origin)
    const accountPublicKey = credentialResponse.accountPublicKey.encodedValue
    const accountPublicKeyHex = convertBase64URLStringToHex(accountPublicKey)

    let passkeyWalletClient: PasskeyWalletClient
    switch (ENABLED_CHAIN_TYPE) {
      case ChainType.POLKADOT:
        passkeyWalletClient = new PolkadotPasskeyWalletClient(frequencyAddress)
        await passkeyWalletClient.init()
        break
      case ChainType.ETHEREUM:
        passkeyWalletClient = new EthereumPasskeyWalletClient(frequencyAddress)
        await passkeyWalletClient.init()
    }

    await sendFundsToAccountFromAlice(accountPublicKeyHex, fundAmount, frequencyAddress)
    showSuccessMessage(`Funding from alice succeeded, user balance increased by ${fundAmount}`)
  } catch (e: unknown) {
    if (e instanceof Error && e.stack) {
      console.error(JSON.stringify(e.stack))
    } else {
      console.error("Caught an unknown error:", JSON.stringify(e))
    }
    showErrorMessage('Account funding failed, please press "Fund User Account" button to try again')
  }
}

//This is solely for playwright testing purposes and has nothing to do with a prod implementation
const sendFundsToAccountFromAlice = async (
  receiverAccountPublicKey: string,
  fundAmount: number,
  frequencyAddress: string,
) => {
  const testPasskeyClient = new TestPolkadotPasskeyWalletClient(frequencyAddress)
  await testPasskeyClient.init()

  const accountPublicKeyBytes = hexToU8a(receiverAccountPublicKey)
  const unifiedAccountPublicKeyBytes = getUnifiedAccountAddress(accountPublicKeyBytes)
  const unifiedAccountPublicKey: PublicKey = {
    encodedValue: u8aToHex(unifiedAccountPublicKeyBytes),
    encoding: Encoding.BASE16,
    format: EncodingFormat.COMPRESSED_HEX,
    type: PublicKeyType.SECP256K1,
  }

  try {
    return await testPasskeyClient.transferAccountBalanceFromAlice(unifiedAccountPublicKey, fundAmount)
  } catch (err) {
    console.error(JSON.stringify(err))
    throw err
  }
}
