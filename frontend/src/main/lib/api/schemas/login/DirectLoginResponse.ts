import { z } from "zod"

export const DirectLoginResponseSchema = z.boolean()

export type DirectLoginResponse = z.infer<typeof DirectLoginResponseSchema>
