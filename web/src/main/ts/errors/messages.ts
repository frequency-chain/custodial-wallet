import { assertTypesExhausted } from "@/passkey/helpers/util"
import { ApplicationErrorId } from "./ApplicationErrorId"
import { ErrorType, RuntimeError } from "./RuntimeError"

export const getRuntimeErrorMessages = (error: RuntimeError, messages: { [k: string]: string }): [string, string] => {
  switch (error.type) {
    case ErrorType.Application:
      return getApplicationErrorMessages(error.id, messages)

    case ErrorType.Api:
      return getApiErrorMessages(error.apiErrorId, messages)

    case ErrorType.Frequency:
      return getFrequencyErrorMessages(error.frequencyErrorId, messages)

    default:
      return assertTypesExhausted(error)
  }
}

export const getApplicationErrorMessages = (
  id: ApplicationErrorId,
  messages: { [k: string]: string },
): [string, string] => {
  switch (id) {
    case ApplicationErrorId.Unknown:
    case ApplicationErrorId.IFrame:
      return [messages["error.internal.title"], messages["error.internal.desc"]]

    default:
      return [messages["error.generic.title"], messages["error.generic.desc"]]
  }
}

/**
 * Pulls error titles and desc from messages. Add more errors as needed
 * @param apiErrorId
 * @param messages
 * @returns {[string, string]} returns the internationalized title and desc for the error
 */
export const getApiErrorMessages = (apiErrorId: number, messages: { [k: string]: string }): [string, string] => {
  switch (apiErrorId) {
    case 0:
      return [messages["error.internal.title"], messages["error.internal.desc"]]
    case 2:
    case 3:
    case 4:
      return [messages["error.session-expired.title"], messages["error.session-expired.desc"]]
    case 5:
      return [messages["error.not-signed-up.title"], messages["error.not-signed-up.desc"]]
    case 18:
    case 19:
    case 54:
      return [messages["error.invalid-token.title"], messages["error.invalid-token.desc"]]
    case 20:
      return [messages["error.resend-limit.title"], messages["error.resend-limit.desc"]]
    case 21:
      return [messages["error.resend-timer.title"], messages["error.resend-timer.desc"]]
    case 22:
      return [messages["error.invalid-email.title"], messages["error.invalid-email.desc"]]
    case 23:
      return [messages["error.identifier-in-use.title"], messages["error.identifier-in-use.desc"]]
    case 26:
      return [messages["error.invalid-phone-number.title"], messages["error.invalid-phone-number.desc"]]
    case 33:
      return [messages["error.website-session-not-found.title"], messages["error.website-session-not-found.desc"]]
    default:
      return [messages["error.generic.title"], messages["error.generic.desc"]]
  }
}

export const getFrequencyErrorMessages = (
  frequencyErrorId: string,
  messages: { [k: string]: string },
): [string, string] => {
  switch (frequencyErrorId) {
    default:
      return [messages["error.internal.title"], messages["error.internal.desc"]]
  }
}
