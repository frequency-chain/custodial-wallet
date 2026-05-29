import "@testing-library/jest-dom/vitest"
import { render, screen } from "@testing-library/svelte"
import userEvent from "@testing-library/user-event"
import { beforeEach, describe, expect, it } from "vitest"

import TestContactVerification from "./TestContactVerification.svelte"

describe("Login", async () => {
  let user: ReturnType<typeof userEvent.setup>

  beforeEach(() => {
    user = userEvent.setup()
    render(TestContactVerification)
  })

  async function givenAUserWantsToLoginWithSMS() {
    //Choose SMS entry
    const smsButton = screen.getByTestId("switch-button")
    await user.click(smsButton)

    const phoneForm = screen.getByTestId("phone-form")
    await user.click(phoneForm)
    await user.keyboard("3108675309")
    const contactVerificationSubmit = screen.getByTestId("contact-verification-submit")
    await user.click(contactVerificationSubmit)
  }

  async function whenTheUserSubmitsTheOTP() {
    const form = screen.getByTestId("sms-code-submit-form")
    form.addEventListener("submit", (event) => {
      const defaultPrevented = event.defaultPrevented
      console.log(`event.defaultPrevented=${defaultPrevented}`)
      expect(defaultPrevented).toBeTruthy()
    })
    const smsCodeInput = screen.getByTestId("contact-verification-input-sms-code")
    await user.click(smsCodeInput)
    await user.keyboard("111111[Enter]")
  }

  it("Only Submits to the API", async () => {
    await givenAUserWantsToLoginWithSMS()

    await whenTheUserSubmitsTheOTP()

    //THEN
    //it does not explode
  })
})
