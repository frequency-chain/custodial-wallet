<script lang="ts">
  import type { Testable } from "$components/interfaces/Testable"
  import type { Readable } from "svelte/store"
  import type { I18nGetter } from "$lib/i18n"
  import Spinner from "$components/icons/Spinner.svelte"

  export type Props = {
    i18n: Readable<I18nGetter>
  } & Testable

  const { i18n }: Props = $props()
</script>

<div class="m-auto flex h-full flex-col items-center gap-8">
  <Spinner />
  <p>{$i18n("siwa.submission.loading")}</p>
</div>
