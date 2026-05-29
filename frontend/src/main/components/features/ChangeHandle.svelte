<script lang="ts">
  import type { Testable } from "$components/interfaces/Testable"
  import type { Readable } from "svelte/store"
  import type { I18nGetter } from "$lib/i18n"
  import { type ModalTriggerProps } from "$components/interfaces/ModalTriggerProps"
  import { Modal } from "$components/atoms/modal"
  import Button from "$components/atoms/Button.svelte"
  import Person from "$components/icons/Person.svelte"
  import EnterHandleField from "$components/features/EnterHandleField.svelte"
  import { type ChangeHandleStep } from "$components/interfaces/ChangeHandleStep"
  import { ChangeHandleStepType } from "$components/interfaces/ChangeHandleStep"
  import { Result } from "neverthrow"
  import { type ErrorData } from "$lib/types/data/ErrorData"
  import CircledCheckmark from "$components/icons/CircledCheckmark.svelte"
  import { isValidPreNormalization } from "$lib/utils/handles"

  // The component can either render normally on the page, or appear inside a modal (which requires a trigger)
  type StyleProps = { style: "page"; trigger?: never } | { style: "modal"; trigger: ModalTriggerProps["trigger"] }

  type ChangeHandleCall = (request: { newHandle: string }) => Promise<Result<Result<string, string>, ErrorData>>

  export type Props = {
    i18n: Readable<I18nGetter>
    changeHandle: ChangeHandleCall
    onSuccess?: (newHandle: string) => void
  } & StyleProps &
    Testable

  let { i18n, changeHandle, onSuccess, style, trigger }: Props = $props()

  const initialStep: ChangeHandleStep = { type: ChangeHandleStepType.ENTER_HANDLE, isLoading: false }
  let step: ChangeHandleStep = $state(initialStep)
  let handleInputValue: string = $state("")
  let handleIsValid: boolean = $derived(isValidPreNormalization(handleInputValue))

  $inspect(handleInputValue)

  const onSubmit = async (): Promise<void> => {
    step = { type: ChangeHandleStepType.ENTER_HANDLE, isLoading: true }

    const response = await changeHandle({ newHandle: handleInputValue })
    response.match(
      (handleResult) =>
        handleResult.match(
          (newHandle) => {
            step = { type: ChangeHandleStepType.SUCCESS, newHandle }
            if (onSuccess !== undefined) {
              onSuccess(newHandle)
            }
          },
          (validationErrorMessage) => {
            step = { type: ChangeHandleStepType.ENTER_HANDLE, isLoading: false, validationErrorMessage }
          },
        ),
      (errorData: ErrorData) => {
        step = { type: ChangeHandleStepType.ERROR, errorData }
      },
    )
  }

  const onModalClose = (): void => {
    // Reset state
    step = initialStep
    handleInputValue = ""
  }
</script>

{#if style === "modal" && trigger !== undefined}
  <Modal.Root {trigger} onClose={onModalClose}>
    <div>
      {@render content()}
    </div>
  </Modal.Root>
{:else}
  {@render content()}
{/if}

{#snippet content()}
  {#if step.type === ChangeHandleStepType.ENTER_HANDLE}
    {@render enterHandle(step.isLoading, step.validationErrorMessage)}
  {:else if step.type === ChangeHandleStepType.SUCCESS}
    <div class="flex flex-col items-center text-center" data-testid="change-handle-success">
      <h2 class="heading-2 text-c-text-default pb-5">
        {$i18n("change-handle.success.title")}
      </h2>
      <CircledCheckmark />
      <p class="pt-6 font-bold">{$i18n("change-handle.success.description")}</p>
    </div>
  {:else if step.type === ChangeHandleStepType.ERROR}
    <div class="flex flex-col items-center text-center">
      <h2 class="heading-2 text-c-text-default pb-5">
        {$i18n(step.errorData.title)}
      </h2>
      <p class="pb-6">{$i18n(step.errorData.description)}</p>
      <Button width="full" onClick={() => (step = { type: ChangeHandleStepType.ENTER_HANDLE, isLoading: false })}>
        {$i18n("button.back")}
      </Button>
    </div>
  {/if}
{/snippet}

{#snippet enterHandle(isLoading: boolean, errorMessage: string | undefined)}
  <div class={style === "modal" ? "text-center" : ""}>
    <h2 class="heading-2 text-c-text-default pb-5">
      {$i18n("change-handle.title")}
    </h2>
    <p>{$i18n("change-handle.description")}</p>
  </div>
  <EnterHandleField bind:inputValue={handleInputValue} {errorMessage} {i18n} class="py-4" />
  <p class="py-2 text-start font-bold">{$i18n("change-handle.permission.lead-in")}</p>
  <ul>
    <li class="flex items-center gap-2 pb-6">
      <div class="p-4">
        <Person />
      </div>
      <p class="text-start">{$i18n("change-handle.permission.1.description")}</p>
    </li>
  </ul>
  <Button
    disabled={!handleIsValid}
    loading={isLoading}
    onClick={onSubmit}
    width="full"
    data-testid="change-handle-submit-button"
  >
    {$i18n("change-handle.submit")}
  </Button>
{/snippet}
