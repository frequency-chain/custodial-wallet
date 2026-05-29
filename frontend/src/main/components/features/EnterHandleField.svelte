<script lang="ts">
  import Input from "$components/atoms/Input.svelte"
  import { Modal } from "$components/atoms/modal"
  import type { I18nGetter } from "$lib/i18n.js"
  import type { Readable } from "svelte/store"
  import type { Testable } from "$components/interfaces/Testable.js"
  import { cn } from "$lib/utils/util"
  import Label from "$components/atoms/Label.svelte"

  export type Props = {
    i18n: Readable<I18nGetter>
    inputValue?: string
    errorMessage?: string
    class?: string
  } & Testable

  let { i18n, inputValue = $bindable(), errorMessage, class: additionalClasses }: Props = $props()
</script>

<div class={cn("flex flex-col gap-1", additionalClasses)}>
  <Label text={$i18n("enter-handle-field.label")} isRequired={true} for="handle-input" help={handleRequirements} />

  <div class="flex flex-row items-start gap-2">
    <Input
      isRequired={true}
      bind:inputValue
      id="handle-input"
      width="full"
      {errorMessage}
      data-testid="enter-handle-input"
    />
    <div class="w-24">
      <Input disabled={true} alignment="center" placeholder="##"></Input>
    </div>
  </div>
</div>

{#snippet handleRequirements()}
  <Modal.Title>{$i18n("enter-handle-field.help.title")}</Modal.Title>
  <ul class="list-disc pl-8 text-start">
    <li>
      {$i18n("enter-handle-field.help.requirements.1.before-link")}<a href="/terms"
        >{$i18n("enter-handle-field.help.requirements.1.link-text")}</a
      >{$i18n("enter-handle-field.help.requirements.1.after-link")}
    </li>
    <li>{$i18n("enter-handle-field.help.requirements.2")}</li>
    <!-- TODO: 'Learn more' link once we have a sane destination URL -->
  </ul>
{/snippet}
