import { z } from "zod"
import { UserDetailTypeSchema } from "./UserDetailType"

export const UserDetailSchema = z.object({
  value: z.string(),
  type: UserDetailTypeSchema,
  priority: z.number(),
})

export type UserDetail = z.infer<typeof UserDetailSchema>
