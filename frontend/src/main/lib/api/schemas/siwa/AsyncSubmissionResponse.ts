import { ApiErrorResponseSchema } from "$lib/api/schemas/error/ApiErrorResponse"
import { z } from "zod"

export const AsyncSubmissionResponseSchema = z.object({
  id: z.string(),
  status: z.enum(["SUBMITTED", "SUCCESS", "FAILED"]),
  error: z.optional(ApiErrorResponseSchema),
})

export type AsyncSubmissionResponse = z.infer<typeof AsyncSubmissionResponseSchema>
