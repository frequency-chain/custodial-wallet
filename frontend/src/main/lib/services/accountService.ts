import { ContactMethodType } from "$components/enums/ContactMethodType"
import { FormActionType } from "$components/enums/FormActionType"
import { ContactMethod } from "$components/interfaces/ContactMethod"
import { ContactVerificationServiceCalls } from "$components/interfaces/ContactVerificationServiceCalls"
import { FormSubmission } from "$components/interfaces/FormAction"
import { api } from "$lib/api"
import { VerifyContactRequest } from "$lib/api/schemas/account/VerifyContactRequest"
import { AccountData } from "$lib/types/data/AccountData"
import { ErrorData } from "$lib/types/data/ErrorData"
import { Result } from "neverthrow"
import { Readable, readonly, writable } from "svelte/store"

export interface AccountService {
  data: Readable<AccountData>
  revokeProviderDelegations: (providerMsaId: number) => Promise<Result<void, ErrorData>>
  changePassword: (request: { userAccountId: number; newRawPassword: string }) => Promise<Result<void, ErrorData>>
  updateHandle: (request: { newHandle: string }) => Promise<Result<Result<string, string>, ErrorData>>
  addContactServiceCalls: ContactVerificationServiceCalls
}

export const createAccountService = (initialValue: AccountData): AccountService => {
  // A Svelte 'store' that is reactive and can be written to
  const dataStore = writable<AccountData>(initialValue)

  // Internal helper for updating the store to reflect revoking permissions from the given provider
  const deleteProviderPermissionsFromStore = (msaId: number) => {
    dataStore.update(({ providers, ...otherAccountData }): AccountData => {
      const newProviders = providers.map((provider) => {
        if (provider.msaId === msaId) {
          return { name: provider.name, msaId: provider.msaId, permissions: [] }
        } else {
          return provider
        }
      })
      return { providers: newProviders, ...otherAccountData }
    })
  }

  // Internal helper for updating the store to reflect revoking permissions from the given provider
  const updateHandleInStore = (newHandle: string) => {
    dataStore.update(({ ...accountData }): AccountData => {
      return { ...accountData, handle: newHandle }
    })
  }

  // Public method for revoking delegations from a provider
  const revokeProviderDelegations = async (providerMsaId: number): Promise<Result<void, ErrorData>> => {
    return api.account.revokeAllDelegations(providerMsaId).then((result) => {
      if (result.isOk()) {
        deleteProviderPermissionsFromStore(providerMsaId)
      }
      return result
    })
  }

  const changePassword = async (request: {
    userAccountId: number
    newRawPassword: string
  }): Promise<Result<void, ErrorData>> => {
    return api.account.changePassword(request)
  }

  const updateHandle = async (request: { newHandle: string }): Promise<Result<Result<string, string>, ErrorData>> => {
    const response = await api.account.updateHandle(request)

    // Update the store to reflect the change
    response.andTee((handleResult) => {
      handleResult.andTee((newHandle) => updateHandleInStore(newHandle))
    })

    return response
  }

  const submitAddContactRequest = async (contact: ContactMethod): Promise<Result<void, ErrorData>> => {
    const verifyContactRequest: VerifyContactRequest = {
      userAccountId: initialValue.userAccountId,
      newIdentifier: contact.value,
    }

    switch (contact.type) {
      case ContactMethodType.EMAIL:
        return api.account.verifyContactEmail(verifyContactRequest)
      case ContactMethodType.PHONE_NUMBER:
        return api.account.verifyContactSms(verifyContactRequest)
    }
  }

  const submitSmsVerificationCode: FormSubmission = {
    type: FormActionType.FORM_SUBMISSION,
    url: "/web/add/sms",
  }

  return {
    data: readonly(dataStore), // Expose a read-only 'view' of the store to the UI
    revokeProviderDelegations,
    changePassword,
    updateHandle,
    addContactServiceCalls: {
      initiateOrResendContactVerification: submitAddContactRequest,
      submitSmsVerificationCode,
    },
  }
}
