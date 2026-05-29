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

<div class="p-5 text-center text-xs">
  <div class="pb-2">
    <a href="/privacy.html" target="_blank" class="pr-2">
      <TextButton>{$i18n("siwa.footer.privacy-policy")}</TextButton>
    </a>
    <span class="border-b-c-border-default border-r-[1px]"></span>
    <a href="/terms.html" target="_blank" class="pl-2">
      <TextButton>{$i18n("siwa.footer.terms-of-service")}</TextButton>
    </a>
  </div>
  <div>
    <p>© {$i18n("siwa.footer.copyright")}</p>
  </div>
</div>
