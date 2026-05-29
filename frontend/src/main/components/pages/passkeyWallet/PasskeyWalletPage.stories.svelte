<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import { initI18n } from "$lib/i18n"
  import PasskeyWalletPage from "$components/pages/passkeyWallet/PasskeyWalletPage.svelte"
  import { PasskeyWalletPage as PasskeyWalletEnum } from "$components/enums/PasskeyWalletPage"

  const { Story } = defineMeta({
    title: "Pages/PasskeyWallet",
    component: PasskeyWalletPage,
    argTypes: {
      entryPoint: {
        control: { type: "select" },
        label: PasskeyWalletEnum,
      },
    },
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
    "wallet.passkey.confirm.desktop-splash":
      "A seed phrase is a series of words that acts as a backup to access your digital wallet. Keep it offline, secure, and never share it to protect your funds and data.",
    "wallet.passkey.confirm.header": "Is Your Seed Phrase Safe?",
    "wallet.passkey.confirm.text": "Did you really write down your seed phrase and put it in a safe place?",
    "wallet.passkey.confirm.button": "I wrote it down",
    "wallet.passkey.confirm.view-again": "View Phrase Again",
    "siwa.footer.privacy-policy": "Privacy Policy",
    "siwa.footer.terms-of-service": "Terms of Service",
    "siwa.footer.copyright": "2025 Project Liberty LLC",
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
    "wallet.passkey.finished.desktop-splash":
      "Create and control your Social Identity for the Social Web. With your Universal Handle, take your follows and connections to any app. You're in full control - your identity, owned and managed by you.",
    "wallet.passkey.finished.header": "Passkey created!",
    "wallet.passkey.finished.text": "You have successfully created a passkey!",
    "wallet.passkey.finished.button": "Next",
  })
</script>

<Story name="Primary" args={{ entryPoint: PasskeyWalletEnum.SEED_PHRASE }} parameters={{ layout: "fullscreen" }}>
  {#snippet children(args)}
    <PasskeyWalletPage {i18n} {...args} />
  {/snippet}
</Story>
