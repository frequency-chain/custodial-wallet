<script lang="ts">
  import { onDestroy, onMount } from "svelte"
  import intlTelInput from "intl-tel-input/intlTelInputWithUtils"
  import "intl-tel-input/build/css/intlTelInput.css"
  import { type Testable } from "$components/interfaces/Testable"
  import { cn } from "$lib/utils/util"

  export type Props = {
    id?: string
    value?: string | undefined
    name?: string
    onComplete?: () => void
    borderClasses?: string
  } & Testable
  let {
    id,
    name,
    value = $bindable(),
    borderClasses = "border border-c-border-default rounded-input text-c-text-default",
    ...restProps
  }: Props = $props()

  let phoneInput = $state<intlTelInput.Plugin>()
  let input = $state<HTMLInputElement>()
  let formattedValue = $state("")

  $effect(() => {
    if (formattedValue !== undefined) {
      value = phoneInput?.getNumber()
    }
  })

  onMount(async () => {
    console.log("Attempting to creat phone input")
    phoneInput = intlTelInput(input!!, {
      countryOrder: ["us"],
      initialCountry: "us",
      separateDialCode: true,
      strictMode: true,
      useFullscreenPopup: false,
      containerClass: "body-sm",
    })
  })

  onDestroy(() => phoneInput?.destroy())
</script>

<input
  type="tel"
  class={cn("body-sm relative h-13 w-full", "flex items-center justify-center focus:outline-none", borderClasses)}
  bind:this={input}
  bind:value={formattedValue}
  {id}
  {name}
  {...restProps}
/>

<style>
  :global(:root) {
    --iti-input-padding: 10px;
  }
  :global(.iti__search-input) {
    padding: 16px;
    outline: none;
  }
  :global(.iti__country) {
    padding-top: 16px;
    padding-bottom: 16px;
  }
  :global(.iti__dropdown-content) {
    border-radius: 6px;
  }
  :global(.iti__selected-country-primary) {
    padding: 0 var(--iti-arrow-padding) 0 var(--iti-spacer-horizontal);
  }
</style>
