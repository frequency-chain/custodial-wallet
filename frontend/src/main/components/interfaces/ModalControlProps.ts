// Set `isOpen` and `handleClose` for a 'controlled' modal that is driven by its parent
export interface ModalControlProps {
  trigger?: never
  onClose?: () => void // Callback invoked after `handleClose` executes
  isOpen: boolean
  handleClose: () => void
}
