import { z } from "zod"

export const UpdateHandleResponseSchema = z.object({
  claimedHandle: z.string(),
})

export type UpdateHandleResponse = z.infer<typeof UpdateHandleResponseSchema>
