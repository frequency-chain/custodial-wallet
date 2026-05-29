<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import CodeInput from "$components/atoms/CodeInput.svelte"

  const { Story } = defineMeta({
    title: "Atoms/CodeInput",
    component: CodeInput,
    argTypes: {
      maxlength: { control: "number" },
    },
  })

  let inputValue = $state("")
</script>

<Story name="Length 6" args={{ maxlength: 6 }}>
  {#snippet children(args)}
    <p class="body-lg pb-2">Value of Input is: {inputValue}</p>
    <CodeInput {...args} bind:value={inputValue} />
  {/snippet}
</Story>
