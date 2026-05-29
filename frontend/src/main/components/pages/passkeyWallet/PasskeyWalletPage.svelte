<script lang="ts">
  import { type Readable } from "svelte/store"
  import { type I18nGetter } from "$lib/i18n"
  import SeedPhrasePage from "$components/pages/passkeyWallet/subpages/SeedPhrasePage.svelte"
  import { PasskeyWalletPage } from "$components/enums/PasskeyWalletPage"
  import PhraseRecoveryPage from "$components/pages/passkeyWallet/subpages/PhraseRecoveryPage.svelte"
  import WalletConfirmPage from "$components/pages/passkeyWallet/subpages/WalletConfirmPage.svelte"
  import PasskeyCreatePage from "$components/pages/passkeyWallet/subpages/PasskeyCreatePage.svelte"
  import PasskeyFinishPage from "$components/pages/passkeyWallet/subpages/PasskeyFinishPage.svelte"

  interface Props {
    i18n: Readable<I18nGetter>
    entryPoint?: PasskeyWalletPage
  }

  let { i18n, entryPoint = PasskeyWalletPage.SEED_PHRASE }: Props = $props()

  // ************************************
  // ********** Page Switching **********
  // ************************************

  // For the recovery path, the PHRASE_RECOVERY Page is designated as the entry point
  let currentPage = $derived(entryPoint)
  const nextPage = () => {
    switch (currentPage) {
      case PasskeyWalletPage.SEED_PHRASE:
      case PasskeyWalletPage.PHRASE_RECOVERY:
        currentPage = PasskeyWalletPage.WALLET_CONFIRM
        break
      case PasskeyWalletPage.WALLET_CONFIRM:
        currentPage = PasskeyWalletPage.PASSKEY_CREATE
        break
      case PasskeyWalletPage.PASSKEY_CREATE:
        currentPage = PasskeyWalletPage.PASSKEY_FINISH
        break
      case PasskeyWalletPage.PASSKEY_FINISH:
        break
    }
  }

  // ************************************
  // ********** Button Actions **********
  // ************************************

  function seedPhraseButton(): void {
    //TODO Wallet Seed Phrase Iframe stuff
    nextPage()
  }

  function phraseRecoveryButton(): void {
    //TODO Wallet Recovery Iframe stuff
    nextPage()
  }

  function walletConfirmButton(): void {
    //TODO Wallet Confirm Phrase Iframe stuff
    nextPage()
  }

  function passkeyCreateButton(): void {
    //TODO Create Passkey stuff
    nextPage()
  }

  function passkeyFinishButton(): void {
    //TODO Passkey Finished stuff
    nextPage()
  }

  function openIframe(): void {
    //TODO Re-open Iframe for use re-viewing seed phrase on confirm page
  }
</script>

<div>
  <div>
    <!-- TODO(Figure out the iframe stuff and how it fits with svelte) -->
    <!-- ??? <IframePasskeyWallet /> ??? -->
  </div>
  {#if currentPage === PasskeyWalletPage.SEED_PHRASE}
    <div>
      <SeedPhrasePage {i18n} buttonAction={seedPhraseButton} />
    </div>
  {:else if currentPage === PasskeyWalletPage.PHRASE_RECOVERY}
    <div>
      <PhraseRecoveryPage {i18n} buttonAction={phraseRecoveryButton} />
    </div>
  {:else if currentPage === PasskeyWalletPage.WALLET_CONFIRM}
    <div>
      <WalletConfirmPage {i18n} buttonAction={walletConfirmButton} {openIframe} />
    </div>
  {:else if currentPage === PasskeyWalletPage.PASSKEY_CREATE}
    <div>
      <PasskeyCreatePage {i18n} buttonAction={passkeyCreateButton} />
    </div>
  {:else if currentPage === PasskeyWalletPage.PASSKEY_FINISH}
    <div>
      <PasskeyFinishPage {i18n} buttonAction={passkeyFinishButton} />
    </div>
  {/if}
</div>
