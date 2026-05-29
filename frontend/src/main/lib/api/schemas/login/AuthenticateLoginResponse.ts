import { z } from "zod"

export const AuthenticateLoginResponseSchema = z.object({
  sessionId: z.string().uuid(),
})

export type AuthenticateLoginResponse = z.infer<typeof AuthenticateLoginResponseSchema>
