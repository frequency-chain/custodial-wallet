<script lang="ts">
  import { Modal } from "$components/atoms/modal"
  import Button from "$components/atoms/Button.svelte"
</script>

<Modal.Root>
  {#snippet trigger(open)}
    <Button onClick={open} data-testid="open-button">Open</Button>
  {/snippet}
  {#snippet children(close)}
    <Modal.Title>Lorem Ipsum Dolor Sit Amet</Modal.Title>
    <Modal.Description>
      Consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim
      veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.
    </Modal.Description>

    <Button onClick={close} width="full" data-testid="close-button">Okay</Button>
  {/snippet}
</Modal.Root>
