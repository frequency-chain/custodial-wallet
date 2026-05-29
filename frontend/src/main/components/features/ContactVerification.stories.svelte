<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import ContactVerification from "./ContactVerification.svelte"
  import Button from "$components/atoms/Button.svelte"
  import { err, ok } from "neverthrow"
  import { initI18n } from "$lib/i18n"
  import type { ContactVerificationServiceCalls } from "$components/interfaces/ContactVerificationServiceCalls"
  import { ContactVerificationType } from "$components/enums/ContactVerificationType"
  import { FormActionType } from "$components/enums/FormActionType"

  const { Story } = defineMeta({
    title: "Features/ContactVerification",
    component: ContactVerification,
    args: {},
  })

  const i18n = initI18n({
    "email-input.existing-email": "Welcome Back",
    "email-input.new-email": "Add Contact",
    "email-input.email": "Email",
    "email-input.use-phone": "Use phone number",
    "email-input.add-contact": "Add Contact",
    "email-input.login": "Login",
    "sms-input.existing-number": "Welcome Back",
    "sms-input.new-number": "Add Contact",
    "sms-input.phone-number": "Phone Number",
    "sms-input.use-email": "Use email",
    "sms-input.add-contact": "Add Contact",
    "sms-input.login": "Login",
    "sms-verification.title": "Confirm Phone Number",
    "sms-verification.description-start": "Please enter the 6-digit code sent via SMS to",
    "sms-verification.description-end": "for verification.",
    "button.submit": "Submit",
    "sms-verification.resend-description": "Didn't receive code?",
    "sms-verification.resend-link": "Resend code",
    "email-verification.title": "Confirm Email",
    "email-verification.description-start": "We have sent your verification link to",
    "email-verification.description-end-add-contact":
      ". Follow the provided link to finish adding new contact. This link will expire in 10 minutes.",
    "email-verification.description-end-login": ". Follow the provided link to finish logging in.",
    "email-verification.spam-reminder": "Can't find the email? (Did you check your spam folder?)",
    "email-verification.resend-description": "Still can't find it?",
    "email-verification.resend-link": "Resend Link",
    "add-contact-success.title": "Success",
    "add-contact-success.message": "You have successfully added a contact!",
    "error.generic.title": "Whoops!",
    "error.generic.desc": "Something went wrong",
    "button.back": "Back",
  })

  const serviceCalls: ContactVerificationServiceCalls = {
    async initiateOrResendContactVerification(_: unknown) {
      console.log("verifyOrResend")
      await new Promise((r) => setTimeout(r, 1000))
      return ok()
    },
    submitSmsVerificationCode: {
      type: FormActionType.FORM_SUBMISSION,
      url: "/?path=/story/features-contactverification--add-contact-success",
    },
  }

  const errorServiceCalls: ContactVerificationServiceCalls = {
    async initiateOrResendContactVerification(_: unknown) {
      return err({ title: "error.generic.title", description: "error.generic.desc" })
    },
    submitSmsVerificationCode: {
      type: FormActionType.FORM_SUBMISSION,
      url: "#",
    },
  }

  const addContact = ContactVerificationType.ADD_CONTACT
</script>

<Story name="Add Contact">
  {#snippet children()}
    <ContactVerification {serviceCalls} {i18n} type={addContact} hCaptcha={null}>
      {#snippet trigger(open)}
        <Button onClick={open}>Add Contact</Button>
      {/snippet}
    </ContactVerification>
  {/snippet}
</Story>

<Story name="Add Contact Error">
  {#snippet children()}
    <ContactVerification serviceCalls={errorServiceCalls} {i18n} type={addContact} hCaptcha={null}>
      {#snippet trigger(open)}
        <Button onClick={open}>Add Contact</Button>
      {/snippet}
    </ContactVerification>
  {/snippet}
</Story>
