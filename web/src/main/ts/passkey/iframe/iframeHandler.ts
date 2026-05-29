import { IFRAME_ERROR_HANDLER, runCatchingAsync } from "@/errors"
import {
  FinishRecoveryMessage,
  FinishRegistrationMessage,
  iFrameMessage,
  IFrameMessageType,
} from "@/passkey/helpers/IFrameMessages"
import { Environment, PublicKey } from "@/passkey/helpers/interfaces"
import {
  checkAndStoreSeedPhrase,
  generateAndReturnPublicKey,
  generateSeedPhrase,
  signPasskeyPublicKey,
} from "@/passkey/registration/walletCreation"
import { HexString } from "@frequency-chain/ethereum-utils"
import { getElementByIdOrThrow } from "../helpers/document"

let _environment: Environment

// Call onload
export async function startPasskeyWalletCreationFlow(isPasskeyWalletRecovery: boolean, environment: Environment) {
  _environment = environment
  // Called onload of main screen
  if (isPasskeyWalletRecovery) {
    displaySeedPhraseInput()
  } else {
    await displaySeedPhrase()
  }

  await parentMessageHandlers()
}

const displaySeedPhrase = async () => {
  window.addEventListener("load", async () =>
    runCatchingAsync(async () => {
      //show div that contains seed phrase
      const seedPhraseDisplay = getElementByIdOrThrow("seedPhraseDisplay")
      seedPhraseDisplay.removeAttribute("hidden")
      //generate seed phrase
      const seedPhrase = await generateSeedPhrase()
      const seedPhraseList = seedPhrase.split(" ")
      //display seed phrase to user
      const seedPhraseDisplayList = getElementByIdOrThrow("seedPhraseList")
      seedPhraseList.forEach((word) => {
        seedPhraseDisplayList.innerHTML += `<li>${word}</li>`
      })
    }, IFRAME_ERROR_HANDLER),
  )
}

const displaySeedPhraseInput = () => {
  window.addEventListener("load", async () =>
    runCatchingAsync(async () => {
      const walletRecoveryInput = getElementByIdOrThrow("enterWalletRecovery")
      walletRecoveryInput.removeAttribute("hidden")
    }, IFRAME_ERROR_HANDLER),
  )
}

const sendAccountPublicKeyPostMessage = async () => {
  const accountPublicKeyHex: string = await generateAndReturnPublicKey()
  const message = {
    type: IFrameMessageType.AccountPublicKey,
    data: accountPublicKeyHex,
  }
  const host = `${$(location).attr("protocol")}//${$(location).attr("host")}`
  parent.postMessage(message, host)
}

const signAndReturnPasskeyPublicKey = async (passkeyPublicKey: PublicKey) => {
  const passkeyPublicKeyHex = passkeyPublicKey.encodedValue
  const credentialPublicKeySignature = await signPasskeyPublicKey(passkeyPublicKeyHex, _environment)
  const returnMessage: FinishRegistrationMessage = {
    type: IFrameMessageType.FinishRegistration,
    data: credentialPublicKeySignature,
  }
  const host = `${$(location).attr("protocol")}//${$(location).attr("host")}`
  parent.postMessage(returnMessage, host)
}

const validateRecoverySeedPhrase = async (publicKeyHex: HexString) => {
  const textarea = getElementByIdOrThrow("walletRecoveryInput") as HTMLTextAreaElement
  const seedPhrase = textarea.value.trim()
  const publicKeyMatch = await checkAndStoreSeedPhrase(seedPhrase, publicKeyHex)
  const returnMessage: FinishRecoveryMessage = {
    type: IFrameMessageType.FinishRecovery,
    data: publicKeyMatch,
  }
  const host = `${$(location).attr("protocol")}//${$(location).attr("host")}`
  parent.postMessage(returnMessage, host)
}

const parentMessageHandlers = async () => {
  window.addEventListener("message", async (event) =>
    runCatchingAsync(async () => {
      event.preventDefault()
      const message: iFrameMessage = event.data
      if (event.origin == `${$(location).attr("protocol")}//${$(location).attr("host")}`) {
        switch (message.type) {
          case IFrameMessageType.StartRegistration:
            await sendAccountPublicKeyPostMessage()
            return

          case IFrameMessageType.SignPasskeyPublicKey:
            await signAndReturnPasskeyPublicKey(message.data)
            return

          case IFrameMessageType.StartRecovery:
            await validateRecoverySeedPhrase(message.data)
            return

          case IFrameMessageType.Error:
            throw message.error

          default:
            console.warn("Received malformed message from parent:", message)
        }
      } else {
        console.warn("Origin of message not from parent")
      }
    }, IFRAME_ERROR_HANDLER),
  )
}
