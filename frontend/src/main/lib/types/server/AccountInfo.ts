import { z } from "zod"
import { ProviderUserInfoSchema } from "./ProviderUserInfo"
import { UserDetailSchema } from "./UserDetail"

export const AccountInfoSchema = z.object({
  userDetails: z.array(UserDetailSchema),
  providerUserInfo: z.array(ProviderUserInfoSchema),
  hasPasskeyWallet: z.boolean(),
})

export type AccountInfo = z.infer<typeof AccountInfoSchema>
