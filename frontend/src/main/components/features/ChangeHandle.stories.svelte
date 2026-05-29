<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import ChangeHandle from "./ChangeHandle.svelte"
  import Edit from "$components/icons/Edit.svelte"
  import TextButton from "$components/atoms/TextButton.svelte"
  import { initI18n } from "$lib/i18n"
  import { err, ok, Result } from "neverthrow"
  import { type ErrorData } from "$lib/types/data/ErrorData"
  import { GENERIC_ERROR } from "$lib/utils/errors"

  const { Story } = defineMeta({
    title: "Features/ChangeHandle",
    component: ChangeHandle,
    args: {},
  })

  const i18n = initI18n({
    "enter-handle-field.label": "Handle",
    "enter-handle-field.help.title": "Handle requirements",
    "enter-handle-field.help.requirements.1.before-link":
      "Keep it appropriate - no offensive language. For additional rules, please see ",
    "enter-handle-field.help.requirements.1.after-link": ".",
    "enter-handle-field.help.requirements.1.link-text": "Terms of Service",
    "enter-handle-field.help.requirements.2":
      "Frequency will automatically add a few numbers to the end of your handle to make sure it's unique.",
    "change-handle.title": "Handle",
    "change-handle.description": "Your unique handle is how others will find you across all apps on Frequency.",
    "change-handle.input.label": "Handle",
    "change-handle.permission.lead-in": "By updating your handle you agree to these permissions:",
    "change-handle.permission.1.description": "Update your handle and profile information on Frequency",
    "change-handle.submit": "Save and Continue",
    "change-handle.success.title": "Success",
    "change-handle.success.description": "You have successfully updated your handle!",
    "error.generic.title": "Whoops!",
    "error.generic.desc": "Something went wrong",
    "button.back": "Back",
  })

  interface ChangeHandleRequest {
    newHandle: string
  }

  const changeHandle = async ({
    newHandle,
  }: ChangeHandleRequest): Promise<Result<Result<string, string>, ErrorData>> => {
    await new Promise((r) => setTimeout(r, 1000))
    return ok(ok(newHandle))
  }

  const changeHandleValidationError = async (
    _: ChangeHandleRequest,
  ): Promise<Result<Result<string, string>, ErrorData>> => {
    await new Promise((r) => setTimeout(r, 1000))
    return ok(err("Invalid handle"))
  }

  const changeHandleNetworkError = async (
    _: ChangeHandleRequest,
  ): Promise<Result<Result<string, string>, ErrorData>> => {
    await new Promise((r) => setTimeout(r, 1000))
    return err(GENERIC_ERROR)
  }

  let userHandle = $state("JaneLovesWinter")
</script>

<Story name="Modal form">
  {#snippet children()}
    <div class="flex flex-row gap-4">
      <p>Handle: @{userHandle}</p>
      <ChangeHandle {changeHandle} {i18n} onSuccess={(newHandle) => (userHandle = newHandle)} style={"modal"}>
        {#snippet trigger(open)}
          <TextButton onClick={open}><Edit style={"negative"}></Edit></TextButton>
        {/snippet}
      </ChangeHandle>
    </div>
  {/snippet}
</Story>

<Story name="Modal form (validation error)">
  {#snippet children()}
    <ChangeHandle changeHandle={changeHandleValidationError} {i18n} style={"modal"}>
      {#snippet trigger(open)}
        <TextButton onClick={open}><Edit style={"negative"}></Edit></TextButton>
      {/snippet}
    </ChangeHandle>
  {/snippet}
</Story>

<Story name="Modal form (network error)">
  {#snippet children()}
    <ChangeHandle changeHandle={changeHandleNetworkError} {i18n} style={"modal"}>
      {#snippet trigger(open)}
        <TextButton onClick={open}><Edit style={"negative"}></Edit></TextButton>
      {/snippet}
    </ChangeHandle>
  {/snippet}
</Story>

<Story name="Page form">
  {#snippet children()}
    <ChangeHandle {changeHandle} {i18n} style={"page"} />
  {/snippet}
</Story>
