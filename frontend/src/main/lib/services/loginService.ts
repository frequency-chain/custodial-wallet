import { FormActionType } from "$components/enums/FormActionType"
import { ContactMethod } from "$components/interfaces/ContactMethod"
import { ContactVerificationServiceCalls } from "$components/interfaces/ContactVerificationServiceCalls"
import { api } from "$lib/api"
import { AuthenticateLoginRequest } from "$lib/api/schemas/login/AuthenticateLoginRequest"
import { DirectLoginRequest } from "$lib/api/schemas/login/DirectLoginRequest"
import { ErrorData } from "$lib/types/data/ErrorData"
import { Result } from "neverthrow"

export interface LoginService {
  serviceCalls: ContactVerificationServiceCalls
}

export const createLoginService = (): LoginService => {
  const submitLoginRequest = async (
    contact: ContactMethod,
    captchaToken: string | undefined,
  ): Promise<Result<void, ErrorData>> => {
    const callbackUrl = new URLSearchParams(location.search)?.get("callbackUrl")

    const directLoginRequest: DirectLoginRequest = {
      contactMethod: contact.value,
      contactMethodType: contact.type,
      captchaToken,
      callbackUrl: callbackUrl ? callbackUrl : undefined,
    }
    const response = await api.login.loginDirect(directLoginRequest)
    return response.map(() => {})
  }

  const codeSubmit = async (authenticateLoginRequest: AuthenticateLoginRequest): Promise<Result<void, ErrorData>> => {
    const loginAuthenticateResult = await api.login.loginAuthenticate(authenticateLoginRequest)
    if (loginAuthenticateResult.isOk()) {
      window.location.href = "/web/account"
    }
    return loginAuthenticateResult.map(() => {})
  }

  return {
    serviceCalls: {
      initiateOrResendContactVerification: submitLoginRequest,
      submitSmsVerificationCode: {
        type: FormActionType.API_CALL,
        call: codeSubmit,
      },
    },
  }
}
