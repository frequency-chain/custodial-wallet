<svelte:options customElement={{ tag: "header-element", shadow: "none" }} />

<script lang="ts">
  import { initI18nFromMessagesJson } from "$lib/utils/server"
  import type { CwProps } from "$components/customElements/interfaces/CwProps"
  import { StringToBoolean } from "$lib/api/schemas/util/StringToBoolean"
  import Header from "$components/features/Header.svelte"
  import { createLoginService } from "$lib/services/loginService"
  import { getSessionId } from "$lib/utils"
  import { HeaderButtonAction } from "$components/interfaces/HeaderButtonAction"
  import { HeaderButtonActionType } from "$components/enums/HeaderButtonActionType"

  type Props = {
    "user-is-logged-in": string
    "is-account-page": string
    "is-home-page": string
    "captcha-enabled": string
    "site-key": string
  } & CwProps

  let {
    "user-is-logged-in": userIsLoggedInString,
    "is-account-page": isAccountPageString,
    "is-home-page": isHomePageString,
    "captcha-enabled": captchaEnabledString,
    "site-key": siteKey,
    "messages-json": localizedMessagesJson,
  }: Props = $props()

  let isAccountPage: boolean = $derived(isAccountPageString ? StringToBoolean.parse(isAccountPageString) : false)
  let isHomePage: boolean = $derived(isHomePageString ? StringToBoolean.parse(isHomePageString) : false)
  let userIsLoggedIn: boolean = $derived(userIsLoggedInString ? StringToBoolean.parse(userIsLoggedInString) : false)
  let captchaEnabled: boolean = $derived(captchaEnabledString ? StringToBoolean.parse(captchaEnabledString) : false)

  const i18n = initI18nFromMessagesJson(localizedMessagesJson)

  const getButtonAction = (
    isAccountPage: boolean,
    isHomePage: boolean,
    userIsLoggedIn: boolean,
  ): HeaderButtonAction | undefined => {
    if (isAccountPage) {
      return { type: HeaderButtonActionType.LOGOUT }
    }

    if (isHomePage) {
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
  }

  let buttonAction = $derived(getButtonAction(isAccountPage, isHomePage, userIsLoggedIn))
</script>

<Header {i18n} {buttonAction} />
