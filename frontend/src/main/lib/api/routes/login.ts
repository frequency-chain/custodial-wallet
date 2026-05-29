import { safeFetch, unwrapPostResult } from "$lib/api/helpers"
import { AuthenticateLoginRequest } from "$lib/api/schemas/login/AuthenticateLoginRequest"
import { AuthenticateLoginResponseSchema } from "$lib/api/schemas/login/AuthenticateLoginResponse"
import { DirectLoginRequest } from "$lib/api/schemas/login/DirectLoginRequest"
import { DirectLoginResponseSchema } from "$lib/api/schemas/login/DirectLoginResponse"
import { ErrorData } from "$lib/types/data/ErrorData"
import { Result } from "neverthrow"

const path = "/api/login"

// Returns true when backend receives an email and false when the backend receives a phone number
const loginDirect = (request: DirectLoginRequest): Promise<Result<boolean, ErrorData>> => {
  const endpoint = "direct"
  const fetchResult = safeFetch(`${path}/${endpoint}`, DirectLoginResponseSchema, {
    method: "POST",
    credentials: "include", // Includes cookies with request
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  })
  return unwrapPostResult(fetchResult, "Failed to login")
}

const loginAuthenticate = async (request: AuthenticateLoginRequest): Promise<Result<string, ErrorData>> => {
  const endpoint = "authenticate"
  const fetchResult = safeFetch(`${path}/${endpoint}`, AuthenticateLoginResponseSchema, {
    method: "POST",
    credentials: "include", // Includes cookies with request
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  })
  return unwrapPostResult(fetchResult, "Failed to authenticate login").then((result) =>
    result.map(({ sessionId }) => sessionId),
  )
}

export const login = {
  loginDirect,
  loginAuthenticate,
}
