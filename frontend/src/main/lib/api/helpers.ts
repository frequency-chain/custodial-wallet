import { ErrorData } from "$lib/types/data/ErrorData"
import { GENERIC_ERROR, getApiErrorMessages } from "$lib/utils/errors"
import { err, errAsync, ok, okAsync, Result, ResultAsync } from "neverthrow"
import { z, ZodTypeAny } from "zod"
import { ApiError, ClientError, ClientErrorType, UnknownError } from "./error"
import { ApiErrorResponse, ApiErrorResponseSchema } from "./schemas/error/ApiErrorResponse"

export const unknownErrorOf = (message: string): UnknownError => ({
  type: ClientErrorType.UNKNOWN,
  message,
})

export const apiErrorOf = ({ id, description }: ApiErrorResponse, status: number): ApiError => ({
  type: ClientErrorType.API,
  id,
  status,
  message: description,
})

export const safeFetch = <T extends ZodTypeAny>(
  url: string,
  schema: T,
  request?: RequestInit,
): ResultAsync<z.infer<T>, ClientError> => {
  return ResultAsync.fromPromise(fetch(url, request), (error: unknown): ClientError => {
    return unknownErrorOf(`Failed to perform fetch ${url} - Due to error: ${JSON.stringify(error)}`)
  }).andThen((response): ResultAsync<z.infer<T>, ClientError> => {
    return ResultAsync.fromPromise(response.json(), (error: unknown): ClientError => {
      return unknownErrorOf(
        `Failed to get JSON object: [${response.status}] ${url} - Due to error: ${JSON.stringify(error)}`,
      )
    }).andThen((responseJson): ResultAsync<z.infer<T>, ClientError> => {
      if (response.ok) {
        try {
          return okAsync<z.infer<T>, never>(schema.parse(responseJson))
        } catch (error: unknown) {
          return errAsync<never, ClientError>(
            unknownErrorOf(
              `Failed to parse API response: [${response.status}] ${url} - Due to error: ${JSON.stringify(error)}`,
            ),
          )
        }
      } else {
        try {
          const errorBody = ApiErrorResponseSchema.parse(responseJson)
          return errAsync<never, ClientError>(apiErrorOf(errorBody, response.status))
        } catch (error: unknown) {
          return errAsync<never, ClientError>(
            unknownErrorOf(
              `Failed to parse API error response: [${response.status}] ${url} - Due to error: ${JSON.stringify(error)}`,
            ),
          )
        }
      }
    })
  })
}

export interface BooleanHolder {
  response: boolean
}

export const matchBooleanPostResult = (
  result: ResultAsync<boolean, ClientError>,
  errorMessagePrefix: string,
): Promise<Result<void, ErrorData>> => {
  return result.match(
    (booleanResult: boolean): Result<void, ErrorData> => {
      if (booleanResult) {
        return ok()
      }
      throw new Error("Return of false on ok() Result expecting only true")
    },
    (clientError): Result<void, ErrorData> => {
      return err(getErrorData(clientError, errorMessagePrefix))
    },
  )
}

export const matchBooleanHolderPostResult = (
  result: ResultAsync<BooleanHolder, ClientError>,
  errorMessagePrefix: string,
): Promise<Result<void, ErrorData>> => {
  return result.match(
    (booleanHolderResult: BooleanHolder): Result<void, ErrorData> => {
      if (booleanHolderResult.response) {
        return ok()
      }
      throw new Error("Return of false on ok() Result expecting only true")
    },
    (clientError): Result<void, ErrorData> => {
      return err(getErrorData(clientError, errorMessagePrefix))
    },
  )
}

export const unwrapPostResult = <T>(
  result: ResultAsync<T, ClientError>,
  errorMessagePrefix: string,
): Promise<Result<T, ErrorData>> => {
  return result.match(
    (innerResult: T): Result<T, ErrorData> => {
      return ok(innerResult)
    },
    (clientError): Result<T, ErrorData> => {
      return err(getErrorData(clientError, errorMessagePrefix))
    },
  )
}

export const getErrorData = (clientError: ClientError, errorMessagePrefix: string): ErrorData => {
  console.error(`${errorMessagePrefix}: ${JSON.stringify(clientError)}`)
  return clientError.type === ClientErrorType.API ? getApiErrorMessages(clientError) : GENERIC_ERROR
}
