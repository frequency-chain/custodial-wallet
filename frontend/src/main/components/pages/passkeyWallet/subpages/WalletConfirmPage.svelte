<script lang="ts">
  import { type Readable } from "svelte/store"
  import { type I18nGetter } from "$lib/i18n"
  import SiwaPageOutline from "$components/features/siwa/SiwaPageOutline.svelte"
  import Warning from "$components/icons/Warning.svelte"
  import CircledCheckmark from "$components/icons/CircledCheckmark.svelte"
  import Button from "$components/atoms/Button.svelte"
  import TextButton from "$components/atoms/TextButton.svelte"
  import { CircledCheckmarkColor } from "$components/enums/CircledCheckmarkColor"

  interface Props {
    i18n: Readable<I18nGetter>
    buttonAction: () => void
    openIframe: () => void
  }

  let { i18n, buttonAction, openIframe }: Props = $props()
</script>

<SiwaPageOutline {i18n}>
  {#snippet splash()}
    <p>
      {$i18n("wallet.passkey.confirm.desktop-splash")}
    </p>
  {/snippet}
  {#snippet title()}
    <h4>{$i18n("wallet.passkey.confirm.header")}</h4>
  {/snippet}
  {#snippet children()}
    <div>
      <div class="flex flex-col items-center gap-5 pb-6">
        <CircledCheckmark color={CircledCheckmarkColor.GREEN} class="w-full md:h-30" />
        <p>{$i18n("wallet.passkey.confirm.text")}</p>
      </div>
      <div class="py-6">
        <Button width="full" onClick={buttonAction} data-testid="wallet-confirm-button">
          {$i18n("wallet.passkey.confirm.button")}
        </Button>
      </div>
      <div class="pb-6 text-center">
        <TextButton onClick={openIframe} data-testid="wallet-confirm-view-again">
          {$i18n("wallet.passkey.confirm.view-again")}
        </TextButton>
      </div>
    </div>
  {/snippet}
</SiwaPageOutline>
