import { api } from "$lib/api"
import { ClientErrorType, UnknownError } from "$lib/api/error"
import { AsyncSubmissionResponse } from "$lib/api/schemas/siwa/AsyncSubmissionResponse"
import { createSiwaSubmissionService, SiwaSubmissionService } from "$lib/services/siwaSubmissionService"
import { SiwaSubmissionStatusType } from "$lib/types/data/SiwaSubmissionStatus"
import { err, ok } from "neverthrow"
import { get } from "svelte/store"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

// 1. Mock the API module
vi.mock(import("$lib/api"), async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    api: {
      ...actual.api,
      siwa: {
        getAsyncSubmission: vi.fn(),
      },
    },
  }
})

describe("SiwaSubmissionService", () => {
  const mockSubmissionId = "1"
  const mockRedirectUrl = "https://cheo.com/app?code=123"
  const pollingDelay = 1000

  const options = { pollingDelayMillis: pollingDelay }

  let service: SiwaSubmissionService

  beforeEach(() => {
    // Mock timing-related functions (incl. `setTimeout`)
    vi.useFakeTimers()

    service = createSiwaSubmissionService(options, mockSubmissionId, mockRedirectUrl)
  })

  afterEach(() => {
    vi.resetAllMocks()
    vi.useRealTimers()
  })

  describe("startPolling", () => {
    it("Continues polling if status is SUBMITTED", async () => {
      // GIVEN
      const mockResponse: AsyncSubmissionResponse = {
        id: mockSubmissionId,
        status: "SUBMITTED",
      }
      vi.mocked(api.siwa.getAsyncSubmission).mockResolvedValue(ok(mockResponse))

      // WHEN
      await service.startPolling()
      await vi.runOnlyPendingTimersAsync()

      // THEN
      const data = get(service.submissionStatus)
      expect(data.type).toBe(SiwaSubmissionStatusType.SUBMITTED)
      expect(api.siwa.getAsyncSubmission).toHaveBeenCalledTimes(2)
    })

    it("Updates store to SUCCESS and stops polling when API returns SUCCESS", async () => {
      // GIVEN
      const mockResponse: AsyncSubmissionResponse = { id: mockSubmissionId, status: "SUCCESS" }
      vi.mocked(api.siwa.getAsyncSubmission).mockResolvedValueOnce(ok(mockResponse))

      // WHEN
      await service.startPolling()
      await vi.runOnlyPendingTimersAsync() // Flush out any `setTimeout` calls (there shouldn't be any)

      // THEN
      const data = get(service.submissionStatus)
      expect(data.type).toBe(SiwaSubmissionStatusType.SUCCESS)
      expect(api.siwa.getAsyncSubmission).toHaveBeenCalledTimes(1)
    })

    it("Updates store to FAILED and stops polling when API returns FAILED", async () => {
      // GIVEN
      const mockResponse: AsyncSubmissionResponse = {
        id: mockSubmissionId,
        status: "FAILED",
        error: { id: 1, description: "Mock API error", stackTrace: null },
      }
      vi.mocked(api.siwa.getAsyncSubmission).mockResolvedValueOnce(ok(mockResponse))

      // WHEN
      await service.startPolling()
      await vi.runOnlyPendingTimersAsync() // Flush out any `setTimeout` calls (there shouldn't be any)

      // THEN
      const data = get(service.submissionStatus)
      expect(data.type).toBe(SiwaSubmissionStatusType.FAILED)
      expect(api.siwa.getAsyncSubmission).toHaveBeenCalledTimes(1)
    })

    it("Updates state but continues polling on client errors", async () => {
      // GIVEN
      const mockResponse: AsyncSubmissionResponse = {
        id: mockSubmissionId,
        status: "SUBMITTED",
      }
      const mockError: UnknownError = {
        type: ClientErrorType.UNKNOWN,
        message: "Network error",
      }

      // First calls responds with SUBMITTED, then every calls results in network error
      vi.mocked(api.siwa.getAsyncSubmission).mockResolvedValueOnce(ok(mockResponse))
      vi.mocked(api.siwa.getAsyncSubmission).mockResolvedValue(err(mockError))

      // WHEN
      await service.startPolling()
      await vi.runOnlyPendingTimersAsync()
      await vi.runOnlyPendingTimersAsync()

      // THEN
      // TODO(FI-23): May need to update ...
      const data = get(service.submissionStatus)
      expect(data.type).toBe(SiwaSubmissionStatusType.UNKNOWN)
      expect(api.siwa.getAsyncSubmission).toHaveBeenCalledTimes(3)
    })

    // TODO(FI-23): More test cases if needed ...
  })
})
