import { z } from "zod"

export const LocalizedMessagesSchema = z.record(z.string(), z.string())

export type LocalizedMessages = z.infer<typeof LocalizedMessagesSchema>
