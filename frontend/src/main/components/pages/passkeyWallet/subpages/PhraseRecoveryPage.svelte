<script lang="ts">
  import { type Readable } from "svelte/store"
  import { type I18nGetter } from "$lib/i18n"
  import SiwaPageOutline from "$components/features/siwa/SiwaPageOutline.svelte"

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
    <div>
      <!-- TODO() -->
      {buttonAction}
      <!-- just to get it to stop complaining about not used -->
    </div>
  {/snippet}
</SiwaPageOutline>
