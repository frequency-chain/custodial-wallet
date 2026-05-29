<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import { initI18n } from "$lib/i18n"
  import WalletConfirmPage from "$components/pages/passkeyWallet/subpages/WalletConfirmPage.svelte"

  const { Story } = defineMeta({
    title: "Pages/PasskeyWallet/WalletConfirm",
    component: WalletConfirmPage,
    args: {},
  })

  // ************************************
  // *********** I18N Service ***********
  // ************************************

  const i18n = initI18n({
    "wallet.passkey.confirm.desktop-splash":
      "A seed phrase is a series of words that acts as a backup to access your digital wallet. Keep it offline, secure, and never share it to protect your funds and data.",
    "wallet.passkey.confirm.header": "Is Your Seed Phrase Safe?",
    "wallet.passkey.confirm.text": "Did you really write down your seed phrase and put it in a safe place?",
    "wallet.passkey.confirm.button": "I wrote it down",
    "wallet.passkey.confirm.view-again": "View Phrase Again",
    "siwa.footer.privacy-policy": "Privacy Policy",
    "siwa.footer.terms-of-service": "Terms of Service",
    "siwa.footer.copyright": "2025 Project Liberty LLC",
  })

  function buttonAction() {
    console.log("wallet confirm Button Pressed!")
  }

  function openIframe() {
    console.log("view phrase again Button Pressed!")
  }
</script>

<Story name="Primary" parameters={{ layout: "fullscreen" }}>
  {#snippet children()}
    <WalletConfirmPage {i18n} {buttonAction} {openIframe} />
  {/snippet}
</Story>
