import { createI18nGetter } from "$lib/i18n"
import { describe, expect, it } from "vitest"

describe("createI18nGetter", async () => {
  it("grabs correct message", async () => {
    // GIVEN
    const key = "test.foobar"
    const message = "Foo Bar"
    const messages = {
      [key]: message,
    }
    const i18n = createI18nGetter(messages)

    // WHEN
    const result = i18n(key)

    // THEN
    expect(result).toBe(message)
  })

  it("falls back to the key when not defined", async () => {
    // GIVEN
    const key = "fake.key"
    const i18n = createI18nGetter({})

    // WHEN
    const result = i18n(key)

    // THEN
    expect(result).toBe(key)
  })

  it("fills a single placeholder correctly", async () => {
    // GIVEN
    const key = "test.foobar"
    const message = "Foo Bar {0}"
    const messages = {
      [key]: message,
    }
    const i18n = createI18nGetter(messages)

    // WHEN
    const result = i18n(key, ["Baz"])

    // THEN
    expect(result).toBe("Foo Bar Baz")
  })

  it("fills multiple placeholders correctly", async () => {
    // GIVEN
    const key = "test.foobar"
    const message = "Foo Bar {1} {0}"
    const messages = {
      [key]: message,
    }
    const i18n = createI18nGetter(messages)

    // WHEN
    const result = i18n(key, ["Buzz", "Baz"])

    // THEN
    expect(result).toBe("Foo Bar Baz Buzz")
  })
})
