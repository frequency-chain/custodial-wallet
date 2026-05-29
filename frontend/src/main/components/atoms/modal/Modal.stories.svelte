<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import { Modal } from "./index"
  import Button from "../Button.svelte"

  const { Story } = defineMeta({
    title: "Atoms/Modal",
    component: Modal.Root,
    argTypes: {
      dismissable: { control: "boolean" },
    },
  })
</script>

<Story name="Primary">
  {#snippet children(args)}
    <Modal.Root dismissable={args.dismissable}>
      {#snippet trigger(open)}
        <Button onClick={open}>Open</Button>
      {/snippet}
      {#snippet children(close)}
        <Modal.Title>Lorem Ipsum Dolor Sit Amet</Modal.Title>
        <Modal.Description>
          Consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad
          minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.
        </Modal.Description>

        <Button onClick={close} width="full">Okay</Button>
      {/snippet}
    </Modal.Root>
  {/snippet}
</Story>
