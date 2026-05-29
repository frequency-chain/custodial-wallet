<script lang="ts">
  import Exit from "../../icons/Exit.svelte"
  import { type Snippet } from "svelte"
  import { Dialog } from "bits-ui"
  import type { Testable } from "$components/interfaces/Testable"
  import type { ModalTriggerProps } from "$components/interfaces/ModalTriggerProps"
  import type { ModalControlProps } from "$components/interfaces/ModalControlProps"
  import { clsx } from "clsx"

  type Props = {
    children: Snippet<[() => void]>
    dismissable?: boolean
  } & Testable &
    (ModalTriggerProps | ModalControlProps)

  let {
    children,
    dismissable = true,
    trigger,
    onClose,
    isOpen: externalIsOpen,
    handleClose: externalHandleClose,
    "data-testid": testId,
  }: Props = $props()

  let internalIsOpen = $state(false)

  let isOpen = $derived(externalIsOpen !== undefined ? externalIsOpen : internalIsOpen)

  const internalHandleClose = () => {
    internalIsOpen = false
  }

  let handleClose = () => {
    externalHandleClose ? externalHandleClose() : internalHandleClose()

    onClose?.()
  }

  const onEscapeKeydownOrInteractOutside = (e: UIEvent) => {
    e.preventDefault()
    if (dismissable === true) {
      handleClose()
    }
  }

  let dialogClasses = clsx(
    // Center in the page
    "fixed left-[50%] top-[50%] translate-x-[-50%] translate-y-[-50%] z-50",
    // Pin max width to sm breakpoint (with margins)
    "w-full max-w-[calc(100%-2rem)] sm:max-w-[calc(var(--breakpoint-sm)-2rem)]",
    // Modal container style
    "rounded-modal-default bg-c-modal-default",
  )
</script>

<Dialog.Root open={isOpen}>
  {#if trigger}
    <!-- Only rendered in 'uncontrolled' mode with internal state management -->
    {@render trigger(() => (internalIsOpen = true))}
  {/if}
  <Dialog.Portal>
    <Dialog.Overlay
      class="data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 bg-c-modal-backdrop fixed inset-0 z-50"
    />
    <Dialog.Content
      class={dialogClasses}
      onInteractOutside={onEscapeKeydownOrInteractOutside}
      onEscapeKeydown={onEscapeKeydownOrInteractOutside}
      data-testid={testId}
    >
      <div class="flex flex-col gap-6 p-6 pb-14">
        {#if dismissable}
          <div class="flex w-full justify-end">
            <button onclick={handleClose} aria-label="Close" class="cursor-pointer">
              <Exit />
            </button>
          </div>
        {/if}

        {@render children(handleClose)}
      </div>
    </Dialog.Content>
  </Dialog.Portal>
</Dialog.Root>
