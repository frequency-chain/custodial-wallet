<script lang="ts">
  import { type Snippet } from "svelte"
  import type { Testable } from "$components/interfaces/Testable"

  export type Props = {
    children: Snippet
  } & Testable

  let { children, ...restProps }: Props = $props()
</script>

<h2 class="heading-2 text-c-text-default pb-5 text-center" {...restProps}>
  {@render children()}
</h2>
