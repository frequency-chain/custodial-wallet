import { z } from "zod"

export const ApiErrorResponseSchema = z.object({
  id: z.number(),
  description: z.string(),
  stackTrace: z.string().nullable(),
})

export type ApiErrorResponse = z.infer<typeof ApiErrorResponseSchema>
