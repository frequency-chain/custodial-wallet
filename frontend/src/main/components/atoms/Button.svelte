<script lang="ts">
  import { type Snippet } from "svelte"
  import type { Testable } from "$components/interfaces/Testable"
  import { cn } from "$lib/utils/util"
  import SmallSpinner from "$components/icons/SmallSpinner.svelte"

  export type Props = {
    children: Snippet
    disabled?: boolean
    loading?: boolean
    style?: "primary" | "negative" | "secondary" | "destructive"
    width?: "fit" | "full"
    type?: "button" | "submit" | "reset"
    onClick?: () => void
  } & Testable

  let {
    children,
    disabled = false,
    loading = false,
    style = "primary",
    width = "fit",
    onClick = () => {},
    type = "button",
    ...restProps
  }: Props = $props()

  // Dynamic styling based on props
  let innerDisabled = $derived(disabled || loading)
  let _style = $derived(innerDisabled ? "disabled" : style)
  let affordanceClasses = $derived(innerDisabled ? "cursor-not-allowed" : "cursor-pointer")
  let animation = "transition duration-200 ease-out"
  let shadowOnHover = "hover:shadow-button-focus"

  let styleClasses = $derived.by(() => {
    switch (_style) {
      case "primary":
        return cn(
          "bg-c-button-primary text-c-button-primary-text",
          "hover:bg-c-button-primary-focus focus:bg-c-button-primary-focus",
          shadowOnHover,
          animation,
        )
      case "negative":
        return cn(
          "bg-c-button-negative text-c-button-negative-text",
          "hover:bg-c-button-negative-focus focus:bg-c-button-negative-focus",
          "hover:text-c-button-negative-text-focus focus:text-c-button-negative-text-focus",
          animation,
        )
      case "secondary":
        return cn(
          "text-c-button-secondary-text border-c-button-secondary border border-2",
          "hover:border-c-button-secondary-focus hover:text-c-button-secondary-focus",
          "focus:text-c-button-secondary-focus focus:border-c-button-secondary-focus",
          animation,
        )
      case "disabled":
        return "bg-c-button-disabled text-c-button-disabled-text"
      case "destructive":
        return cn(
          "bg-c-button-destructive text-c-button-destructive-text ",
          "hover:bg-c-button-destructive-focus focus:bg-c-button-destructive-focus",
          shadowOnHover,
          animation,
        )
    }
  })
  let widthClasses = $derived(width === "full" ? "w-full" : "")
</script>

<button
  disabled={innerDisabled}
  class={cn(
    "rounded-button-default flex flex-col items-center p-3 px-8 font-sans font-bold",
    affordanceClasses,
    styleClasses,
    widthClasses,
  )}
  {type}
  onclick={() => (!innerDisabled ? onClick?.() : undefined)}
  {...restProps}
>
  <div class={loading === true ? "invisible" : ""}>
    {@render children()}
  </div>
  {#if loading === true}
    <SmallSpinner class="absolute" />
  {/if}
</button>
