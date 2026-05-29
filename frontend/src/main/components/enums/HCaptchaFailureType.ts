// See: https://docs.hcaptcha.com/configuration/#error-codes
export const enum HCaptchaFailureType {
  CHALLENGE_CLOSED = "CHALLENGE_CLOSED",
  CHALLENGE_ERROR = "CHALLENGE_ERROR",
  CHALLENGE_EXPIRED = "CHALLENGE_EXPIRED",
  INTERNAL_ERROR = "INTERNAL_ERROR",
  INVALID_CAPTCHA_ID = "INVALID_CAPTCHA_ID",
  INVALID_DATA = "INVALID_DATA",
  MISSING_CAPTCHA = "MISSING_CAPTCHA",
  NETWORK_ERROR = "NETWORK_ERROR",
  RATE_LIMITED = "RATE_LIMITED",
  SCRIPT_ERROR = "SCRIPT_ERROR",
  UNKNOWN_ERROR = "UNKNOWN_ERROR",
}

const hCaptchaFailureMap: Record<string, HCaptchaFailureType> = {
  "challenge-closed": HCaptchaFailureType.CHALLENGE_CLOSED,
  "challenge-error": HCaptchaFailureType.CHALLENGE_ERROR,
  "challenge-expired": HCaptchaFailureType.CHALLENGE_EXPIRED,
  "internal-error": HCaptchaFailureType.INTERNAL_ERROR,
  "invalid-captcha-id": HCaptchaFailureType.INVALID_CAPTCHA_ID,
  "invalid-data": HCaptchaFailureType.INVALID_DATA,
  "missing-captcha": HCaptchaFailureType.MISSING_CAPTCHA,
  "network-error": HCaptchaFailureType.NETWORK_ERROR,
  "rate-limited": HCaptchaFailureType.RATE_LIMITED,
  "script-error": HCaptchaFailureType.SCRIPT_ERROR,
}

export const hCaptchaFailureTypeFromString = (s: string): HCaptchaFailureType => {
  return hCaptchaFailureMap[s] ?? HCaptchaFailureType.UNKNOWN_ERROR
}
