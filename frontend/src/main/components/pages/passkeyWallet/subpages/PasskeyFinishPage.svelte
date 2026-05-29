<script lang="ts">
  import { type Readable } from "svelte/store"
  import { type I18nGetter } from "$lib/i18n"
  import SiwaPageOutline from "$components/features/siwa/SiwaPageOutline.svelte"
  import Button from "$components/atoms/Button.svelte"
  import CircledCheckmark from "$components/icons/CircledCheckmark.svelte"
  import { CircledCheckmarkColor } from "$components/enums/CircledCheckmarkColor"

  interface Props {
    i18n: Readable<I18nGetter>
    buttonAction: () => void
  }

  let { i18n, buttonAction }: Props = $props()
</script>

<SiwaPageOutline {i18n}>
  {#snippet splash()}
    <p>
      {$i18n("wallet.passkey.finished.desktop-splash")}
    </p>
  {/snippet}
  {#snippet title()}
    <h4>{$i18n("wallet.passkey.finished.header")}</h4>
  {/snippet}

  {#snippet children()}
    <div class="text-center">
      <CircledCheckmark
        class="passkey-wallet-checkmark inline-block size-1/4 pb-4"
        color={CircledCheckmarkColor.GREEN}
      />
      <div class="passkey-wallet-text mb-6">
        <p>{$i18n("wallet.passkey.finished.text")}</p>
      </div>
    </div>
    <div class="content-center py-4">
      <Button width="full" onClick={buttonAction} data-testid="passkey-finished-submit">
        {$i18n("wallet.passkey.finished.button")}
      </Button>
    </div>
  {/snippet}
</SiwaPageOutline>
