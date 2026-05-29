import HamburgerButton from "$components/atoms/HamburgerButton.svelte"
import "@testing-library/jest-dom/vitest"
import { render, screen } from "@testing-library/svelte"
import userEvent from "@testing-library/user-event"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

describe("HamburgerButton", async () => {
  let user: ReturnType<typeof userEvent.setup>

  const mockOnClick = vi.fn()

  beforeEach(() => {
    user = userEvent.setup()
    render(HamburgerButton, { props: { onClick: mockOnClick, "data-testid": "test-open-close", isOpen: false } })
  })

  afterEach(() => {})

  it("isClicked", async () => {
    await user.click(screen.getByTestId("test-open-close"))
    expect(mockOnClick).toHaveBeenCalledTimes(1)
    await user.click(screen.getByTestId("test-open-close"))
    expect(mockOnClick).toHaveBeenCalledTimes(2)
  })
})
