import { ErrorData } from "$lib/types/data/ErrorData"

export const enum ChangeHandleStepType {
  ENTER_HANDLE = "ENTER_HANDLE",
  SUCCESS = "SUCCESS",
  ERROR = "ERROR",
}

export interface EnterHandleStep {
  type: ChangeHandleStepType.ENTER_HANDLE
  isLoading: boolean
  validationErrorMessage?: string
}

export interface SuccessStep {
  type: ChangeHandleStepType.SUCCESS
  newHandle: string
}

export interface ErrorStep {
  type: ChangeHandleStepType.ERROR
  errorData: ErrorData
}

export type ChangeHandleStep = EnterHandleStep | SuccessStep | ErrorStep
