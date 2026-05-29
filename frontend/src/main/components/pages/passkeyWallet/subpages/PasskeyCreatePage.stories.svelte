<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import { initI18n } from "$lib/i18n"
  import PasskeyCreatePage from "$components/pages/passkeyWallet/subpages/PasskeyCreatePage.svelte"

  const { Story } = defineMeta({
    title: "Pages/PasskeyWallet/PasskeyCreate",
    component: PasskeyCreatePage,
    args: {},
  })

  // ************************************
  // *********** I18N Service ***********
  // ************************************

  const i18n = initI18n({
    "wallet.passkey.create.desktop-splash":
      "A passkey makes your account more secure and easier to access. No need to remember long passwords - just use your fingerprint, face ID, or PIN to log in quickly. It also protects you from hacking and phishing.",
    "wallet.passkey.create.header": "Create a Passkey",
    "wallet.passkey.create.set-up.title": "Set Up Your Passkey!",
    "wallet.passkey.create.set-up.desc": "Now, let's set up a passkey to make accessing your wallet easy and secure.",
    "wallet.passkey.create.benefit.title": "Benefits of a Passkey:",
    "wallet.passkey.create.benefit.list1":
      "Quick Access: your device can use your FaceID, fingerprint, or a PIN to access your wallet without entering your recovery phrase. Learn More",
    "wallet.passkey.create.benefit.list2": "Enhanced Security: Protects your wallet from unauthorized access.",
    "wallet.passkey.create.button": "Next",

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
    <PasskeyCreatePage {i18n} {buttonAction} />
  {/snippet}
</Story>
