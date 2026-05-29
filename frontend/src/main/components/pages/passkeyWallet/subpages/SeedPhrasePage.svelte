<script lang="ts">
  import { type Readable } from "svelte/store"
  import { type I18nGetter } from "$lib/i18n"
  import SiwaPageOutline from "$components/features/siwa/SiwaPageOutline.svelte"
  import Warning from "$components/icons/Warning.svelte"
  import Button from "$components/atoms/Button.svelte"

  interface Props {
    i18n: Readable<I18nGetter>
    buttonAction: () => void
  }

  let { i18n, buttonAction }: Props = $props()
</script>

<SiwaPageOutline {i18n}>
  {#snippet splash()}
    <p>
      {$i18n("wallet.passkey.phrase.desktop-splash")}
    </p>
  {/snippet}
  {#snippet title()}
    <h4>{$i18n("wallet.passkey.phrase.header")}</h4>
  {/snippet}
  {#snippet children()}
    <div class="justify-left flex flex-row gap-2 pb-5">
      <Warning />
      <h6 class="font-bold">{$i18n("wallet.passkey.phrase.title")}</h6>
    </div>
    <div class="pb-5">
      <p>{$i18n("wallet.passkey.phrase.remember")}</p>
      <ul class="list-disc py-4 pl-8">
        <li>{$i18n("wallet.passkey.phrase.list1")}</li>
        <li>{$i18n("wallet.passkey.phrase.list2")}</li>
      </ul>
      <p class="font-bold">{$i18n("wallet.passkey.phrase.forget")}</p>
    </div>
    <div class="py-8">
      <Button width="full" onClick={buttonAction} data-testid="wallet-phrase-button">
        {$i18n("wallet.passkey.phrase.button")}
      </Button>
    </div>
  {/snippet}
</SiwaPageOutline>
