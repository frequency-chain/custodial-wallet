import { LocalizedMessages } from "$lib/types/server/LocalizedMessages"
import { Readable, readable } from "svelte/store"

export type I18nGetter = (key: string, values?: Array<string>) => string

// 'Curried' function that first accepts the map of localized messages, and can then be invoked to get entries
export const createI18nGetter =
  (messages: Record<string, string>): I18nGetter =>
  (key: string, values = undefined): string => {
    const message = messages[key]
    if (message !== undefined) {
      if (values !== undefined) {
        return values.reduce((acc, value, index) => {
          return acc.replace(`{${index}}`, value)
        }, message)
      } else {
        return message
      }
    } else {
      console.error(`Unable to find a localized message for: '${key}'`)
      return key
    }
  }

export const initI18n = (messages: LocalizedMessages): Readable<I18nGetter> => {
  const { ...entries } = messages // Make a copy of the messages record
  const store = readable(createI18nGetter({ ...entries }))

  return {
    subscribe: store.subscribe,
  }
}
