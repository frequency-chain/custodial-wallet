<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import { initI18n } from "$lib/i18n"
  import IframeModal from "$components/pages/passkeyWallet/subpages/IframeModal.svelte"
  import Button from "$components/atoms/Button.svelte"

  const { Story } = defineMeta({
    title: "Pages/PasskeyWallet/IframeModal",
    component: IframeModal,
    args: {},
  })

  // ************************************
  // *********** I18N Service ***********
  // ************************************

  const i18n = initI18n({
    "wallet.passkey.iframe-modal.title.first": "Write Down Your Seed Phrase",
    "wallet.passkey.iframe-modal.title.second": "Passkey Creation",
    "wallet.passkey.iframe-modal.desc.first":
      "This is your seed phrase. Write it down on a paper and keep it in a safe place.",
    "wallet.passkey.iframe-modal.desc.second": "Press the button below to start creating your passkey",
    "wallet.passkey.iframe-modal.close": "Done",
    "wallet.passkey.iframe-modal.create": "Create Your Passkey",
  })

  let isOpen = $state(true)
  let seedPhraseText = $state(true)

  function handleClose() {
    console.log("Close Button Pressed!")
    isOpen = false
  }

  function handleOpen() {
    console.log("Open Button Pressed!")
    isOpen = true
  }

  function handleSwitchText() {
    console.log("Switch Text Button Pressed!")
    seedPhraseText = false
  }
</script>

<Story name="Primary">
  {#snippet children()}
    <div>
      <Button onClick={handleOpen}>Open Modal</Button>
      <Button onClick={handleSwitchText}>Switch Modal Text</Button>
      <IframeModal {i18n} {isOpen} {handleClose} {seedPhraseText}></IframeModal>
    </div>
  {/snippet}
</Story>
