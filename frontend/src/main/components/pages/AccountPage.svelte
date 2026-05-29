<script lang="ts">
  import { Modal } from "$components/atoms/modal"
  import Button from "$components/atoms/Button.svelte"
  import { type AccountService } from "$lib/services/accountService"
  import { Accordion } from "$components/atoms/accordion"
  import { type ErrorData } from "$lib/types/data/ErrorData"
  import Input from "$components/atoms/Input.svelte"
  import { InputType } from "$components/enums/InputType"
  import { type Readable } from "svelte/store"
  import { type I18nGetter } from "$lib/i18n"
  import { type FeatureFlagService } from "$lib/services/featureFlagService"
  import { UserDetailType } from "$lib/types/data/AccountData"
  import AccountPermission from "$components/features/AccountPermission.svelte"
  import TextButton from "$components/atoms/TextButton.svelte"
  import ContactVerification from "$components/features/ContactVerification.svelte"
  import { ContactVerificationType } from "$components/enums/ContactVerificationType"
  import PageOutline from "$components/features/PageOutline.svelte"
  import Header from "$components/features/Header.svelte"
  import { HeaderButtonActionType } from "$components/enums/HeaderButtonActionType"
  import ChangeHandle from "$components/features/ChangeHandle.svelte"
  import Edit from "$components/icons/Edit.svelte"
  import CircledCheckmark from "$components/icons/CircledCheckmark.svelte"
  import Footer from "$components/features/Footer.svelte"

  interface Props {
    accountService: AccountService
    i18n: Readable<I18nGetter>
    featureFlagService: FeatureFlagService
    contactAdded: boolean
  }

  let { accountService, i18n, featureFlagService, contactAdded }: Props = $props()

  const accountData = accountService.data
  const featureFlagData = featureFlagService.data

  let revokeLoading = $state(false)
  let openSuccessModal = $state(contactAdded)
  let errorModalContent = $state<ErrorData>() // Modal appears when this value is defined

  // Called when the user wants to close/dismiss the error modal
  const errorModalCloseHandler = () => {
    errorModalContent = undefined
  }

  const revokeHandler = async (providerMsaId: number) => {
    revokeLoading = true // Disable the confirm revoke button while waiting for the server to respond

    const result = await accountService.revokeProviderDelegations(providerMsaId)

    // If the revoke operation failed show the user an error modal
    result.orTee((error) => {
      errorModalContent = error
    })

    revokeLoading = false // Re-enable the confirm revoke button
  }

  // Password handling
  let newPassword = $state("")
  let retypedNewPassword = $state("")
  let isChangePasswordSuccess = $state(false)

  //NOTE: (Aziz 5/20/25) Need to figure out formal approach to form validation as a whole, ideally would add some
  // feedback of some sort for user in real time for password validation
  const changePasswordHandler = async () => {
    isChangePasswordSuccess = false
    const result = await accountService.changePassword({
      userAccountId: $accountData.userAccountId,
      newRawPassword: newPassword,
    })

    result.orTee((error) => {
      errorModalContent = error
    })

    if (result.isOk()) {
      isChangePasswordSuccess = true
      newPassword = ""
      retypedNewPassword = ""
    }
  }
</script>

