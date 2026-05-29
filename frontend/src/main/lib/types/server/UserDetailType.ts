import { z } from "zod"

export const UserDetailTypeSchema = z.enum(["EMAIL", "PHONE_NUMBER"])

export type UserDetailType = z.infer<typeof UserDetailTypeSchema>
