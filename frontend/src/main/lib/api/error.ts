export enum ClientErrorType {
  UNKNOWN = "UnknownError",
  API = "ApiError",
}

export interface UnknownError {
  type: ClientErrorType.UNKNOWN
  message: string
}

export interface ApiError {
  type: ClientErrorType.API
  id: number
  status: number
  message: string
}

export type ClientError = UnknownError | ApiError
