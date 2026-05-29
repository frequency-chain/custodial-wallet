import { z } from "zod"

export const VerifyContactResponseSchema = z.object({
  response: z.boolean(),
})

export type VerifyContactResponse = z.infer<typeof VerifyContactResponseSchema>
