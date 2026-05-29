<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import AccountPage from "./AccountPage.svelte"
  import { type AccountService } from "$lib/services/accountService"
  import { type AccountData, UserDetailType } from "$lib/types/data/AccountData"
  import { readable, readonly, writable } from "svelte/store"
  import { err, ok } from "neverthrow"
  import { initI18n } from "$lib/i18n"
  import { type FeatureFlagService } from "$lib/services/featureFlagService"
  import { type FeatureFlagData } from "$lib/types/data/FeatureFlagData"
  import { type ContactVerificationServiceCalls } from "$components/features/ContactVerification.svelte"

  const { Story } = defineMeta({
    title: "Pages/AccountPage",
    component: AccountPage,
    args: {},
  })

  const sessionIdGetter = {
    getSessionId: () => {
      return "fake-session-id"
    },
  }

  // ************************************
  // *********** I18N Service ***********
  // ************************************

  const i18n = initI18n({
    "website-header.home": "HOME.",
    "website-header.faqs": "FAQs.",
    "website-header.account": "My Account",
    "website-header.login": "Login",
    "account.logout": "Logout",
    "account.providers": "Providers",
    "account.handle": "Handle",
    "account.msa": "MSA: ",
    "account.permissions": "Permissions",
    "account.revoke-button": "Revoke",
    "account.change-password-button": "Change Password",
    "account.password.title": "Password",
    "button.close": "Close",
    "change-password-modal.title": "Change Password",
    "change-password-modal.new": "New Password",
    "change-password-modal.retype": "Retype New Password",
    "change-password-modal.nomatch": "New password does not match",
    "change-password-modal.changed": "Password successfully changed",
    "change-password-modal.button": "Change Password",
    "enter-handle-field.label": "Handle",
    "enter-handle-field.help.title": "Handle requirements",
    "enter-handle-field.help.requirements.1.before-link":
      "Keep it appropriate - no offensive language. For additional rules, please see ",
    "enter-handle-field.help.requirements.1.after-link": ".",
    "enter-handle-field.help.requirements.1.link-text": "Terms of Service",
    "enter-handle-field.help.requirements.2":
      "Frequency will automatically add a few numbers to the end of your handle to make sure it's unique.",
    "change-handle.title": "Handle",
    "change-handle.description": "Your unique handle is how others will find you across all apps on Frequency.",
    "change-handle.input.label": "Handle",
    "change-handle.permission.lead-in": "By updating your handle you agree to these permissions:",
    "change-handle.permission.1.description": "Update your handle and profile information on Frequency",
    "change-handle.submit": "Save and Continue",
    "change-handle.success.title": "Success",
    "change-handle.success.description": "You have successfully updated your handle!",
    "revoke-modal.title": "Revoke Permissions?",
    "revoke-modal.confirmation-message": "This provider will no longer be authorized to access your frequency data.",
    "revoke-modal.revoke": "Confirm",
    "permission.account.update-identity": "Permission to update identity",
    "permission.account.graph":
      "Record, delete and modify the public and private follows and connections from your account to your Social Graph on Frequency",
    "error.generic.title": "Whoops!",
    "error.generic.desc": "Something went wrong",
    "account.contact": "Contact",
    "account.add-contact": "Add Contact",
    "account.emails": "Email:",
    "account.phone-numbers": "Phone Number:",
    "email-input.new-email": "Add Contact",
    "email-input.email": "Email",
    "email-input.use-phone": "Use phone number",
    "email-input.add-contact": "Add Contact",
    "sms-input.new-number": "Add Contact",
    "sms-input.phone-number": "Phone Number",
    "sms-input.use-email": "Use email",
    "sms-input.add-contact": "Add Contact",
    "sms-verification.title": "Confirm Phone Number",
    "sms-verification.description-start": "Please enter the 6-digit code sent via SMS to",
    "sms-verification.description-end": "for verification.",
    "button.submit": "Submit",
    "button.back": "Back",
    "sms-verification.resend-description": "Didn't receive code?",
    "sms-verification.resend-link": "Resend code",
    "email-verification.title": "Confirm Email",
    "email-verification.description-start": "We have sent your verification link to",
    "email-verification.description-end-add-contact":
      ". Follow the provided link to finish adding new contact. This link will expire in 10 minutes.",
    "email-verification.spam-reminder": "Can't find the email? (Did you check your spam folder?)",
    "email-verification.resend-description": "Still can't find it?",
    "email-verification.resend-link": "Resend Link",
    "add-contact-success.title": "Success",
    "add-contact-success.message": "You have successfully added a contact!",
    "account.passkey-wallet.title": "Passkey Wallet",
    "account.passkey-wallet.exists": "You have created a passkey Wallet for your Frequency Access Account",
    "account.passkey-wallet.create":
      "You have not created a passkey wallet for your Frequency Access Account. Click the button below to get started.",
    "account.passkey-wallet.button": "Create Wallet",
    "account.passkey-wallet.recovery.button": "Restore Wallet",
  })

  // ************************************
  // ********** Account Service *********
  // ************************************

  const mockData: AccountData = {
    providers: [
      {
        msaId: 3,
        name: "Example Provider",
        permissions: ["permission.account.update-identity", "permission.account.graph"],
      },
    ],
    userDetails: {
      [UserDetailType.EMAIL]: [
        {
          priority: 1,
          type: UserDetailType.EMAIL,
          value: "peter.frank@projectliberty.io",
        },
        {
          priority: 1,
          type: UserDetailType.EMAIL,
          value: "peter.frank+1@projectliberty.io",
        },
      ],
      [UserDetailType.PHONE_NUMBER]: [
        {
          priority: 2,
          type: UserDetailType.PHONE_NUMBER,
          value: "+18055591665",
        },
      ],
    },
    handle: "JaneLovesWinter.99",
    userPublicKeyHex: "0.x000",
    userAccountId: 1,
    hasPassword: true,
    hasPasskeyWallet: false,
    msaId: 123,
  }

  const contactVerificationServiceCalls: ContactVerificationServiceCalls = {
    async emailVerify(_request) {
      console.log("emailVerify")
      await new Promise((r) => setTimeout(r, 1000))
      return ok()
    },
    async smsVerify(_request) {
      console.log("smsVerify")
      await new Promise((r) => setTimeout(r, 1000))
      return ok()
    },
    async emailResend(_request) {
      console.log("emailResend")
      await new Promise((r) => setTimeout(r, 1000))
      return ok()
    },
    async codeResend(_request) {
      console.log("codeResend")
      await new Promise((r) => setTimeout(r, 1000))
      return ok()
    },
    codeSubmit: "http://localhost:6006/?path=/story/features-contactverification--add-contact-success",
  }

  const contactVerificationServiceCallsErrors: ContactVerificationServiceCalls = {
    async emailVerify(_request) {
      return err({ title: "error.generic.title", description: "error.generic.desc" })
    },
    async smsVerify(_request) {
      return err({ title: "error.generic.title", description: "error.generic.desc" })
    },
    async emailResend(_request) {
      return err({ title: "error.generic.title", description: "error.generic.desc" })
    },
    async codeResend(_request) {
      return err({ title: "error.generic.title", description: "error.generic.desc" })
    },
    codeSubmit: "#",
  }

  const store = writable<AccountData>(mockData)
  const accountService: AccountService = {
    data: readonly(store),
    // Spoof delay from API round trip
    revokeProviderDelegations: async (_) => {
      await new Promise((r) => setTimeout(r, 1000))
      store.update(({ providers, ...rest }: AccountData) => {
        // Assumes only a single provider
        const { name, msaId } = providers[0]!
        return { providers: [{ name, msaId, permissions: [] }], ...rest }
      })
      return ok()
    },
    changePassword: async (_) => {
      return ok()
    },
    updateHandle: async ({ newHandle }) => {
      await new Promise((r) => setTimeout(r, 1000))

      const mockClaimedHandle = newHandle + ".99"
      store.update(({ handle, ...otherAccountData }): AccountData => {
        return { handle: mockClaimedHandle, ...otherAccountData }
      })
      return ok(ok(mockClaimedHandle))
    },
    contactVerificationServiceCalls,
  }

  const accountServiceError: AccountService = {
    data: readable<AccountData>(mockData),
    // Spoof delay from API round trip
    revokeProviderDelegations: async (_) => {
      await new Promise((r) => setTimeout(r, 1000))
      return err({ title: "error.generic.title", description: "error.generic.desc" })
    },
    changePassword: async (_) => {
      await new Promise((r) => setTimeout(r, 1000))
      return err({ title: "error.generic.title", description: "error.generic.desc" })
    },
    updateHandle: async (_) => {
      return err({ title: "error.generic.title", description: "error.generic.desc" })
    },
    contactVerificationServiceCalls: contactVerificationServiceCallsErrors,
  }

  // ************************************
  // ******* Feature Flag Service *******
  // ************************************

  const mockFeatureFlagData: FeatureFlagData = {
    passwordEnabled: true,
    addContactEnabled: true,
    passkeyWalletEnabled: true,
    changeHandleEnabled: true,
  }

  const featureFlagStore = writable<FeatureFlagData>(mockFeatureFlagData)
  const featureFlagService: FeatureFlagService = {
    data: readonly(featureFlagStore),
  }

  // ************************************
  // *********** Add Contact  ***********
  // ************************************

  const contactAdded = false
</script>

<Story name="Primary" parameters={{ layout: "fullscreen" }}>
  {#snippet children()}
    <AccountPage {accountService} {i18n} {featureFlagService} {contactAdded} />
  {/snippet}
</Story>

<Story name="Contact Added Success" parameters={{ layout: "fullscreen" }}>
  {#snippet children()}
    <AccountPage {accountService} {i18n} {featureFlagService} contactAdded={true} />
  {/snippet}
</Story>

<Story name="Account Service Error" parameters={{ layout: "fullscreen" }}>
  {#snippet children()}
    <AccountPage accountService={accountServiceError} {i18n} {featureFlagService} {contactAdded} />
  {/snippet}
</Story>
