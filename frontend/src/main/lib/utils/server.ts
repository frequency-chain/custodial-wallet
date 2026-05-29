import { I18nGetter, initI18n } from "$lib/i18n"
import { AccountData, ProviderData, UserDetailData, UserDetailType } from "$lib/types/data/AccountData"
import { AccountInfo, AccountInfoSchema } from "$lib/types/server/AccountInfo"
import { LocalizedMessagesSchema } from "$lib/types/server/LocalizedMessages"
import { groupBy } from "lodash"
import { Readable } from "svelte/store"

export const parseAccountInfoJson = (accountInfoJson: string): AccountData => {
  const accountInfo: AccountInfo = AccountInfoSchema.parse(JSON.parse(accountInfoJson))

  if (accountInfo.providerUserInfo.length == 0) {
    throw new Error("AccountInfo.providerUserInfo array is empty!")
  }

  return {
    providers: accountInfo.providerUserInfo.map(
      (it): ProviderData => ({ msaId: it.providerMsaId, name: it.providerName, permissions: it.permissions }),
    ),
    userDetails: groupBy(
      accountInfo.userDetails.map(
        (it): UserDetailData => ({
          value: it.value,
          type: UserDetailType[it.type],
          priority: it.priority,
        }),
      ),
      (it) => it.type,
    ),
    // Pull the handle and public key from the first provider info object--assuming the values are the same
    // for all providers.
    handle: accountInfo.providerUserInfo[0]!.userHandle,
    userPublicKeyHex: accountInfo.providerUserInfo[0]!.publicKeyHex,
    userAccountId: accountInfo.providerUserInfo[0]!.userAccountId,
    hasPassword: accountInfo.providerUserInfo[0]!.hasPassword,
    hasPasskeyWallet: accountInfo.hasPasskeyWallet,
    msaId: accountInfo.providerUserInfo[0]!.userMsaId,
  }
}

export const initI18nFromMessagesJson = (jsonString: string): Readable<I18nGetter> => {
  const messages = LocalizedMessagesSchema.parse(JSON.parse(jsonString))
  return initI18n(messages)
}
