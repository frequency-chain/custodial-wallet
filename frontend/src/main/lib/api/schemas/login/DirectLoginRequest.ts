export interface DirectLoginRequest {
  contactMethod: string
  contactMethodType: string
  callbackUrl?: string
  captchaToken?: string
}
