<script lang="ts">
  import type { Testable } from "$components/interfaces/Testable"
  import { cn } from "$lib/utils/util"

  export type Props = {
    isOpen: boolean
    style?: "primary" | "negative"
    onClick?: () => void
    class?: string
  } & Testable

  let { style, isOpen, onClick, ...restProps }: Props = $props()

  let styleClasses = $derived.by(() => {
    switch (style) {
      case "primary":
        return "stroke-c-button-primary"
      case "negative":
        return "stroke-c-button-negative"
    }
  })
</script>

<button class="cursor-pointer" onclick={() => onClick?.()} aria-label={isOpen ? "Close" : "Open"} {...restProps}>
  <svg class="h-[40px] w-[40px] stroke-current" role="none">
    <line
      x1="5%"
      y1="50%"
      x2="95%"
      y2="50%"
      stroke-linecap="round"
      stroke-linejoin="round"
      class={cn(
        "origin-left translate-y-[25%] transform stroke-[10%] transition-all duration-[0.3s]",
        isOpen ? "translate-x-[8px] translate-y-[35%] -rotate-45" : "",
        styleClasses,
      )}
    />
    <line
      x1="5%"
      y1="50%"
      x2="95%"
      y2="50%"
      stroke-linecap="round"
      stroke-linejoin="round"
      class={cn(
        "origin-center transform stroke-[10%] transition-all duration-[0.3s]",
        isOpen ? "scale-x-0" : "",
        styleClasses,
      )}
    />
    <line
      x1="5%"
      y1="50%"
      x2="95%"
      y2="50%"
      stroke-linecap="round"
      stroke-linejoin="round"
      class={cn(
        "origin-left translate-y-[-25%] transform stroke-[10%] transition-all duration-[0.3s]",
        isOpen ? "translate-x-[8px] translate-y-[-35%] rotate-45" : "",
        styleClasses,
      )}
    />
  </svg>
</button>
