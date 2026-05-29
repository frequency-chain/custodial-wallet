import type { Snippet } from "svelte"

// Supply a `trigger` snippet for an 'uncontrolled' modal that manages its own state
export interface ModalTriggerProps {
  trigger: Snippet<[() => void]>
  onClose?: () => void // Callback invoked when the Modal closes itself
  isOpen?: never
  handleClose?: never
}
