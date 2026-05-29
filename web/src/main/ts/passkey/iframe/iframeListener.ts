import { runCatchingAsync, RuntimeError } from "@/errors"
import { performRequest } from "@/passkey/helpers/api"
import { iFrameMessage, IFrameMessageType, StartRecoveryMessage } from "@/passkey/helpers/IFrameMessages"
import { PasskeyData } from "@/passkey/helpers/interfaces"
import { showSuccessMessage } from "@/passkey/helpers/util"
import { HexString } from "@frequency-chain/ethereum-utils"
import { browserSupportsWebAuthn } from "@simplewebauthn/browser"
import { v4 as uuidv4 } from "uuid"
import { getElementByIdOrThrow } from "../helpers/document"
import { createNewPasskey, createPasskeyData } from "../registration/passkeyCreation"

// ---------------------------------------------------------------------------------------
// 'API' used by the parent application
// ---------------------------------------------------------------------------------------

let passkeyUsername: string

//button click event listener
export const startPasskeyRegistrationFlow = async (username: string, errorHandler: (error: RuntimeError) => void) => {
  await runCatchingAsync(
    async () => {
      const today = new Date()

      const dateString = Intl.DateTimeFormat().format(today)
      passkeyUsername = `${username} ${dateString}`

      const message = { type: IFrameMessageType.StartRegistration }
      const getIFrame = <HTMLIFrameElement>getElementByIdOrThrow("secureIFrame")
      getIFrame.contentWindow?.postMessage(message, "*")
    },
    (runtimeError: RuntimeError) => {
      errorHandler(runtimeError)
    },
  )
}

const acceptRegistration = (passkeyData: PasskeyData, serverAddress: string): Promise<void> => {
  return performRequest(`${serverAddress}/api/passkey/registration/accept`, "POST", passkeyData)
}

const handleIFrameMessage = async (
  message: iFrameMessage,
  serverAddress: string,
  recoveryPhraseCallback: (publicKeyMatch: boolean) => void,
  registrationFinishedCallback: () => void,
): Promise<void> => {
  switch (message.type) {
    case IFrameMessageType.FinishRecovery: {
      const publicKeyMatch = message.data
      recoveryPhraseCallback(publicKeyMatch)
      return
    }
    case IFrameMessageType.AccountPublicKey: {
      const accountPublicKeyHex = message.data
      const userId = uuidv4()
      const compressedPasskeyPublicKey = await createNewPasskey(
        serverAddress,
        passkeyUsername,
        userId,
        accountPublicKeyHex,
      )
      const sendMessage = {
        type: IFrameMessageType.SignPasskeyPublicKey,
        data: compressedPasskeyPublicKey,
      }
      const getIFrame = <HTMLIFrameElement>getElementByIdOrThrow("secureIFrame")
      getIFrame.contentWindow?.postMessage(sendMessage, "*")
      return
    }

    case IFrameMessageType.FinishRegistration: {
      const passkeyPublicKeySignature = message.data
      const passkeyData = await createPasskeyData(passkeyPublicKeySignature)
      // Submit the passkey data to the backend for validation / persistence
      await acceptRegistration(passkeyData, serverAddress)

      showSuccessMessage("Passkey creation successful!")
      //call callback with a hidden submit button via js to return final page of passkey registration flow to display to user
      registrationFinishedCallback()
      return
    }

    case IFrameMessageType.Error:
      throw message.error

    default:
      console.warn("Received malformed message from iFrame:", message)
  }
}

// Listens for messages posted by the iFrame and responds accordingly
export function iframeMessageListener(
  serverAddress: string,
  recoveryPhraseCallback: (publicKeyMatch: boolean) => void,
  registrationFinishedCallback: () => void,
  errorHandler: (error: RuntimeError) => void, // Calling page responsible for errors
) {
  window.addEventListener(
    "message",
    async (event) =>
      runCatchingAsync(
        async () => {
          event.preventDefault()
          const message: iFrameMessage = event.data
          await handleIFrameMessage(message, serverAddress, recoveryPhraseCallback, registrationFinishedCallback)
        },
        (runtimeError: RuntimeError) => {
          errorHandler(runtimeError)
        },
      ),
    false,
  )
}

export function arePasskeysSupportedOnDevice() {
  return browserSupportsWebAuthn()
}

export const startRecoveryFlow = async (
  existingAccountPublicKeyHex: string,
  errorHandler: (error: RuntimeError) => void,
) => {
  await runCatchingAsync(
    async () => {
      const message: StartRecoveryMessage = {
        type: IFrameMessageType.StartRecovery,
        data: existingAccountPublicKeyHex as HexString,
      }
      const getIFrame = <HTMLIFrameElement>getElementByIdOrThrow("secureIFrame")
      getIFrame.contentWindow?.postMessage(message, "*")
    },
    (runtimeError: RuntimeError) => {
      errorHandler(runtimeError)
    },
  )
}
