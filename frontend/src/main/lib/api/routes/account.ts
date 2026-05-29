import { ClientError, ClientErrorType } from "$lib/api/error"
import { ChangePasswordRequest } from "$lib/api/schemas/account/ChangePasswordRequest"
import { ChangePasswordResponseSchema } from "$lib/api/schemas/account/ChangePasswordResponse"
import { UpdateHandleRequest } from "$lib/api/schemas/account/UpdateHandleRequest"
import { UpdateHandleResponseSchema } from "$lib/api/schemas/account/UpdateHandleResponse"
import { VerifyContactRequest } from "$lib/api/schemas/account/VerifyContactRequest"
import { VerifyContactResponseSchema } from "$lib/api/schemas/account/VerifyContactResponse"
import { ErrorData } from "$lib/types/data/ErrorData"
import { getHandleValidationMessage, HANDLE_VALIDATION_ERRORS } from "$lib/utils/errors"
import { err, ok, Result } from "neverthrow"
import { getErrorData, matchBooleanHolderPostResult, matchBooleanPostResult, safeFetch } from "../helpers"
import { RevokeDelegationResponseSchema } from "../schemas/account/RevokeDelegationResponse"

const path = "/api/account"

const revokeAllDelegations = async (providerMsaId: number): Promise<Result<void, ErrorData>> => {
  const fetchResult = safeFetch(`${path}/delegations/provider/${providerMsaId}`, RevokeDelegationResponseSchema, {
    method: "DELETE",
    credentials: "include", // Includes cookies with request
  })
  return matchBooleanPostResult(fetchResult, "Failed to revoke delegations")
}

const updateHandle = async (request: UpdateHandleRequest): Promise<Result<Result<string, string>, ErrorData>> => {
  const fetchResult = safeFetch(`${path}/handle`, UpdateHandleResponseSchema, {
    method: "PUT",
    credentials: "include", // Includes cookies with request
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  })

  return fetchResult.match(
    ({ claimedHandle }): Result<Result<string, string>, ErrorData> => ok(ok(claimedHandle)),
    (clientError: ClientError): Result<Result<string, string>, ErrorData> => {
      // Check if the error is related to the handle being invalid
      if (clientError.type === ClientErrorType.API && clientError.id in HANDLE_VALIDATION_ERRORS) {
        const validationMessage = getHandleValidationMessage(clientError)
        return ok(err(validationMessage))
      } else {
        return err(getErrorData(clientError, "Failed to update handle"))
      }
    },
  )
}

const verifyContact = (
  endpoint: string,
  request: VerifyContactRequest,
  errorMessagePrefix: string,
): Promise<Result<void, ErrorData>> => {
  const fetchResult = safeFetch(`${path}/${endpoint}`, VerifyContactResponseSchema, {
    method: "POST",
    credentials: "include", // Includes cookies with request
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  })
  return matchBooleanHolderPostResult(fetchResult, errorMessagePrefix)
}

const verifyContactEmail = (request: VerifyContactRequest): Promise<Result<void, ErrorData>> => {
  return verifyContact("contact/email/verify", request, "Failed to start contact verification process for Email")
}

const verifyContactSms = (request: VerifyContactRequest): Promise<Result<void, ErrorData>> => {
  return verifyContact("contact/sms/verify", request, "Failed to start contact verification process for SMS")
}

const changePassword = (request: ChangePasswordRequest): Promise<Result<void, ErrorData>> => {
  const result = safeFetch(`${path}/password`, ChangePasswordResponseSchema, {
    method: "PUT",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  })
  return matchBooleanHolderPostResult(result, "Failed to change password")
}

export const account = {
  revokeAllDelegations,
  updateHandle,
  verifyContactEmail,
  verifyContactSms,
  changePassword,
}
