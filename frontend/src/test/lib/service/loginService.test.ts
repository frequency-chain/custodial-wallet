import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

// See: https://vitest.dev/guide/mocking.html#modules
vi.mock(import("$lib/api"), async (importOriginal) => {
  const actual = await importOriginal() // type is inferred
  return {
    ...actual, // Keep everything else from the real module
    api: {
      ...actual.api,
      login: {
        ...actual.api.login,
        loginDirect: vi.fn(),
        loginAuthenticate: vi.fn(),
      },
    },
  }
})

import { ContactMethodType } from "$components/enums/ContactMethodType"
import { FormActionType } from "$components/enums/FormActionType"
import { api } from "$lib/api"
import { apiErrorOf } from "$lib/api/helpers"
import { createLoginService } from "$lib/services/loginService"
import { getApiErrorMessages } from "$lib/utils/errors"
import { errAsync, ok } from "neverthrow"

describe("LoginService", async () => {
  beforeEach(() => {})

  afterEach(() => {
    vi.resetAllMocks()
  })

  describe("Login Direct Email", async () => {
    const emailContact = "sampleemail@email.com"
    const callbackUrl = undefined
    const captchaToken = undefined
    const contactMethod = emailContact
    const contactMethodType = "EMAIL"
    const request = { callbackUrl, captchaToken, contactMethodType, contactMethod }

    it("logs in correctly with email", async () => {
      // GIVEN
      const mockApiResponse = Promise.resolve(ok(true))
      vi.mocked(api.login.loginDirect).mockReturnValue(mockApiResponse)

      const service = createLoginService()

      // WHEN
      const result = await service.serviceCalls.initiateOrResendContactVerification(
        { type: ContactMethodType.EMAIL, value: emailContact },
        undefined,
      )

      // THEN
      expect(result.isOk()).toBe(true)
      // API endpoint was invoked correctly
      expect(api.login.loginDirect).toHaveBeenCalledExactlyOnceWith(request)
    })

    it("handles api error gracefully", async () => {
      // GIVEN
      const apiErrorResponse = { id: 4, description: "Oops!", stackTrace: null }
      const status = 404
      const errorData = getApiErrorMessages(apiErrorOf(apiErrorResponse, status))
      const mockApiResponse = Promise.resolve(errAsync(errorData))
      vi.mocked(api.login.loginDirect).mockReturnValue(mockApiResponse)

      const service = createLoginService()

      // WHEN
      const result = await service.serviceCalls.initiateOrResendContactVerification(
        { type: ContactMethodType.EMAIL, value: emailContact },
        undefined,
      )

      // THEN
      expect(result.isOk()).toBe(false)
      // API endpoint was invoked correctly
      expect(api.login.loginDirect).toHaveBeenCalledExactlyOnceWith(request)
    })
  })

  describe("Login Direct Sms", async () => {
    const smsContact = "+16268586456"
    const callbackUrl = undefined
    const captchaToken = undefined
    const contactMethod = smsContact
    const contactMethodType = "PHONE_NUMBER"
    const request = { callbackUrl, captchaToken, contactMethodType, contactMethod }

    it("logs in correctly with sms", async () => {
      // GIVEN
      const mockApiResponse = Promise.resolve(ok(true))
      vi.mocked(api.login.loginDirect).mockReturnValue(mockApiResponse)

      const service = createLoginService()

      // WHEN
      const result = await service.serviceCalls.initiateOrResendContactVerification(
        { type: ContactMethodType.PHONE_NUMBER, value: smsContact },
        undefined,
      )

      // THEN
      expect(result.isOk()).toBe(true)
      // API endpoint was invoked correctly
      expect(api.login.loginDirect).toHaveBeenCalledExactlyOnceWith(request)
    })

    it("handles api error gracefully", async () => {
      // GIVEN
      const apiErrorResponse = { id: 4, description: "Oops!", stackTrace: null }
      const status = 404
      const errorData = getApiErrorMessages(apiErrorOf(apiErrorResponse, status))
      const mockApiResponse = Promise.resolve(errAsync(errorData))
      vi.mocked(api.login.loginDirect).mockReturnValue(mockApiResponse)

      const service = createLoginService()

      // WHEN
      const result = await service.serviceCalls.initiateOrResendContactVerification(
        { type: ContactMethodType.PHONE_NUMBER, value: smsContact },
        undefined,
      )

      // THEN
      expect(result.isOk()).toBe(false)
      // API endpoint was invoked correctly
      expect(api.login.loginDirect).toHaveBeenCalledExactlyOnceWith(request)
    })
  })

  describe("Login Authenticate", async () => {
    const authenticationCode = "826486"
    const sessionId = "sessionid"
    const request = { authenticationCode, sessionId }

    it("authenticates login", async () => {
      // GIVEN
      const mockApiResponse = Promise.resolve(ok("a8098c1a-f86e-11da-bd1a-00112444be1e"))
      vi.mocked(api.login.loginAuthenticate).mockReturnValue(mockApiResponse)
      const service = createLoginService()
      let result

      // WHEN
      if (service.serviceCalls.submitSmsVerificationCode.type === FormActionType.API_CALL) {
        result = await service.serviceCalls.submitSmsVerificationCode.call({ authenticationCode, sessionId })
      }

      // THEN
      expect(result!.isOk()).toBe(true)
      // API endpoint was invoked correctly
      expect(api.login.loginAuthenticate).toHaveBeenCalledExactlyOnceWith(request)
    })

    it("handles api error gracefully", async () => {
      // GIVEN
      const apiErrorResponse = { id: 4, description: "Oops!", stackTrace: null }
      const status = 404
      const errorData = getApiErrorMessages(apiErrorOf(apiErrorResponse, status))
      const mockApiResponse = Promise.resolve(errAsync(errorData))
      vi.mocked(api.login.loginAuthenticate).mockReturnValue(mockApiResponse)

      const service = createLoginService()

      let result

      // WHEN
      if (service.serviceCalls.submitSmsVerificationCode.type === FormActionType.API_CALL) {
        result = await service.serviceCalls.submitSmsVerificationCode.call({ authenticationCode, sessionId })
      }

      // THEN
      expect(result!.isOk()).toBe(false)
      // API endpoint was invoked correctly
      expect(api.login.loginAuthenticate).toHaveBeenCalledExactlyOnceWith(request)
    })
  })
})
