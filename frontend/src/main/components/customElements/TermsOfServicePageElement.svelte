<svelte:options customElement={{ tag: "terms-of-service-page", shadow: "none" }} />

<script lang="ts">
  import { initI18nFromMessagesJson } from "$lib/utils/server"
  import type { CwProps } from "$components/customElements/interfaces/CwProps"
  import TermsOfServicePage from "$components/pages/TermsOfServicePage.svelte"
  import { StringToBoolean } from "$lib/api/schemas/util/StringToBoolean"
  import { HeaderButtonAction } from "$components/interfaces/HeaderButtonAction"
  import { HeaderButtonActionType } from "$components/enums/HeaderButtonActionType"
  import { createLoginService } from "$lib/services/loginService"
  import { getSessionId } from "$lib/utils"

  // All types come in as strings.
  type Props = {
    "user-is-logged-in": string
    "is-developer-terms": string
    "captcha-enabled": string
    "site-key": string
  } & CwProps

  let {
    "messages-json": localizedMessagesJson,
    "user-is-logged-in": userIsLoggedInString,
    "is-developer-terms": isDeveloperTermsString,
    "captcha-enabled": captchaEnabledString,
    "site-key": siteKey,
  }: Props = $props()

  let userIsLoggedIn: boolean = $derived(userIsLoggedInString ? StringToBoolean.parse(userIsLoggedInString) : false)
  let isDeveloperTerms: boolean = $derived(
    isDeveloperTermsString ? StringToBoolean.parse(isDeveloperTermsString) : false,
  )
  let captchaEnabled: boolean = $derived(captchaEnabledString ? StringToBoolean.parse(captchaEnabledString) : false)

  console.log(userIsLoggedIn)

  const getButtonAction = (userIsLoggedIn: boolean): HeaderButtonAction | undefined => {
    if (userIsLoggedIn) {
      return { type: HeaderButtonActionType.NAVIGATE }
    }
    return {
      type: HeaderButtonActionType.LOGIN,
      serviceCalls: createLoginService().serviceCalls,
      sessionIdGetter: getSessionId,
      hCaptcha: captchaEnabled ? { siteKey } : null,
    }
  }

  let buttonAction = $derived(getButtonAction(userIsLoggedIn))

  const i18n = initI18nFromMessagesJson(localizedMessagesJson)
</script>

<TermsOfServicePage {i18n} headerButtonAction={buttonAction} {isDeveloperTerms} />
