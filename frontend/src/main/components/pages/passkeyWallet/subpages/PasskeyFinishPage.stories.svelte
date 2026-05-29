<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import { initI18n } from "$lib/i18n"
  import PasskeyFinishPage from "$components/pages/passkeyWallet/subpages/PasskeyFinishPage.svelte"

  const { Story } = defineMeta({
    title: "Pages/PasskeyWallet/PasskeyFinish",
    component: PasskeyFinishPage,
    args: {},
  })

  // ************************************
  // *********** I18N Service ***********
  // ************************************

  const i18n = initI18n({
    "wallet.passkey.finished.desktop-splash":
      "Create and control your Social Identity for the Social Web. With your Universal Handle, take your follows and connections to any app. You're in full control - your identity, owned and managed by you.",
    "wallet.passkey.finished.header": "Passkey created!",
    "wallet.passkey.finished.text": "You have successfully created a passkey!",
    "wallet.passkey.finished.button": "Next",

    // Footer Stuff
    "siwa.footer.privacy-policy": "Privacy Policy",
    "siwa.footer.terms-of-service": "Terms of Service",
    "siwa.footer.copyright": "2025 Project Liberty LLC",
  })

  function buttonAction() {
    console.log("Next Button Pressed!")
  }
</script>

<Story name="Primary" parameters={{ layout: "fullscreen" }}>
  {#snippet children()}
    <PasskeyFinishPage {i18n} {buttonAction} />
  {/snippet}
</Story>
