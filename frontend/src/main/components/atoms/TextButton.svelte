<script lang="ts">
  import { type Snippet } from "svelte"
  import type { Testable } from "$components/interfaces/Testable"
  import { clsx } from "clsx"

  export type Props = {
    children: Snippet
    style?: "primary" | "negative"
    disabled?: boolean
    type?: "button" | "submit" | "reset"
    onClick?: (() => void) | (() => Promise<void>)
    class?: string
  } & Testable

  let {
    children,
    disabled = false,
    style = "primary",
    type = "button",
    onClick = () => {},
    class: additionalClasses,
    ...restProps
  }: Props = $props()

  // Dynamic styling based on props
  let _style = $derived(disabled ? "disabled" : style)
  let affordanceClasses = $derived(disabled ? "cursor-not-allowed" : "cursor-pointer")
  let styleClasses = $derived.by(() => {
    switch (_style) {
      case "primary":
        return "text-c-text-button-default hover:text-c-text-button-focus"
      case "negative":
        return "text-c-text-button-negative hover:text-c-text-button-negative-focus"
      case "disabled":
        return "text-c-text-disabled"
    }
  })
</script>

<button
  {disabled}
  class={clsx(
    "underline underline-offset-5 transition duration-200 ease-out",
    affordanceClasses,
    styleClasses,
    additionalClasses,
  )}
  {type}
  onclick={() => onClick?.()}
  {...restProps}
>
  {@render children()}
</button>
