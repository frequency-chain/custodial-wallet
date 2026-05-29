import { FrequencyErrorId } from "@/errors/FrequencyError"
import { ApplicationErrorId } from "./ApplicationErrorId"

export type RuntimeError = ApiError | ApplicationError | FrequencyError

export type ApiError = {
  type: ErrorType.Api
  status: number
  apiErrorId: number
  description: string
  serverStacktrace?: string
  stacktrace?: string
}

// Default
export type ApplicationError = {
  type: ErrorType.Application
  id: ApplicationErrorId
  description: string
  stacktrace?: string
}

export type FrequencyError = {
  type: ErrorType.Frequency
  frequencyErrorId: FrequencyErrorId
  description: string
}

export enum ErrorType {
  Api = "ApiError",
  Application = "ApplicationError",
  Frequency = "FrequencyError",
}

export type ErrorHandler = (error: RuntimeError) => void
