<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import TelephoneInput from "$components/atoms/telephoneInput/TelephoneInput.svelte"
  const { Story } = defineMeta({
    title: "Atoms/TelephoneInput",
    component: TelephoneInput,
  })
  let inputValue = $state("")
</script>

<Story name="Phone Input">
  {#snippet children(args)}
    <p class="body-lg pb-2">Value of Input is: {inputValue}</p>
    <TelephoneInput {...args} bind:value={inputValue} />
  {/snippet}
</Story>
