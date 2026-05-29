<script lang="ts">
  import type { Testable } from "$components/interfaces/Testable"
  import type { ContactMethod } from "$components/interfaces/ContactMethod"
  import Input from "$components/atoms/Input.svelte"
  import type { Readable } from "svelte/store"
  import type { I18nGetter } from "$lib/i18n"
  import { InputType } from "$components/enums/InputType"
  import { ContactMethodType } from "$components/enums/ContactMethodType"
  import TextButton from "$components/atoms/TextButton.svelte"

  export type Props = {
    i18n: Readable<I18nGetter>
    value?: ContactMethod | undefined
  } & Testable

  let { i18n, value = $bindable(), ...restProps }: Props = $props()

  let emailInputValue = $state("")
  let phoneInputValue = $state("")
  let method = $state<ContactMethodType>(ContactMethodType.EMAIL)

  // NOTE(Julian, 2025-10-06): There is currently no elegant way to expose a derived value
  // See: https://github.com/sveltejs/svelte/issues/16593
  $effect(() => {
    switch (method) {
      case ContactMethodType.EMAIL: {
        value = { type: method, value: emailInputValue }
        break
      }
      case ContactMethodType.PHONE_NUMBER: {
        value = { type: method, value: phoneInputValue ?? "" }
        break
      }
    }
  })

  // Updates `method` and resets all inputs
  const toggleMethod = () => {
    switch (method) {
      case ContactMethodType.EMAIL: {
        method = ContactMethodType.PHONE_NUMBER
        emailInputValue = ""
        break
      }
      case ContactMethodType.PHONE_NUMBER: {
        method = ContactMethodType.EMAIL
        break
      }
    }
  }
</script>

<div class="flex flex-col gap-2" {...restProps}>
  {#if method === ContactMethodType.EMAIL}
    <Input
      label={$i18n("email-input.email")}
      isRequired={true}
      id="contact-verification-input-email"
      type={InputType.TEXT}
      bind:inputValue={emailInputValue}
      data-testid="contact-verification-email-input"
    />
  {/if}
  {#if method === ContactMethodType.PHONE_NUMBER}
    <Input
      label={$i18n("sms-input.phone-number")}
      isRequired={true}
      id="contact-verification-input-sms"
      type={InputType.PHONE}
      bind:inputValue={phoneInputValue}
      data-testid="phone-form"
    />
  {/if}
  <!-- Toggle button -->
  <div class="text-right">
    <TextButton onClick={toggleMethod} data-testid="switch-button">
      {#if method === ContactMethodType.EMAIL}
        {$i18n("email-input.use-phone")}
      {/if}
      {#if method === ContactMethodType.PHONE_NUMBER}
        {$i18n("sms-input.use-email")}
      {/if}
    </TextButton>
  </div>
</div>
