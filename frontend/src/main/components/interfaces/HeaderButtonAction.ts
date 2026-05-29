import { HeaderButtonActionType } from "$components/enums/HeaderButtonActionType"
import { ContactVerificationServiceCalls } from "$components/interfaces/ContactVerificationServiceCalls"
import { SessionIdGetter } from "$components/interfaces/SessionIdGetter"

interface NavigateToAccount {
  type: HeaderButtonActionType.NAVIGATE
}
interface Logout {
  type: HeaderButtonActionType.LOGOUT
}
interface Login {
  type: HeaderButtonActionType.LOGIN
  hCaptcha: { siteKey: string } | null
  serviceCalls: ContactVerificationServiceCalls
  sessionIdGetter: SessionIdGetter
}

export type HeaderButtonAction = NavigateToAccount | Logout | Login
