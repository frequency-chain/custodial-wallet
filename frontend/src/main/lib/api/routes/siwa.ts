import { safeFetch } from "$lib/api/helpers"

import { ClientError } from "$lib/api/error"
import { AsyncSubmissionResponse, AsyncSubmissionResponseSchema } from "$lib/api/schemas/siwa/AsyncSubmissionResponse"
import { Result } from "neverthrow"

const path = "/siwa/api"

const getAsyncSubmission = async (submissionId: string): Promise<Result<AsyncSubmissionResponse, ClientError>> => {
  const endpoint = "submission"

  return safeFetch(`${path}/${endpoint}/${submissionId}`, AsyncSubmissionResponseSchema, { method: "GET" })
}

export const siwa = {
  getAsyncSubmission,
}
