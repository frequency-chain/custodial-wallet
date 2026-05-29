import { api } from "$lib/api"
import { ClientError, ClientErrorType } from "$lib/api/error"
import { AsyncSubmissionResponse } from "$lib/api/schemas/siwa/AsyncSubmissionResponse"
import { ErrorData } from "$lib/types/data/ErrorData"
import { SiwaSubmissionStatus, SiwaSubmissionStatusType } from "$lib/types/data/SiwaSubmissionStatus"
import { GENERIC_ERROR, getApiErrorIdMessages, getApiErrorMessages } from "$lib/utils/errors"
import { type Readable, readonly, writable } from "svelte/store"

export interface SiwaSubmissionServiceOptions {
  pollingDelayMillis: number
}

export interface SiwaSubmissionService {
  submissionStatus: Readable<SiwaSubmissionStatus>
  startPolling: () => Promise<void>
}

export const createSiwaSubmissionService = (
  options: SiwaSubmissionServiceOptions,
  submissionId: string,
  redirectUrl: string,
): SiwaSubmissionService => {
  const submissionStatusStore = writable<SiwaSubmissionStatus>({ type: SiwaSubmissionStatusType.SUBMITTED })

  const pollSubmission = async () => {
    const result = await api.siwa.getAsyncSubmission(submissionId)

    const continuePolling = result.match(
      (submission: AsyncSubmissionResponse) => {
        switch (submission.status) {
          case "SUBMITTED": {
            return true
          }

          case "FAILED": {
            let errorData: ErrorData
            if (submission.error !== undefined) {
              errorData = getApiErrorIdMessages(submission.error.id)
            } else {
              console.error("An `AsyncSubmissionResponse` with `status`=FAILED did not provide an `error`")
              errorData = GENERIC_ERROR
            }

            submissionStatusStore.set({ type: SiwaSubmissionStatusType.FAILED, errorData })
            return false
          }

          case "SUCCESS": {
            submissionStatusStore.set({ type: SiwaSubmissionStatusType.SUCCESS, redirectUrl })
            return false
          }
        }
      },
      // TODO(FI-23): Improve network / client error UX
      (error: ClientError) => {
        console.error({ error })

        const errorData = error.type === ClientErrorType.API ? getApiErrorMessages(error) : GENERIC_ERROR
        submissionStatusStore.set({ type: SiwaSubmissionStatusType.UNKNOWN, errorData })

        // Keep polling and hope the issue resolves on retry
        return true
      },
    )

    if (continuePolling) {
      setTimeout(pollSubmission, options.pollingDelayMillis)
    }
  }

  return {
    startPolling: pollSubmission,
    submissionStatus: readonly(submissionStatusStore),
  }
}
