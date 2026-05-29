<script lang="ts">
  import type { Testable } from "$components/interfaces/Testable"
  import type { Readable } from "svelte/store"
  import type { I18nGetter } from "$lib/i18n"
  import TextButton from "$components/atoms/TextButton.svelte"

  export type Props = {
    i18n: Readable<I18nGetter>
  } & Testable

  const { i18n }: Props = $props()
</script>

<div class="p-5 text-center text-xs lg:px-20 lg:text-right lg:text-sm">
  <div>
    <a href="/privacy.html" target="_blank" class="pr-6 underline lg:pr-8">
      {$i18n("website-footer.pp")}
    </a>
    <span class="border-b-c-border-default border-r-[1px]"></span>
    <a href="/terms.html" target="_blank" class="px-6 underline lg:px-8">
      {$i18n("website-footer.tos")}
    </a>
    <span class="border-b-c-border-default border-r-[1px]"></span>
    <a href="https://projectliberty.zendesk.com" target="_blank" class="pl-6 underline lg:px-8">
      {$i18n("website-footer.helpdesk")}
    </a>
    <span class="border-b-c-border-default border-r-[1px] not-lg:hidden"></span>
    <p class="not-lg:pt-2 lg:inline-block lg:pl-8">© {$i18n("website-footer.copyright")}</p>
  </div>
</div>
