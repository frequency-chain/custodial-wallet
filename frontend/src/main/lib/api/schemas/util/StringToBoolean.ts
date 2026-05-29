import { z } from "zod"

// When/If we upgrade to Zod 4, this functionality is included in the Zod4 z.stringbool type for parsing
// this solution was recommended by the thread for people still on zod 3
// https://github.com/colinhacks/zod/issues/2985
export const StringToBoolean = z.union([
  z.literal("true").transform((): boolean => true),
  z.literal("false").transform((): boolean => false),
  z.boolean(),
])
