<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import TextButton from "$components/atoms/TextButton.svelte"

  const { Story } = defineMeta({
    title: "Atoms/TextButton",
    component: TextButton,
    args: {
      onClick: () => alert("Button clicked!"),
    },
    argTypes: {
      disabled: { control: "boolean" },
      style: {
        control: { type: "select" },
        options: ["primary", "secondary"],
      },
      children: { control: "text" },
    },
  })
</script>

<Story name="Primary">
  {#snippet children(args)}
    <TextButton {...args}>Use Phone Number</TextButton>
  {/snippet}
</Story>

<Story name="Link" args={{ onClick: () => {} }}>
  {#snippet children(args)}
    <a href="https://www.example.com" target="_blank">
      <TextButton {...args}>Link</TextButton>
    </a>
  {/snippet}
</Story>

<Story name="Negative" args={{ style: "negative" }}>
  {#snippet children(args)}
    <div class="bg-c-sb-black p-4">
      <TextButton {...args}>Negative</TextButton>
    </div>
  {/snippet}
</Story>

<Story name="Disabled" args={{ disabled: true }}>
  {#snippet children(args)}
    <TextButton {...args}>Disabled</TextButton>
  {/snippet}
</Story>
