<script lang="ts">
  import { ContactVerificationType } from "$components/enums/ContactVerificationType"
  import ContactVerification from "$components/features/ContactVerification.svelte"
  import { initI18n } from "$lib/i18n"
  import type { ContactVerificationServiceCalls } from "$components/interfaces/ContactVerificationServiceCalls"
  import { ok } from "neverthrow"
  import { FormActionType } from "$components/enums/FormActionType"
  import { SessionIdGetter } from "$components/interfaces/SessionIdGetter"

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

  const mockApiCall = async () => {
    return ok()
  }
  const serviceCalls: ContactVerificationServiceCalls = {
    initiateOrResendContactVerification: mockApiCall,
    submitSmsVerificationCode: {
      type: FormActionType.API_CALL,
      call: mockApiCall,
    },
  }

  const sessionIdGetter: SessionIdGetter = () => "someSession"
</script>

<ContactVerification
  isOpen={true}
  {serviceCalls}
  {sessionIdGetter}
  {i18n}
  type={ContactVerificationType.LOGIN}
  hCaptcha={null}
/>
