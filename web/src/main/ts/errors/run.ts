import { ApplicationErrorId } from "@/errors/ApplicationErrorId"
import { ErrorType, RuntimeError } from "@/errors/RuntimeError"

const isRuntimeError = (e: unknown): e is RuntimeError => {
  return (
    typeof e === "object" && e !== null && "type" in e && Object.values(ErrorType).includes((e as RuntimeError).type)
  )
}

const hasMessage = (e: unknown): e is { message: string } => {
  return e !== null && typeof e === "object" && "message" in e && typeof e.message === "string"
}

const coerceRuntimeError = (e: RuntimeError | unknown): RuntimeError => {
  if (isRuntimeError(e)) {
    return e
  }

  return {
    type: ErrorType.Application,
    id: ApplicationErrorId.Unknown,
    description: hasMessage(e) ? e.message : `An unknown error occurred: ${JSON.stringify(e)}`,
  }
}

// Executes a function that might throw and dispatches any errors that are thrown to the
// error handler
export const runCatching = (fallibleFunction: () => void, errorHandler: (runtimeError: RuntimeError) => void) => {
  try {
    fallibleFunction()
  } catch (e) {
    errorHandler(coerceRuntimeError(e))
  }
}

// Executes an asynchronous function that might throw and dispatches any errors to the
// error handler
export const runCatchingAsync = async (
  fallibleFunction: () => Promise<void>,
  errorHandler: (runtimeError: RuntimeError) => void,
) => {
  try {
    await fallibleFunction()
  } catch (e) {
    errorHandler(coerceRuntimeError(e))
  }
}
