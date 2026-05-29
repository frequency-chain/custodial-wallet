import { ApplicationErrorId, ErrorType } from "@/errors"

export type HttpMethod = "POST" | "PUT" | "GET" | "DELETE"

interface ApiErrorBody {
  id: number
  description: string
  stackTrace?: string
}

export const performRequest = <T>(url: string, method: HttpMethod, body?: object): Promise<T> => {
  // eslint-disable-next-line no-async-promise-executor
  return new Promise(async (resolve, reject) => {
    let data: string | undefined
    try {
      data = body !== undefined ? JSON.stringify(body) : undefined
    } catch (e) {
      reject({
        type: ErrorType.Application,
        id: ApplicationErrorId.Unknown,
        description: `Failed to serialize body for '${method}' to ${url}. Reason: ${e.message}`,
        stacktrace: e.stack,
      })
      return
    }

    await $.ajax({
      url,
      method,
      data,
      contentType: "application/json; charset=utf-8",
      timeout: 5000,
      success: (response) => resolve(response),
    }).catch((error) => {
      const hasStatus = error.status !== null && error.status !== undefined && error.status > 0
      const hasResponse = error.responseText !== null && error.responseText !== undefined

      if (hasStatus && hasResponse) {
        let response: ApiErrorBody
        try {
          response = JSON.parse(error.responseText)
        } catch {
          reject({
            type: ErrorType.Application,
            id: ApplicationErrorId.Unknown,
            description: "Unable to parse response from server",
          })
          return
        }

        reject({
          type: ErrorType.Api,
          status: error.status,
          apiErrorId: response.id,
          description: response.description,
          serverStacktrace: response.stackTrace,
          stacktrace: new Error().stack,
        })
      } else {
        const description =
          typeof error === "object" && "message" in error
            ? error.message
            : `Unable to complete request: (${method}) ${url}`

        reject({
          type: ErrorType.Application,
          id: ApplicationErrorId.Network,
          description,
        })
      }
    })
  })
}
