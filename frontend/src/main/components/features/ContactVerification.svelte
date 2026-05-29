<script lang="ts">
  import { Modal } from "$components/atoms/modal"
  import type { Testable } from "$components/interfaces/Testable"
  import { type ErrorData } from "$lib/types/data/ErrorData"
  import { InputType } from "$components/enums/InputType"
  import Button from "$components/atoms/Button.svelte"
  import Input from "$components/atoms/Input.svelte"
  import type { Readable } from "svelte/store"
  import type { I18nGetter } from "$lib/i18n"
  import TextButton from "$components/atoms/TextButton.svelte"
  import { ContactVerificationStep, ContactVerificationType } from "$components/enums/ContactVerificationType"
  import type { ModalTriggerProps } from "$components/interfaces/ModalTriggerProps"
  import type { ModalControlProps } from "$components/interfaces/ModalControlProps"
  import { FormActionType } from "$components/enums/FormActionType"
  import type { ContactVerificationServiceCalls } from "$components/interfaces/ContactVerificationServiceCalls"
  import type { SessionIdGetter } from "$components/interfaces/SessionIdGetter"
  import HCaptcha from "$components/features/HCaptcha.svelte"
  import type { ContactMethod } from "$components/interfaces/ContactMethod"
  import EnterContactMethodField from "$components/features/EnterContactMethodField.svelte"
  import { ContactMethodType } from "$components/enums/ContactMethodType"
  import { HCaptchaFailureType } from "$components/enums/HCaptchaFailureType"

  export type Props = {
    type: ContactVerificationType
    serviceCalls: ContactVerificationServiceCalls
    sessionIdGetter?: SessionIdGetter
    i18n: Readable<I18nGetter>
    hCaptcha: { siteKey: string } | null // Disabled when `null`
  } & Testable &
    // This component inherits part of the Modal.Root interface
    (ModalTriggerProps | ModalControlProps)

  let {
    type,
    serviceCalls,
    sessionIdGetter,
    i18n,
    hCaptcha: hCaptchaProps,
    onClose: externalOnClose,
    ...restProps
  }: Props = $props()

  let currentPage = $state<ContactVerificationStep>(ContactVerificationStep.ENTER_CONTACT_METHOD)
  let isLoading = $state(false)
  let contactMethod = $state<ContactMethod>({ type: ContactMethodType.EMAIL, value: "" })
  let hCaptcha: HCaptcha | undefined = $state()
  let codeValue = $state("")

  // ************************************
  // ******* Internationalization *******
  // ************************************

  type ContactVerificationDictionary = {
    enterContactTitle: string
    enterContactButton: string
    codeTitle: string
    codeLabel1: string
    codeLabel2: string
    codeButton: string
    codeResendDesc: string
    codeResendLink: string
    emailSentTitle: string
    emailSentDesc1: string
    emailSentDesc2: string
    emailSentSpam: string
    emailResendDesc: string
    emailResendLink: string
  }

  // Split login and signup to set their dicts when needed
  let i18nDict: ContactVerificationDictionary = $derived.by(() => {
    switch (type) {
      case ContactVerificationType.LOGIN:
        return {
          enterContactTitle: $i18n("email-input.existing-email"),
          enterContactButton: $i18n("email-input.login"),
          codeTitle: $i18n("sms-verification.title"),
          codeLabel1: $i18n("sms-verification.description-start"),
          codeLabel2: $i18n("sms-verification.description-end"),
          codeButton: $i18n("button.submit"),
          codeResendDesc: $i18n("sms-verification.resend-description"),
          codeResendLink: $i18n("sms-verification.resend-link"),
          emailSentTitle: $i18n("email-verification.title"),
          emailSentDesc1: $i18n("email-verification.description-start"),
          emailSentDesc2: $i18n("email-verification.description-end-login"),
          emailSentSpam: $i18n("email-verification.spam-reminder"),
          emailResendDesc: $i18n("email-verification.resend-description"),
          emailResendLink: $i18n("email-verification.resend-link"),
        }
      case ContactVerificationType.ADD_CONTACT:
        return {
          enterContactTitle: $i18n("email-input.new-email"),
          enterContactButton: $i18n("email-input.add-contact"),
          codeTitle: $i18n("sms-verification.title"),
          codeLabel1: $i18n("sms-verification.description-start"),
          codeLabel2: $i18n("sms-verification.description-end"),
          codeButton: $i18n("button.submit"),
          codeResendDesc: $i18n("sms-verification.resend-description"),
          codeResendLink: $i18n("sms-verification.resend-link"),
          emailSentTitle: $i18n("email-verification.title"),
          emailSentDesc1: $i18n("email-verification.description-start"),
          emailSentDesc2: $i18n("email-verification.description-end-add-contact"),
          emailSentSpam: $i18n("email-verification.spam-reminder"),
          emailResendDesc: $i18n("email-verification.resend-description"),
          emailResendLink: $i18n("email-verification.resend-link"),
        }
    }
  })

  // ************************************
  // ********** Error Handling **********
  // ************************************

  let errorModalContent = $state<ErrorData>() // Modal appears when this value is defined

  // Called when the user wants to close/dismiss the error modal
  const errorModalCloseHandler = () => {
    errorModalContent = undefined
    codeValue = ""
    isLoading = false
  }

  // ************************************
  // ********** Button Actions **********
  // ************************************

  // Shim for the `onClose` callback to add additional logic
  const internalOnClose = () => {
    // Reset state when the modal is closed
    currentPage = ContactVerificationStep.ENTER_CONTACT_METHOD
    isLoading = false
    contactMethod = { type: ContactMethodType.EMAIL, value: "" }
    codeValue = ""
    errorModalContent = undefined

    // Delegate
    externalOnClose?.()
  }

  let codeSubmitAction = $derived(
    serviceCalls.submitSmsVerificationCode.type === FormActionType.FORM_SUBMISSION
      ? serviceCalls.submitSmsVerificationCode.url
      : undefined,
  )

  const getHCaptchaToken = async (): Promise<string | undefined> => {
    if (hCaptcha !== undefined) {
      return (await hCaptcha.executeAsync()).match(
        (val) => val,
        (err) => {
          switch (err) {
            case HCaptchaFailureType.CHALLENGE_CLOSED: {
              errorModalContent = {
                title: "siwa.error.modal.title.captcha-not-satisfied",
                description: "siwa.error.modal.desc.captcha-not-satisfied",
              }
              return undefined
            }
            default: {
              errorModalContent = {
                title: "siwa.error.modal.title.resend-captcha",
                description: "siwa.error.modal.desc.resend-captcha",
              }
              return undefined
            }
          }
        },
      )
    } else return undefined
  }

  const verifyContactClicked = async () => {
    isLoading = true

    const token = await getHCaptchaToken()
    if (hCaptcha !== undefined && token === undefined) {
      // Stop early if hCaptcha failed
      isLoading = false
      return
    }

    const result = await serviceCalls.initiateOrResendContactVerification(contactMethod, token)

    result.orTee((error) => {
      errorModalContent = error
    })
    if (result.isOk()) {
      currentPage = ContactVerificationStep.AUTHENTICATION_SENT
    }
    isLoading = false
  }

  const codeSubmitClicked = async (event) => {
    isLoading = true
    if (serviceCalls.submitSmsVerificationCode.type == FormActionType.API_CALL) {
      event.preventDefault()
      const sessionId = sessionIdGetter?.()
      if (!sessionId) {
        throw new Error(`No sessionIdGetter provided`)
      }
      const result = await serviceCalls.submitSmsVerificationCode.call({
        authenticationCode: codeValue,
        sessionId: sessionId,
      })
      result.orTee((error) => {
        errorModalContent = error
      })
    }
  }
