import { SessionData } from "$lib/types/data/SessionData"
import { Readable, readonly, writable } from "svelte/store"

export interface SessionService {
  data: Readable<SessionData>
}

export const createSessionService = (sessionData: SessionData): SessionService => {
  const dataStore = writable<SessionData>(sessionData)

  return {
    data: readonly(dataStore),
  }
}
