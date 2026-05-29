import { z } from "zod"
import { UserDetailSchema } from "./UserDetail"

export const ProviderUserInfoSchema = z.object({
  userAccountId: z.number(), // bigint
  publicKeyHex: z.string(), // User's public key
  providerMsaId: z.number(), // bigint
  providerExternalId: z.string(),
  providerExternalUserDetailList: z.array(UserDetailSchema),
  providerName: z.string(),
  userHandle: z.string(),
  userMsaId: z.number(), // bigint
  permissions: z.array(z.string()),
  providerExternalUserId: z.number(), // bigint
  hasPassword: z.boolean(),
})

export type ProviderUserInfo = z.infer<typeof ProviderUserInfoSchema>
