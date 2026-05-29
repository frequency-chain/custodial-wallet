<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import Button from "$components/atoms/Button.svelte"

  const { Story } = defineMeta({
    title: "Atoms/Button",
    component: Button,
    args: {
      onClick: () => alert("Button clicked!"),
    },
    argTypes: {
      disabled: { control: "boolean" },
      style: {
        control: { type: "select" },
        options: ["primary", "secondary", "danger", "destructive"],
      },
      children: { control: "text" },
    },
  })
</script>

<Story name="Primary">
  {#snippet children(args)}
    <Button {...args}>Example</Button>
  {/snippet}
</Story>

<Story name="Link" args={{ onClick: () => {} }}>
  {#snippet children(args)}
    <a href="https://www.example.com" target="_blank">
      <Button {...args}>Link</Button>
    </a>
  {/snippet}
</Story>

<Story name="Negative" args={{ style: "negative" }}>
  {#snippet children(args)}
    <div class="bg-c-sb-black p-8">
      <Button {...args}>Example</Button>
    </div>
  {/snippet}
</Story>

<Story name="Disabled" args={{ disabled: true }}>
  {#snippet children(args)}
    <Button {...args}>Disabled</Button>
  {/snippet}
</Story>

<Story name="Secondary" args={{ style: "secondary" }}>
  {#snippet children(args)}
    <Button {...args}>Secondary</Button>
  {/snippet}
</Story>

<Story name="Destructive" args={{ style: "destructive" }}>
  {#snippet children(args)}
    <Button {...args}>Delete</Button>
  {/snippet}
</Story>

<Story name="Loading" args={{ loading: true }}>
  {#snippet children(args)}
    <Button {...args}>Content is hidden</Button>
  {/snippet}
</Story>
