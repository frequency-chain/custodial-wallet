<script lang="ts">
  import { Asterisk } from "lucide-svelte"
  import type { Testable } from "$components/interfaces/Testable"
  import type { Snippet } from "svelte"
  import { Modal } from "$components/atoms/modal"
  import Info from "$components/icons/Info.svelte"
  import TextButton from "$components/atoms/TextButton.svelte"

  export type Props = {
    text: string
    for?: string
    isRequired?: boolean
    hint?: string
    help?: Snippet // TODO: This should be turned into a popover / tooltip to fully match the design system
    children?: Snippet // E.g., an `<Input />`
  } & Testable

  let { for: inputId, text, isRequired, hint, help, children, ...restProps }: Props = $props()
</script>

<label class="flex flex-col space-y-1" for={inputId} {...restProps}>
  <div class="flex flex-row justify-between">
    <p class="text-left font-bold">
      {text}
      {#if isRequired}
        <span class="text-c-text-error inline-flex leading-none"><Asterisk size={16} /></span>
      {/if}
    </p>
    {#if help}
      <Modal.Root>
        {#snippet trigger(open)}
          <TextButton onClick={open}>
            <Info />
          </TextButton>
        {/snippet}

        {@render help()}
      </Modal.Root>
    {/if}
  </div>
  {#if hint}
    <div class="text-c-text-hint body-sm text-left">{hint}</div>
  {/if}
  {#if children}
    {@render children()}
  {/if}
</label>
