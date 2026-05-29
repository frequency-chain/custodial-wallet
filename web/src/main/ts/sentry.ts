import * as Sentry from "@sentry/browser"
import { ErrorType, RuntimeError } from "./errors"
import { assertTypesExhausted } from "./passkey/helpers/util"

const DSN = {
  amplicaAccess: "https://22a7c0761beddc5a66db82936d7f4c9d@o4504725604401152.ingest.us.sentry.io/4506440927739904",
}

const is4xxRange = (httpStatusCode: number) => {
  // Function is called from JS so try to fix type mismatches
  if (typeof httpStatusCode != "number") {
    httpStatusCode = Number(httpStatusCode)
  }
  return httpStatusCode >= 400 && httpStatusCode < 500
}

export const configure = (sentryEnv: string, sentryRelease: string) => {
  if (sentryEnv === undefined || sentryEnv === null || sentryEnv.trim().length === 0) {
    console.error("'sentryEnv' not defined!")
  }

  if (sentryRelease === undefined || sentryRelease === null || sentryRelease.trim().length === 0) {
    console.error("'sentryRelease' not defined!")
  }

  Sentry.init({
    dsn: DSN.amplicaAccess,
    environment: sentryEnv,
    // Set to true if needing debug messages
    debug: false,
    // Change value if wanting more/less breadcrumbs. Default is 100.
    maxBreadcrumbs: 50,
    release: sentryRelease,
  })
}

export const captureXhr = <T>(xhr: JQuery.jqXHR<T>, error: Error | string) => {
  if (!is4xxRange(xhr.status)) {
    // 5xx stuff should be sent, ignoring client errors (4xx) because they could be bad user input
    if (error instanceof Error) {
      Sentry.captureException(error)
    } else {
      Sentry.captureException(new Error(error))
    }
  }
}

export const captureMessage = (message: string | object) => {
  Sentry.captureMessage(JSON.stringify(message))
}

export const captureException = Sentry.captureException

const getSentryName = (error: RuntimeError): string => {
  switch (error.type) {
    case ErrorType.Api:
      return `${ErrorType.Api} ${error.apiErrorId}`

    case ErrorType.Application:
      return `${ErrorType.Application} ${error.id}`

    case ErrorType.Frequency:
      return `${ErrorType.Frequency} ${error.frequencyErrorId || error.description}`

    default:
      return assertTypesExhausted(error)
  }
}

// Create an `Error` instance to make sentry happy
const simulateJsException = (error: RuntimeError): Error => {
  const nativeError = new Error(error.description)
  nativeError.name = getSentryName(error)
  nativeError.stack =
    error.type === ErrorType.Api || error.type === ErrorType.Application ? error.stacktrace : undefined

  return nativeError
}

export const captureRuntimeError = (error: RuntimeError) => {
  // Ignore 4XX api errors
  if (error.type === ErrorType.Api && is4xxRange(error.status)) {
    return
  }

  Sentry.setExtras(error)
  Sentry.captureException(simulateJsException(error))
}
