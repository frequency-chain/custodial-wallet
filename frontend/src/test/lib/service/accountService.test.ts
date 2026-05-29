import { ContactMethodType } from "$components/enums/ContactMethodType"
import { api } from "$lib/api"
import { apiErrorOf } from "$lib/api/helpers"
import { ChangePasswordRequest } from "$lib/api/schemas/account/ChangePasswordRequest"
import { createAccountService } from "$lib/services/accountService"
import { AccountData, UserDetailType } from "$lib/types/data/AccountData"
import { getApiErrorMessages } from "$lib/utils/errors"
import { errAsync, ok } from "neverthrow"
import { get } from "svelte/store"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

// See: https://vitest.dev/guide/mocking.html#modules
vi.mock(import("$lib/api"), async (importOriginal) => {
  const actual = await importOriginal() // type is inferred
  return {
    ...actual, // Keep everything else from the real module
    api: {
      ...actual.api,
      account: {
        ...actual.api.account,
        revokeAllDelegations: vi.fn(),
        updateHandle: vi.fn(),
        verifyContactEmail: vi.fn(),
        verifyContactSms: vi.fn(),
        changePassword: vi.fn(),
      },
    },
  }
})

describe("AccountService", async () => {
  beforeEach(() => {})

  afterEach(() => {
    vi.resetAllMocks()
  })

  const initialValue: AccountData = {
    providers: [
      {
        msaId: 3,
        name: "Example Provider",
        permissions: ["permission.account.update-identity", "permission.account.graph"],
      },
    ],
    userDetails: {
      [UserDetailType.EMAIL]: [
        {
          priority: 1,
          type: UserDetailType.EMAIL,
          value: "peter.frank@projectliberty.io",
        },
        {
          priority: 1,
          type: UserDetailType.EMAIL,
          value: "peter.frank+1@projectliberty.io",
        },
      ],
      [UserDetailType.PHONE_NUMBER]: [
        {
          priority: 2,
          type: UserDetailType.PHONE_NUMBER,
          value: "+18055591665",
        },
      ],
    },
    handle: "han-solo.1",
    userPublicKeyHex: "0x1234",
    userAccountId: 1,
    hasPassword: true,
    hasPasskeyWallet: false,
    msaId: 3,
  }

  describe("revokeAllDelegations", async () => {
    const providerMsaId = initialValue.providers[0]!.msaId

    it("revokes correctly", async () => {
      // GIVEN
      const mockApiResponse = Promise.resolve(ok())
      vi.mocked(api.account.revokeAllDelegations).mockReturnValue(mockApiResponse)

      const service = createAccountService(initialValue)

      // WHEN
      const result = await service.revokeProviderDelegations(providerMsaId)

      // THEN
      expect(result.isOk()).toBe(true)
      // API endpoint was invoked correctly
      expect(api.account.revokeAllDelegations).toHaveBeenCalledExactlyOnceWith(providerMsaId)
      // Store has been updated to reflect the change
      expect(get(service.data).providers[0]!.permissions).toHaveLength(0)
    })

    it("handles api error gracefully", async () => {
      // GIVEN
      const apiErrorResponse = { id: 4, description: "Oops!", stackTrace: null }
      const status = 404
      const errorData = getApiErrorMessages(apiErrorOf(apiErrorResponse, status))
      const mockApiResponse = Promise.resolve(errAsync(errorData))
      vi.mocked(api.account.revokeAllDelegations).mockReturnValue(mockApiResponse)

      const service = createAccountService(initialValue)

      // WHEN
      const result = await service.revokeProviderDelegations(providerMsaId)

      // THEN
      expect(result.isOk()).toBe(false)
      // API endpoint was invoked correctly
      expect(api.account.revokeAllDelegations).toHaveBeenCalledExactlyOnceWith(providerMsaId)
      // Store has not been updated
      expect(get(service.data)).toEqual(initialValue)
    })
  })

  describe("change password", async () => {
    const userAccountId = initialValue.userAccountId
    const newRawPassword = "someNewPassword!"
    const request: ChangePasswordRequest = { userAccountId, newRawPassword }

    it("successfully changes user password", async () => {
      // GIVEN
      const mockApiResponse = Promise.resolve(ok())
      vi.mocked(api.account.changePassword).mockReturnValue(mockApiResponse)

      const service = createAccountService(initialValue)

      // WHEN
      const result = await service.changePassword(request)

      // THEN
      expect(result.isOk()).toBe(true)
      // API endpoint was invoked correctly
      expect(api.account.changePassword).toHaveBeenCalledExactlyOnceWith(request)
      // At this point not really sure anything can be tested to on the front end to verify that the password has been changed
    })

    it("handles api error gracefully", async () => {
      // GIVEN
      const apiErrorResponse = { id: 4, description: "Oops!", stackTrace: null }
      const status = 404
      const errorData = getApiErrorMessages(apiErrorOf(apiErrorResponse, status))
      const mockApiResponse = Promise.resolve(errAsync(errorData))
      vi.mocked(api.account.changePassword).mockReturnValue(mockApiResponse)

      const service = createAccountService(initialValue)

      // WHEN
      const result = await service.changePassword(request)

      // THEN
      expect(result.isOk()).toBe(false)
      // API endpoint was invoked correctly
      expect(api.account.changePassword).toHaveBeenCalledExactlyOnceWith(request)
    })
  })

  describe("update handle", async () => {
    const newHandle = "newHandle"
    const request = { newHandle }

    it("successfully updates the user's handle", async () => {
      // GIVEN
      const mockApiResponse = Promise.resolve(ok(ok(newHandle)))
      vi.mocked(api.account.updateHandle).mockReturnValue(mockApiResponse)

      const service = createAccountService(initialValue)

      // WHEN
      const response = await service.updateHandle(request)

      // THEN
      expect(response.isOk()).toBe(true)
      expect(response._unsafeUnwrap().isOk()).toBe(true)
      // API endpoint was invoked correctly
      expect(api.account.updateHandle).toHaveBeenCalledExactlyOnceWith(request)

      expect(get(service.data).handle).toBe(newHandle)
    })
  })

  describe("Verify Contact Email", async () => {
    const contactMethod = "bob@realcompany.com"

    it("successfully sends verification api call", async () => {
      // GIVEN
      const mockApiResponse = Promise.resolve(ok())
      vi.mocked(api.account.verifyContactEmail).mockReturnValue(mockApiResponse)

      const service = createAccountService(initialValue)

      // WHEN
      const result = await service.addContactServiceCalls.initiateOrResendContactVerification(
        { type: ContactMethodType.EMAIL, value: contactMethod },
        undefined,
      )

      // THEN
      expect(result.isOk()).toBe(true)
      // API endpoint was invoked correctly
      expect(api.account.verifyContactEmail).toHaveBeenCalledOnce()
    })

    it("handles api error gracefully", async () => {
      // GIVEN
      const apiErrorResponse = { id: 4, description: "Oops!", stackTrace: null }
      const status = 404
      const errorData = getApiErrorMessages(apiErrorOf(apiErrorResponse, status))
      const mockApiResponse = Promise.resolve(errAsync(errorData))
      vi.mocked(api.account.verifyContactEmail).mockReturnValue(mockApiResponse)

      const service = createAccountService(initialValue)

      // WHEN
      const result = await service.addContactServiceCalls.initiateOrResendContactVerification(
        { type: ContactMethodType.EMAIL, value: contactMethod },
        undefined,
      )

      // THEN
      expect(result.isOk()).toBe(false)
      // API endpoint was invoked correctly
      expect(api.account.verifyContactEmail).toHaveBeenCalledOnce()
    })
  })
})
