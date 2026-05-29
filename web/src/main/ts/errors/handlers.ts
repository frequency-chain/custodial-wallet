import { ErrorHandler, RuntimeError } from "@/errors/RuntimeError"
import { ErrorMessage, IFrameMessageType } from "@/passkey/helpers/IFrameMessages"
import { showErrorMessage } from "@/passkey/helpers/util"
import * as SENTRY from "@/sentry"

export const aggregate = (handlers: Array<ErrorHandler>): ErrorHandler => {
  return (error: RuntimeError) => {
    handlers.map((it) => it(error))
  }
}

export const LOGGING_ERROR_HANDLER = (tag: string) => (error: RuntimeError) => {
  console.error(`[${tag}] The application encountered an error: ${error.description} ${JSON.stringify(error)}`)
}

export const IFRAME_ERROR_HANDLER = aggregate([
  LOGGING_ERROR_HANDLER("IFRAME"),
  // Forwards errors to the parent page
  (error: RuntimeError) => {
    const message: ErrorMessage = {
      type: IFrameMessageType.Error,
      error,
    }

    parent.postMessage(message)
  },
])

// Used for playwright tests
export const PASSKEY_PLAYWRIGHT_ERROR_HANDLER = aggregate([
  LOGGING_ERROR_HANDLER("PASSKEY_PLAYWRIGHT"),
  (error: RuntimeError) => {
    showErrorMessage(error.description)
  },
])

export const SENTRY_ERROR_HANDLER = SENTRY.captureRuntimeError