</script>

<Modal.Root onClose={internalOnClose} {...restProps}>
  {#if errorModalContent}
    <div data-testid="contact-verification-modal-error">
      <Modal.Title data-testid="title">
        {$i18n(errorModalContent.title)}
      </Modal.Title>
      <Modal.Description class="pb-8">{$i18n(errorModalContent.description)}</Modal.Description>
      <Button width="full" onClick={errorModalCloseHandler} data-testid="close-button">{$i18n("button.back")}</Button>
    </div>
  {:else if currentPage === ContactVerificationStep.ENTER_CONTACT_METHOD}
    <Modal.Title>{i18nDict.enterContactTitle}</Modal.Title>
    <EnterContactMethodField {i18n} bind:value={contactMethod} />
    <Button width="full" data-testid="contact-verification-submit" loading={isLoading} onClick={verifyContactClicked}>
      {i18nDict.enterContactButton}
    </Button>
  {:else if currentPage === ContactVerificationStep.AUTHENTICATION_SENT}
    {#if contactMethod.type === ContactMethodType.PHONE_NUMBER}
      <Modal.Title>{i18nDict.codeTitle}</Modal.Title>
      <form onsubmit={codeSubmitClicked} method="POST" action={codeSubmitAction} data-testid="sms-code-submit-form">
        <Input
          label={`${i18nDict.codeLabel1} ${contactMethod?.value} ${i18nDict.codeLabel2}`}
          id="add-contact-input"
          name="smsCode"
          type={InputType.CODE}
          bind:inputValue={codeValue}
          data-testid="contact-verification-input-sms-code"
        />
        <div class="inline-flex gap-1 py-10">
          <p>{i18nDict.codeResendDesc}</p>
          <TextButton onClick={verifyContactClicked}>{i18nDict.codeResendLink}</TextButton>
        </div>
        <!--Submit form when in the addContact flow (i.e. when codeSubmit is of type FormSubmission)-->
        <Button width="full" type="submit" data-testid="sms-code-submit" loading={isLoading}
          >{i18nDict.codeButton}
        </Button>
      </form>
    {/if}
    {#if contactMethod.type === ContactMethodType.EMAIL}
      <Modal.Title>{i18nDict.emailSentTitle}</Modal.Title>
      <Modal.Description>
        <p>{`${i18nDict.emailSentDesc1} ${contactMethod?.value} ${i18nDict.emailSentDesc2}`}</p>
        <div class="body-sm text-c-text-hint pt-3 *:inline">
          <p>{i18nDict.emailSentSpam}</p>
          <p>{i18nDict.emailResendDesc}</p>
          <TextButton onClick={verifyContactClicked}>{i18nDict.emailResendLink}</TextButton>
        </div>
      </Modal.Description>
    {/if}
  {/if}

  {#if hCaptchaProps !== null}
    <HCaptcha bind:this={hCaptcha} siteKey={hCaptchaProps.siteKey} />
  {/if}
</Modal.Root>
