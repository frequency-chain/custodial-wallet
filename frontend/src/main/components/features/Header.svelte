<script lang="ts">
  import { type Readable } from "svelte/store"
  import { type I18nGetter } from "$lib/i18n"
  import { cn } from "$lib/utils/util"
  import { slide } from "svelte/transition"
  import FrequencyLogo from "$components/icons/FrequencyLogo.svelte"
  import ContactVerification from "$components/features/ContactVerification.svelte"
  import { ContactVerificationType } from "$components/enums/ContactVerificationType"
  import Button from "$components/atoms/Button.svelte"
  import HamburgerButton from "$components/atoms/HamburgerButton.svelte"
  import { type HeaderButtonAction } from "$components/interfaces/HeaderButtonAction"
  import { HeaderButtonActionType } from "$components/enums/HeaderButtonActionType"

  interface Props {
    i18n: Readable<I18nGetter>
    buttonAction?: HeaderButtonAction
  }

  const { i18n, buttonAction }: Props = $props()

  let mobileMenuOpen = $state(false)
  let loginModalOpen = $state(false)

  let accountButtonOnClickFactory = (type: HeaderButtonActionType) => () => {
    switch (type) {
      case HeaderButtonActionType.NAVIGATE:
        window.location.href = "/web/login/resume"
        return
      case HeaderButtonActionType.LOGIN:
        loginModalOpen = true
        return
      case HeaderButtonActionType.LOGOUT:
        window.location.href = "/web/logout"
        return
    }
  }

  const getAccountButtonTitle = (type: string) => {
    switch (type) {
      case HeaderButtonActionType.NAVIGATE:
        return $i18n("website-header.account")
      case HeaderButtonActionType.LOGIN:
        return $i18n("website-header.login")
      case HeaderButtonActionType.LOGOUT:
        return $i18n("account.logout")
    }
  }
</script>

{#snippet headerItems(headerLinkClasses: String, loginButtonTestId: String)}
  <a href="/" target="_self" class={headerLinkClasses}>
    {$i18n("website-header.home")}
  </a>
  <a href="https://projectliberty.zendesk.com/" target="_blank" class={headerLinkClasses}>
    {$i18n("website-header.faqs")}
  </a>
  {#if buttonAction !== undefined}
    <div class="content-center">
      <Button
        width="full"
        type="button"
        data-testid={loginButtonTestId.toString()}
        style={"primary"}
        onClick={accountButtonOnClickFactory(buttonAction.type)}
      >
        {getAccountButtonTitle(buttonAction.type)}
      </Button>
    </div>
  {/if}
{/snippet}

<div class={"flex"}>
  <div class={"m-auto mx-3 flex h-16 min-h-25 w-full flex-row justify-between md:mx-28 md:h-24"}>
    <a href="/" class={"no-materialize self-center"}>
      <FrequencyLogo class={cn("text-c-button-primary w-[146px]", mobileMenuOpen ? "hidden" : "")} />
    </a>
    <div class="hidden items-center md:visible md:flex">
      {@render headerItems("text-c-button-primary content-center px-4 font-bold no-materialize", "account-button")}
    </div>
    <HamburgerButton
      isOpen={mobileMenuOpen}
      onClick={() => (mobileMenuOpen = !mobileMenuOpen)}
      class={"z-50 cursor-pointer md:hidden"}
    />
    {#if mobileMenuOpen}
      <div
        class={"bg-c-white fixed top-0 left-0 z-5 h-full w-full flex-col justify-between overflow-y-scroll px-4"}
        transition:slide={{ duration: 300 }}
      >
        <div class="mt-20 flex flex-col">
          {@render headerItems(
            "heading-4 text-c-button-primary font-bold pb-8 no-materialize",
            "account-button-mobile",
          )}
        </div>
      </div>
    {/if}
  </div>
  {#if buttonAction?.type === HeaderButtonActionType.LOGIN}
    <ContactVerification
      isOpen={loginModalOpen}
      handleClose={() => (loginModalOpen = false)}
      serviceCalls={buttonAction.serviceCalls}
      sessionIdGetter={buttonAction.sessionIdGetter}
      {i18n}
      type={ContactVerificationType.LOGIN}
      hCaptcha={buttonAction.hCaptcha}
    />
  {/if}
</div>
