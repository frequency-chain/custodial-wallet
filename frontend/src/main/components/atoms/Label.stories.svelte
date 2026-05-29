<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import { Modal } from "$components/atoms/modal"
  import Label from "$components/atoms/Label.svelte"

  const { Story } = defineMeta({
    title: "Atoms/Label",
    component: Label,
  })
</script>

<Story name="Basic" args={{}}>
  {#snippet children(args)}
    <Label text={args.text ?? "Label"} {...args} />
  {/snippet}
</Story>

<Story name="Kitchen Sink" args={{ isRequired: true, hint: "This is a hint" }}>
  {#snippet children(args)}
    <Label text={args.text ?? "Label"} {...args}>
      {#snippet help()}
        <Modal.Title>Help</Modal.Title>
        Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do ...
      {/snippet}
    </Label>
  {/snippet}
</Story>
