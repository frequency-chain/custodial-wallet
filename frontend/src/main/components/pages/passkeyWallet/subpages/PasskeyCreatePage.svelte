<script lang="ts">
  import { type Readable } from "svelte/store"
  import { type I18nGetter } from "$lib/i18n"
  import SiwaPageOutline from "$components/features/siwa/SiwaPageOutline.svelte"
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
      {$i18n("wallet.passkey.create.desktop-splash")}
    </p>
  {/snippet}
  {#snippet title()}
    <h4>{$i18n("wallet.passkey.create.header")}</h4>
  {/snippet}
  {#snippet children()}
    <div>
      <div class="justify-left flex flex-row gap-2 pb-5">
        <h6 class="font-bold">
          {$i18n("wallet.passkey.create.set-up.title")}
        </h6>
      </div>
      <div class="pb-5">
        <p>{$i18n("wallet.passkey.create.set-up.desc")}</p>
      </div>
      <div class="pb-5">
        <h6 class="font-bold">{$i18n("wallet.passkey.create.benefit.title")}</h6>
        <ul class="list-disc py-4 pl-8">
          <li>{$i18n("wallet.passkey.create.benefit.list1")}</li>
          <li>{$i18n("wallet.passkey.create.benefit.list2")}</li>
        </ul>
      </div>
      <div class="py-8">
        <Button width="full" onClick={buttonAction} data-testid="passkey-create-button">
          {$i18n("wallet.passkey.create.button")}
        </Button>
      </div>
    </div>
  {/snippet}
</SiwaPageOutline>
