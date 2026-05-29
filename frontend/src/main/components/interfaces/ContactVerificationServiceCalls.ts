import type { ContactMethod } from "$components/interfaces/ContactMethod"
import { ErrorData } from "$lib/types/data/ErrorData"
import { Result } from "neverthrow"
import { FormAction } from "./FormAction"

export interface ContactVerificationServiceCalls {
  initiateOrResendContactVerification: (
    contact: ContactMethod,
    captchaToken: string | undefined,
  ) => Promise<Result<void, ErrorData>>
  submitSmsVerificationCode: FormAction<{ authenticationCode: string; sessionId: string }, void>
}
