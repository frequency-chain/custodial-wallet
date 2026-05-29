<script lang="ts">
  import { type Readable } from "svelte/store"
  import { type I18nGetter } from "$lib/i18n"
  import Button from "$components/atoms/Button.svelte"
  import { Modal } from "$components/atoms/modal"

  type Props = {
    i18n: Readable<I18nGetter>
    isOpen: boolean
    handleClose: () => void
    seedPhraseText: boolean
  }

  let { i18n, isOpen, handleClose, seedPhraseText, ...restProps }: Props = $props()

  let titleText = $derived(
    seedPhraseText ? "wallet.passkey.iframe-modal.title.first" : "wallet.passkey.iframe-modal.title.second",
  )
  let descText = $derived(
    seedPhraseText ? "wallet.passkey.iframe-modal.desc.first" : "wallet.passkey.iframe-modal.desc.second",
  )
</script>

<Modal.Root {isOpen} {handleClose} {...restProps}>
  <Modal.Title data-testid="title">{$i18n(titleText)}</Modal.Title>
  <Modal.Description class="pb-8">{$i18n(descText)}</Modal.Description>
  <div class="center-align">
    <iframe
      sandbox="allow-scripts"
      title="secureIFrame"
      id="secureIFrame"
      src="/wallet/iframe"
      data-testid="secureIFrame"
    ></iframe>
  </div>
  <Button width="full" onClick={handleClose} data-testid="close-button"
    >{$i18n("wallet.passkey.iframe-modal.close")}</Button
  >
</Modal.Root>
