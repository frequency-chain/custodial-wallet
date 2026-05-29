import { ErrorData } from "$lib/types/data/ErrorData"

export const enum SiwaSubmissionStatusType {
  UNKNOWN = "UNKNOWN", // I.e., the frontend is unable to communicate with the backend
  SUBMITTED = "SUBMITTED",
  FAILED = "FAILED",
  SUCCESS = "SUCCESS",
}

export interface UnknownSiwaSubmissionStatus {
  type: SiwaSubmissionStatusType.UNKNOWN
  errorData: ErrorData
}

export interface SubmittedSiwaSubmissionStatus {
  type: SiwaSubmissionStatusType.SUBMITTED
}

export interface FailedSiwaSubmissionStatus {
  type: SiwaSubmissionStatusType.FAILED
  errorData: ErrorData
}

export interface SuccessfulSiwaSubmissionStatus {
  type: SiwaSubmissionStatusType.SUCCESS
  redirectUrl: string
}

export type SiwaSubmissionStatus =
  | UnknownSiwaSubmissionStatus
  | SubmittedSiwaSubmissionStatus
  | FailedSiwaSubmissionStatus
  | SuccessfulSiwaSubmissionStatus
