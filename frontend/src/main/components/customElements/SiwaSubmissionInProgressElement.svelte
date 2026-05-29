<svelte:options customElement={{ tag: "siwa-submission-in-progress", shadow: "none" }} />

<script lang="ts">
  import { initI18nFromMessagesJson } from "$lib/utils/server"
  import type { CwProps } from "$components/customElements/interfaces/CwProps"
  import SubmissionInProgressPage from "$components/pages/siwa/SubmissionInProgressPage.svelte"
  import { createSiwaSubmissionService } from "$lib/services/siwaSubmissionService"

  type Props = {
    "submission-id": string
    "redirect-url": string
  } & CwProps

  let {
    "submission-id": submissionId,
    "redirect-url": redirectUrl,
    "messages-json": localizedMessagesJson,
  }: Props = $props()

  const i18n = initI18nFromMessagesJson(localizedMessagesJson)

  const service = createSiwaSubmissionService({ pollingDelayMillis: 1000 }, submissionId, redirectUrl)
</script>

<SubmissionInProgressPage {service} {i18n} />
