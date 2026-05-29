import { api } from "$lib/api"
import { ClientError, ClientErrorType } from "$lib/api/error"
import { apiErrorOf } from "$lib/api/helpers"
import { ChangePasswordResponse } from "$lib/api/schemas/account/ChangePasswordResponse"
import { RevokeDelegationResponse } from "$lib/api/schemas/account/RevokeDelegationResponse"
import { UpdateHandleResponse } from "$lib/api/schemas/account/UpdateHandleResponse"
import { ApiErrorResponse } from "$lib/api/schemas/error/ApiErrorResponse"
import { AuthenticateLoginResponse } from "$lib/api/schemas/login/AuthenticateLoginResponse"
import { AsyncSubmissionResponse } from "$lib/api/schemas/siwa/AsyncSubmissionResponse"
import { ErrorData } from "$lib/types/data/ErrorData"
import { GENERIC_ERROR, getApiErrorMessages } from "$lib/utils/errors"
import { http } from "msw"
import { setupServer } from "msw/node"
import { ok, Result } from "neverthrow"
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest"

type ApiResponse =
  | ChangePasswordResponse
  | RevokeDelegationResponse
  | UpdateHandleResponse
  | AuthenticateLoginResponse
  | AsyncSubmissionResponse

type ApiTestCase<REQUEST_TYPE, RETURN_TYPE> = {
  name: string
  call: () => Promise<Result<RETURN_TYPE, ErrorData | ClientError>>
  endpoint: string
  method: keyof typeof http
  expectedRequest: REQUEST_TYPE
  mockResponse: ApiResponse
  expectedResult: RETURN_TYPE extends void ? undefined : RETURN_TYPE
  expectedErrorType: "ErrorData" | "ClientError"
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const cases: ApiTestCase<any, any>[] = [
  {
    name: "api.account.revokeAllDelegations",
    call: () => api.account.revokeAllDelegations(23),
    method: "delete",
    endpoint: "api/account/delegations/provider/23",
    expectedRequest: undefined,
    mockResponse: true,
    expectedResult: undefined,
    expectedErrorType: "ErrorData",
  },
  {
    name: "api.account.updateHandle",
    call: () => api.account.updateHandle({ newHandle: "newHandle" }),
    method: "put",
    endpoint: "api/account/handle",
    expectedRequest: { newHandle: "newHandle" },
    mockResponse: { claimedHandle: "newHandle" },
    expectedResult: ok("newHandle"),
    expectedErrorType: "ErrorData",
  },
  {
    name: "api.account.verifyContactEmail",
    call: () => api.account.verifyContactEmail({ userAccountId: 1, newIdentifier: "bob@realcompany.com" }),
    method: "post",
    endpoint: "api/account/contact/email/verify",
    expectedRequest: { userAccountId: 1, newIdentifier: "bob@realcompany.com" },
    mockResponse: { response: true },
    expectedResult: undefined,
    expectedErrorType: "ErrorData",
  },
  {
    name: "api.account.verifyContactSms",
    call: () => api.account.verifyContactSms({ userAccountId: 1, newIdentifier: "+15236427783" }),
    method: "post",
    endpoint: "api/account/contact/sms/verify",
    expectedRequest: { userAccountId: 1, newIdentifier: "+15236427783" },
    mockResponse: { response: true },
    expectedResult: undefined,
    expectedErrorType: "ErrorData",
  },
  {
    name: "api.account.changePassword",
    call: () => api.account.changePassword({ userAccountId: 1, newRawPassword: "somenewpassword" }),
    method: "put",
    endpoint: "api/account/password",
    expectedRequest: { userAccountId: 1, newRawPassword: "somenewpassword" },
    mockResponse: { response: true },
    expectedResult: undefined,
    expectedErrorType: "ErrorData",
  },
  {
    name: "api.login.loginDirect (email)",
    call: () => api.login.loginDirect({ contactMethod: "bob@realcompany.com", contactMethodType: "email" }),
    method: "post",
    endpoint: "api/login/direct",
    expectedRequest: { contactMethod: "bob@realcompany.com", contactMethodType: "email" },
    mockResponse: true,
    expectedResult: true,
    expectedErrorType: "ErrorData",
  },
  {
    name: "api.login.loginDirect (sms)",
    call: () => api.login.loginDirect({ contactMethod: "+15236427783", contactMethodType: "phone_number" }),
    method: "post",
    endpoint: "api/login/direct",
    expectedRequest: { contactMethod: "+15236427783", contactMethodType: "phone_number" },
    mockResponse: false,
    expectedResult: false,
    expectedErrorType: "ErrorData",
  },
  {
    name: "api.login.loginAuthenticate",
    call: () => api.login.loginAuthenticate({ authenticationCode: "638875", sessionId: "sessionId" }),
    method: "post",
    endpoint: "api/login/authenticate",
    expectedRequest: { authenticationCode: "638875", sessionId: "sessionId" },
    mockResponse: { sessionId: "a8098c1a-f86e-11da-bd1a-00112444be1e" },
    expectedResult: "a8098c1a-f86e-11da-bd1a-00112444be1e",
    expectedErrorType: "ErrorData",
  },
  {
    name: "api.siwa.getAsyncSubmission",
    call: () => api.siwa.getAsyncSubmission("1"),
    method: "get",
    endpoint: "siwa/api/submission/1",
    expectedRequest: undefined,
    mockResponse: { id: "1", status: "SUBMITTED" },
    expectedResult: { id: "1", status: "SUBMITTED" },
    expectedErrorType: "ClientError",
  },
]

const server = setupServer()

describe("routes", async () => {
  beforeAll(() => {
    server.listen({ onUnhandledRequest: "error" })
  })

  afterAll(() => {
    server.close()
  })

  describe.for(cases)(
    "$name",
    ({ call, method, endpoint, expectedRequest, mockResponse, expectedResult, expectedErrorType }) => {
      afterEach(() => {
        server.resetHandlers()
      })

      it("invokes endpoint correctly", async () => {
        // GIVEN
        server.use(
          http[method](endpoint, async ({ request }) => {
            if (expectedRequest !== undefined) {
              expect(await request.json()).toEqual(expectedRequest)
            }

            return Response.json(mockResponse)
          }),
        )

        // WHEN
        const result = await call()

        // THEN
        expect(result.isOk()).toBe(true)

        const value = result._unsafeUnwrap()
        expect(value).toEqual(expectedResult)
      })

      it("handles api error gracefully", async () => {
        // GIVEN
        const status = 403
        const apiError: ApiErrorResponse = { id: 1, description: "Oops!", stackTrace: null }

        server.use(
          http[method](endpoint, async () => {
            return Response.json(apiError, { status })
          }),
        )

        // WHEN
        const result = await call()

        // THEN
        expect(result.isErr()).toBe(true)

        const expectedApiError = apiErrorOf(apiError, status)
        const error = result._unsafeUnwrapErr()

        if (expectedErrorType === "ErrorData") {
          expect(error).toEqual(getApiErrorMessages(expectedApiError))
        } else {
          expect(error).toEqual(expectedApiError)
        }
      })

      it("handles malformed api error gracefully", async () => {
        // GIVEN
        // const mockErrorMessage = new Error("Test Error")
        server.use(
          http[method](endpoint, async () => {
            throw new Error("Test Error")
          }),
        )

        // WHEN
        const result = await call()

        // THEN
        expect(result.isErr()).toBe(true)

        const error = result._unsafeUnwrapErr()

        if (expectedErrorType === "ErrorData") {
          expect(error).toEqual(GENERIC_ERROR)
        } else {
          const clientError = error as ClientError
          expect(clientError.type).toEqual(ClientErrorType.UNKNOWN)
          expect(clientError.message.startsWith("Failed to parse API error response")).toEqual(true)
        }
      })
    },
  )
})
