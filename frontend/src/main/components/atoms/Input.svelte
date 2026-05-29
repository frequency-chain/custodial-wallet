<script lang="ts">
  import { InputType } from "$components/enums/InputType"
  import CodeInput from "$components/atoms/CodeInput.svelte"
  import TelephoneInput from "$components/atoms/telephoneInput/TelephoneInput.svelte"
  import type { Testable } from "$components/interfaces/Testable"
  import { cn } from "$lib/utils/util"
  import Label from "$components/atoms/Label.svelte"

  export type Props = {
    id?: string
    name?: string
    label?: string
    isRequired?: boolean
    hint?: string
    disabled?: boolean
    width?: "fit" | "full"
    alignment?: "left" | "center" // Justifies the text in `input`
    //HTML input types
    type?: InputType
    placeholder?: string
    errorMessage?: string
    inputValue?: string
    maxlength?: number
    onComplete?: () => void
  } & Testable

  let {
    id,
    name,
    label,
    isRequired,
    hint,
    disabled = false,
    width = "fit",
    alignment = "left",
    type = InputType.TEXT,
    placeholder,
    errorMessage,
    //NOTE: (Aziz, 5/9/24) followed the svelte docs for this, but in general we want to use binding with caution.
    // Reason being is that by using $bindable we're mutating state from the child to the parent,
    // which can create side effects that can create unpredictability in overall state.
    // Using this for input components should be safe.
    inputValue = $bindable(),
    maxlength,
    onComplete,
    ...restProps
  }: Props = $props()

  let inputBorderColorClasses = $derived(
    errorMessage
      ? "border-c-border-error ring-1 ring-c-border-error"
      : "border-c-border-default focus:border-c-border-focus focus:ring-1 focus:ring-c-border-focus ",
  )

  let inputBorderDefaultShape = "border border-c-border-default rounded-input-default"
</script>

<div class={cn("flex flex-col space-y-1", width === "full" ? "w-full" : "")}>
  {#if label}
    <Label text={label} {hint} {isRequired} for={id}>
      {@render input()}
    </Label>
  {:else}
    {@render input()}
  {/if}

  {#if errorMessage}
    <div class="text-c-text-error text-left font-bold" data-testid={restProps["data-testid"]}>
      {errorMessage}
    </div>
  {/if}
</div>

{#snippet input()}
  {#if type === InputType.CODE}
    <CodeInput
      {id}
      {name}
      {maxlength}
      {onComplete}
      bind:value={inputValue}
      borderClasses={cn(inputBorderDefaultShape, inputBorderColorClasses)}
      {...restProps}
    />
  {:else if type === InputType.PHONE}
    <TelephoneInput
      {id}
      {name}
      {onComplete}
      bind:value={inputValue}
      borderClasses={cn(inputBorderDefaultShape, inputBorderColorClasses)}
      {...restProps}
    />
  {:else}
    <input
      class={cn(
        "body-sm h-13 w-full px-3 focus:outline-none",
        alignment === "left" ? "" : "text-center",
        inputBorderDefaultShape,
        inputBorderColorClasses,
      )}
      {id}
      {name}
      {type}
      {placeholder}
      {disabled}
      bind:value={inputValue}
      required={isRequired}
      {...restProps}
    />
  {/if}
{/snippet}
