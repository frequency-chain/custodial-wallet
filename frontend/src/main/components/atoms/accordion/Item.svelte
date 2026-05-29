<script lang="ts">
  import { Accordion } from "bits-ui"
  import CaretRight from "$components/icons/CaretRight.svelte"
  import { type Snippet } from "svelte"
  import { slide } from "svelte/transition"
  import { ACCORDION_SLIDE_DURATION } from "$style/transitionConfig"
  import type { Testable } from "$components/interfaces/Testable"
  import { cn } from "$lib/utils/util"

  export type Props = {
    id: string
    title: string
    titleClass?: string
    children: Snippet
  } & Testable

  let { id, title, titleClass = "body-md", children, ...restProps }: Props = $props()
</script>

<Accordion.Item value={id} class="border-c-border-default border-b " {...restProps}>
  <Accordion.Header>
    <Accordion.Trigger
      class="flex w-full cursor-pointer items-center justify-between py-5 font-medium transition-all [&[data-state=open]>span>svg]:rotate-90"
    >
      <span class={cn("w-full text-left font-bold", titleClass)}>
        {title}
      </span>
      <span class="inline-flex size-8 items-center justify-center bg-transparent">
        <CaretRight class="size-3 transition-transform duration-200" />
      </span>
    </Accordion.Trigger>
  </Accordion.Header>
  <Accordion.Content forceMount={true}>
    {#snippet child({ props, open })}
      {#if open}
        <div {...props} transition:slide={{ duration: ACCORDION_SLIDE_DURATION }} class="body-normal pb-6">
          {@render children()}
        </div>
      {/if}
    {/snippet}
  </Accordion.Content>
</Accordion.Item>
