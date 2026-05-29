<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import { initI18n } from "$lib/i18n"
  import SeedPhrasePage from "$components/pages/passkeyWallet/subpages/SeedPhrasePage.svelte"

  const { Story } = defineMeta({
    title: "Pages/PasskeyWallet/SeedPhrase",
    component: SeedPhrasePage,
    args: {},
  })

  // ************************************
  // *********** I18N Service ***********
  // ************************************

  const i18n = initI18n({
    "wallet.passkey.phrase.desktop-splash":
      "A recovery phrase is a crucial component of securing digital wallets and accounts. It serves as a backup that allows users to regain access to their wallet in case of device loss, damage, or other issues.",
    "wallet.passkey.phrase.header": "Recovery Seed Phrase",
    "wallet.passkey.phrase.title": "Write Down Your Recovery Phrase!",
    "wallet.passkey.phrase.remember":
      "This is the only time you'll see your 12-word recovery phrase. It's the master key to your wallet.",
    "wallet.passkey.phrase.list1": "Write it down now and store it securely.",
    "wallet.passkey.phrase.list2": "Never share it with anyone.",
    "wallet.passkey.phrase.forget":
      "If you lose your recovery phrase, you won't be able to access your wallet or manage your Frequency Tokens.",
    "wallet.passkey.phrase.button": "View Recovery Phrase",
    "siwa.footer.privacy-policy": "Privacy Policy",
    "siwa.footer.terms-of-service": "Terms of Service",
    "siwa.footer.copyright": "2025 Project Liberty LLC",
  })

  function buttonAction() {
    console.log("Seed Phrase Button Pressed!")
  }
</script>

<Story name="Primary" parameters={{ layout: "fullscreen" }}>
  {#snippet children()}
    <SeedPhrasePage {i18n} {buttonAction} />
  {/snippet}
</Story>
