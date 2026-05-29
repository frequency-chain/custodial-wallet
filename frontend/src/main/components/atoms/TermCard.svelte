<script lang="ts">
  import { type Snippet } from "svelte"
  import type { Testable } from "$components/interfaces/Testable"
  import { cn } from "$lib/utils/util"

  export type Props = {
    title: String
    background: String
    img: Snippet<[string]>
    desc: Snippet
  } & Testable

  let { title, background, img, desc, ...restProps }: Props = $props()

  const border = "rounded-sb-xl border-c-border-default border-1 px-5 pb-5 pt-5"
  const imgSize = "size-3.5"
</script>

<div class={cn(border, background)} {...restProps}>
  <div class="flex flex-row-reverse items-center justify-between pb-2 lg:flex-col lg:items-start">
    <div class="ml-8 *:size-12 lg:m-0 lg:pb-2 lg:*:size-26">
      {@render img(imgSize)}
    </div>
    <h4>{title}</h4>
  </div>
  <div class="">
    {@render desc()}
  </div>
</div>
