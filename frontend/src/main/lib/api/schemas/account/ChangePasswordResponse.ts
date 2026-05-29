import { z } from "zod"

export const ChangePasswordResponseSchema = z.object({
  response: z.boolean(),
})

export type ChangePasswordResponse = z.infer<typeof ChangePasswordResponseSchema>
