import { FormActionType } from "$components/enums/FormActionType"
import { ErrorData } from "$lib/types/data/ErrorData"
import { Result } from "neverthrow"

export interface FormSubmission {
  type: FormActionType.FORM_SUBMISSION
  url: string
}

export interface ApiCall<REQUEST_TYPE, RESPONSE_TYPE> {
  type: FormActionType.API_CALL
  call: (request: REQUEST_TYPE) => Promise<Result<RESPONSE_TYPE, ErrorData>>
}

export type FormAction<REQUEST_TYPE, RESPONSE_TYPE> = FormSubmission | ApiCall<REQUEST_TYPE, RESPONSE_TYPE>
