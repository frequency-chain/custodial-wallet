<svelte:options customElement={{ tag: "account-page", shadow: "none" }} />

<script lang="ts">
  import AccountPage from "../pages/AccountPage.svelte"
  import { createAccountService } from "$lib/services/accountService"
  import { initI18nFromMessagesJson, parseAccountInfoJson } from "$lib/utils/server"
  import type { CwProps } from "$components/customElements/interfaces/CwProps"
  import { createFeatureFlagService } from "$lib/services/featureFlagService"
  import type { FeatureFlagData } from "$lib/types/data/FeatureFlagData"
  import type { AccountData } from "$lib/types/data/AccountData"
  import { StringToBoolean } from "$lib/api/schemas/util/StringToBoolean"

  // All types come in as strings.
  type Props = {
    "account-info-json": string
    "contact-added": string //boolean
    "password-enabled": string //boolean
    "revocation-enabled": string //boolean
    "passkey-wallet-enabled": string //boolean
    "add-contact-enabled": string //boolean
    "verification-callback-url": string | undefined
    "change-handle-enabled": string //boolean
  } & CwProps

  let {
    "account-info-json": accountInfoJson,
    "messages-json": localizedMessagesJson,
    "password-enabled": passwordEnabledString,
    "add-contact-enabled": addContactEnabledString,
    "contact-added": contactAddedString,
    "passkey-wallet-enabled": passkeyWalletEnabledString,
    "change-handle-enabled": changeHandleEnabledString,
  }: Props = $props()

  // Convert values to expected types
  const passwordEnabledBoolean: boolean = StringToBoolean.parse(passwordEnabledString)
  const addContactEnabledBoolean: boolean = StringToBoolean.parse(addContactEnabledString)
  const contactAddedBoolean: boolean = StringToBoolean.parse(contactAddedString)
  const passkeyWalletEnabledBoolean: boolean = StringToBoolean.parse(passkeyWalletEnabledString)
  const changeHandleEnabledBoolean: boolean = StringToBoolean.parse(changeHandleEnabledString)

  const i18n = initI18nFromMessagesJson(localizedMessagesJson)
  const featureFlagData: FeatureFlagData = {
    passwordEnabled: passwordEnabledBoolean,
    addContactEnabled: addContactEnabledBoolean,
    passkeyWalletEnabled: passkeyWalletEnabledBoolean,
    changeHandleEnabled: changeHandleEnabledBoolean,
  }
  const featureFlagService = createFeatureFlagService(featureFlagData)
  const accountData: AccountData = parseAccountInfoJson(accountInfoJson)
  const accountService = createAccountService(accountData)
</script>

<AccountPage {i18n} {featureFlagService} {accountService} contactAdded={contactAddedBoolean} />
