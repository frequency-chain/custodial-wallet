<script lang="ts">
  import { PinInput, REGEXP_ONLY_DIGITS } from "bits-ui"
  import { type PinInputRootSnippetProps } from "bits-ui"
  import type { Testable } from "$components/interfaces/Testable"
  import { cn } from "$lib/utils/util"

  export type Props = {
    id?: string
    maxlength?: number
    borderClasses?: string
    value?: string
    onComplete?: () => void
    name?: string
  } & Testable

  let {
    id,
    name,
    maxlength = 6,
    borderClasses = "border border-c-border-default rounded-input-default text-c-text-default body-lg",
    value = $bindable(),
    onComplete,
    ...restProps
  }: Props = $props()

  // This takes all focus types from the border and adjusts them to work with our groups
  let fixedBorderClasses = $derived.by(() => {
    return borderClasses.split("focus:").join("group-focus-within/pininput:")
  })

  type CellProps = PinInputRootSnippetProps["cells"][0]
</script>

<div>
  <PinInput.Root
    bind:value
    class="group/pininput text-foreground flex items-center justify-center has-disabled:opacity-30"
    {id}
    {name}
    {maxlength}
    {onComplete}
    pattern={REGEXP_ONLY_DIGITS}
    {...restProps}
  >
    {#snippet children({ cells })}
      <div class="flex gap-2">
        {#each cells as cell}
          {@render Cell(cell)}
        {/each}
      </div>
    {/snippet}
  </PinInput.Root>

  {#snippet Cell(cell: CellProps)}
    <PinInput.Cell
      {cell}
      class={cn("body-lg relative h-13 w-13", "flex items-center justify-center", fixedBorderClasses)}
    >
      {#if cell.char !== null}
        <div>
          {cell.char}
        </div>
      {/if}
      {#if cell.hasFakeCaret}
        <div class="animate-caret-blink pointer-events-none absolute inset-0 flex items-center justify-center">
          <div class="bg-c-text-default h-8 w-px"></div>
        </div>
      {/if}
    </PinInput.Cell>
  {/snippet}
</div>
