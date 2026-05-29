import { z } from "zod"

export const RevokeDelegationResponseSchema = z.boolean()

export type RevokeDelegationResponse = z.infer<typeof RevokeDelegationResponseSchema>