<Header {i18n} buttonAction={{ type: HeaderButtonActionType.LOGOUT }} />
<PageOutline>
  {#snippet title()}
    <div class="flex flex-col items-center">
      <div
        class="bg-c-avatar-default rounded-avatar-default flex h-16 w-16 items-center justify-center lg:h-24 lg:w-24"
      >
        <span data-testId="user-handle-first-letter" class="text-2xl font-bold lg:text-4xl">J</span>
      </div>
      <div class="flex flex-row items-center gap-1 pt-8 pb-2">
        <h5 class="h5">@{$accountData.handle}</h5>

        {#if $featureFlagData.changeHandleEnabled === true}
          <ChangeHandle style={"modal"} {i18n} changeHandle={accountService.updateHandle}>
            {#snippet trigger(open)}
              <TextButton onClick={open} data-testid="change-handle-button">
                <Edit />
              </TextButton>
            {/snippet}
          </ChangeHandle>
        {/if}
      </div>

      <p>{$i18n("account.msa")}{$accountData.msaId}</p>
    </div>
  {/snippet}

  <div class="md:px-15">
    <h5 class="heading-5 pb-1">
      {$i18n("account.contact")}
    </h5>
    {#if $accountData.userDetails[UserDetailType.EMAIL]}
      <p class="py-1 font-bold">{$i18n("account.emails")}</p>
      <ul class="pb-1">
        {#each $accountData.userDetails[UserDetailType.EMAIL] as userDetail}
          <li>{userDetail.value}</li>
        {/each}
      </ul>
    {/if}
    {#if $accountData.userDetails[UserDetailType.PHONE_NUMBER]}
      <p class="py-1 font-bold">{$i18n("account.phone-numbers")}</p>
      <ul class="pb-1">
        {#each $accountData.userDetails[UserDetailType.PHONE_NUMBER] as userDetail}
          <li>{userDetail.value}</li>
        {/each}
      </ul>
    {/if}
    {#if $featureFlagData.addContactEnabled === true}
      <div class="py-4">
        <ContactVerification
          serviceCalls={accountService.addContactServiceCalls}
          {i18n}
          type={ContactVerificationType.ADD_CONTACT}
          data-testid="add-contact-modal"
          hCaptcha={null}
        >
          {#snippet trigger(open)}
            <TextButton onClick={open} data-testid="add-contact-button" class="font-bold">
              {$i18n("account.add-contact")}
            </TextButton>
          {/snippet}
        </ContactVerification>
      </div>
    {/if}
    <h5 class="heading-5 py-2">
      {$i18n("account.providers")}
    </h5>
    <Accordion.Root type="single" data-testid="view-provider-details">
      {#each $accountData.providers as provider}
        <Accordion.Item id={`${provider.msaId}`} title={provider.name}>
          <div class="flex-col space-y-6">
            <p class="mt-2">
              <span class="font-bold">{$i18n("account.handle")}: </span>
              {$accountData.handle}
            </p>
            <div>
              <p class="mb-3 font-bold">
                {$i18n("account.permissions")}
              </p>
              <ul class="flex flex-col gap-4 px-4">
                {#each provider.permissions as permission}
                  <AccountPermission key={permission} {i18n} />
                {/each}
              </ul>
            </div>
            {#if provider.permissions.length > 0}
              <Modal.Root>
                {#snippet trigger(open)}
                  <Button onClick={open} data-testid="revoke-button">
                    {$i18n("account.revoke-button")}
                  </Button>
                {/snippet}
                {#snippet children(close)}
                  <Modal.Title>
                    {$i18n("revoke-modal.title")}
                  </Modal.Title>
                  <Modal.Description>
                    {$i18n("revoke-modal.confirmation-message")}
                  </Modal.Description>
                  <Button
                    style={"destructive"}
                    disabled={revokeLoading}
                    width="full"
                    onClick={async () => {
                      await revokeHandler(provider.msaId)
                      // The modal stays open until the revoke request resolves
                      close()
                    }}
                    data-testid="confirm-revoke"
                  >
                    {$i18n("revoke-modal.revoke")}
                  </Button>
                {/snippet}
              </Modal.Root>
            {/if}
          </div>
        </Accordion.Item>
      {/each}
    </Accordion.Root>

    <!-- Change Password Section -->
    {#if $featureFlagData.passwordEnabled === true && $accountData.hasPassword === true}
      <h5 class="heading-5 py-4">{$i18n("account.password.title")}</h5>
      <div class="flex-col space-y-6">
        {@render ChangePasswordButton()}
      </div>
    {/if}

    <!-- Passkey Wallet Section -->
    {#if $featureFlagData.passkeyWalletEnabled === true}
      <h5 class="pt-2">{$i18n("account.passkey-wallet.title")}</h5>
      {#if $accountData.hasPasskeyWallet}
        <p class="py-3">{$i18n("account.passkey-wallet.exists")}</p>
        <form id="account-passkey-wallet-recovery" method="get" action="/wallet/recovery">
          <div class="body-sm">
            <TextButton type="submit" data-testid="account-passkey-wallet-recovery-submit"
              >{$i18n("account.passkey-wallet.recovery.button")}</TextButton
            >
          </div>
        </form>
      {:else}
        <p class="py-3">{$i18n("account.passkey-wallet.create")}</p>
        <form id="account-passkey-wallet-form" method="get" action="/wallet/passkey">
          <Button width="fit" type="submit" data-testid="account-passkey-wallet-submit">
            {$i18n("account.passkey-wallet.button")}
          </Button>
        </form>
      {/if}
    {/if}
  </div>
</PageOutline>
<Footer {i18n} />

<!-- Error modal -->
<!-- 'if' block is needed for types to be narrowed correctly -->
{#if errorModalContent}
  <Modal.Root isOpen={errorModalContent !== undefined} handleClose={errorModalCloseHandler}>
    <Modal.Title>
      {$i18n(errorModalContent.title)}
    </Modal.Title>
    <Modal.Description>{$i18n(errorModalContent.description)}</Modal.Description>
    <Button width="full" onClick={errorModalCloseHandler}>{$i18n("button.close")}</Button>
  </Modal.Root>
{/if}

<!-- Add contact success modal -->
<Modal.Root
  isOpen={openSuccessModal}
  handleClose={() => (openSuccessModal = false)}
  data-testid={"add-contact-modal-success"}
>
  {#snippet children()}
    <div>
      <Modal.Title>{$i18n("add-contact-success.title")}</Modal.Title>
      <Modal.Description>
        <div class="text-center *:m-auto">
          <CircledCheckmark />
          <p class="pt-5">{$i18n("add-contact-success.message")}</p>
        </div>
      </Modal.Description>
    </div>
  {/snippet}
</Modal.Root>

<!-- Change Password modal -->
<!-- When change password button is clicked, pops up the entire modal for typing/retyping password and submitting -->
{#snippet ChangePasswordButton()}
  <Modal.Root>
    {#snippet trigger(open)}
      <Button style={"destructive"} onClick={open} data-testid="change-password-button">
        {$i18n("account.change-password-button")}</Button
      >
    {/snippet}
    {#snippet children()}
      <Modal.Title>{$i18n("change-password-modal.title")}</Modal.Title>
      <Input
        label={$i18n("change-password-modal.new")}
        id="account-password-input"
        type={InputType.PASSWORD}
        bind:inputValue={newPassword}
        data-testid="change-password-input"
      />
      <Input
        label={$i18n("change-password-modal.retype")}
        id="account-password-retype"
        type={InputType.PASSWORD}
        bind:inputValue={retypedNewPassword}
        data-testid="change-password-retype"
      />
      <Button
        style={"destructive"}
        width="full"
        data-testid="confirm-change-password-button"
        onClick={async () => {
          await changePasswordHandler()
        }}
        >{$i18n("change-password-modal.button")}
      </Button>
      {#if isChangePasswordSuccess}
        <div data-testid="change-password-changed">
          <p>{$i18n("change-password-modal.changed")}</p>
        </div>
      {/if}
    {/snippet}
  </Modal.Root>
{/snippet}
