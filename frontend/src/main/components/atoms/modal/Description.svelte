<script lang="ts">
  import { type Snippet } from "svelte"
  import type { Testable } from "$components/interfaces/Testable"
  import { cn } from "$lib/utils/util"

  export type Props = {
    children: Snippet
    class?: string
  } & Testable

  let { children, class: additionalClasses, ...restProps }: Props = $props()
</script>

<p class={cn("text-center", additionalClasses)} {...restProps}>
  {@render children()}
</p>
