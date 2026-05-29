import "@testing-library/jest-dom/vitest"
import { render, screen } from "@testing-library/svelte"
import userEvent from "@testing-library/user-event"
import { afterEach, beforeEach, describe, expect, it } from "vitest"
import TestModal from "./TestModal.svelte"

describe("Modal", async () => {
  let user: ReturnType<typeof userEvent.setup>

  beforeEach(() => {
    user = userEvent.setup()
    render(TestModal)
  })

  afterEach(() => {})

  const openModal = async () => {
    await user.click(screen.getByTestId("open-button"))
    expect(screen.getByRole("dialog")).toBeVisible()
  }

  it("opens", async () => {
    await openModal()
  })

  it("closes on clicking X", async () => {
    await openModal()
    // Finds the button by its accessible name
    await user.click(screen.getByLabelText("Close"))
    expect(screen.queryByRole("dialog")).toBeNull()
  })

  it("closes on clicking the child button", async () => {
    await openModal()
    await user.click(screen.getByTestId("close-button"))
    expect(screen.queryByRole("dialog")).toBeNull()
  })
})
