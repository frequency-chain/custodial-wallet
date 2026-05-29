import { ApiError } from "$lib/api/error"
import { ErrorData } from "$lib/types/data/ErrorData"

export const GENERIC_ERROR: ErrorData = { title: "error.generic.title", description: "error.generic.desc" }

export const getApiErrorMessages = (error: ApiError): ErrorData => {
  return getApiErrorIdMessages(error.id)
}

/**
 * Pulls error titles and desc from messages. Add more errors as needed
 */
export const getApiErrorIdMessages = (apiErrorId: number): ErrorData => {
  switch (apiErrorId) {
    case 0:
      return { title: "error.internal.title", description: "error.internal.desc" }
    case 2:
    case 3:
    case 4:
      return { title: "error.session-expired.title", description: "error.session-expired.desc" }
    case 5:
      return { title: "error.not-signed-up.title", description: "error.not-signed-up.desc" }
    case 18:
    case 19:
    case 54:
      return { title: "error.invalid-token.title", description: "error.invalid-token.desc" }
    case 20:
    case 21:
      return { title: "error.resend-limit.title", description: "error.resend-limit.desc" }
    case 22:
      return { title: "error.invalid-email.title", description: "error.invalid-email.desc" }
    case 23:
      return { title: "error.identifier-in-use.title", description: "error.identifier-in-use.desc" }
    case 26:
      return { title: "error.invalid-phone-number.title", description: "error.invalid-phone-number.desc" }
    case 33:
      return { title: "error.website-session-not-found.title", description: "error.website-session-not-found.desc" }
    case 102:
      return { title: "error.handle-change-too-soon.title", description: "error.handle-change-too-soon.desc" }
    default:
      return GENERIC_ERROR
  }
}

export const HANDLE_VALIDATION_ERRORS = new Set([49, 53]) // Invalid handle or suffixes exhausted

export const getHandleValidationMessage = (error: ApiError): string => {
  switch (error.id) {
    case 53:
      return "error.unavailable-handle"
    case 49:
    default:
      return "error.invalid-handle"
  }
}
