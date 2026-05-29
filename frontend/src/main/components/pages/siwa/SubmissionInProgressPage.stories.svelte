<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import { initI18n } from "$lib/i18n"
  import SubmissionInProgressPage from "$components/pages/siwa/SubmissionInProgressPage.svelte"
  import { writable } from "svelte/store"
  import type { SiwaSubmissionService } from "$lib/services/siwaSubmissionService"
  import { type SiwaSubmissionStatus, SiwaSubmissionStatusType } from "$lib/types/data/SiwaSubmissionStatus"
  import { GENERIC_ERROR } from "$lib/utils/errors"

  const { Story } = defineMeta({
    title: "Pages/Siwa/SubmissionInProgressPage",
    component: SubmissionInProgressPage,
    args: {},
  })

  const i18n = initI18n({
    "siwa.submission.loading": "Finalizing your account ...",
    "error.generic.title": "Error",
    "error.generic.desc": "An error has occurred",
  })

  const createMockService = (mockData: SiwaSubmissionStatus): SiwaSubmissionService => ({
    submissionStatus: writable<SiwaSubmissionStatus>(mockData),
    startPolling: async () => {},
  })

  const submittedData: SiwaSubmissionStatus = {
    type: SiwaSubmissionStatusType.SUBMITTED,
  }
  const submittedService = createMockService(submittedData)

  const failedData: SiwaSubmissionStatus = {
    type: SiwaSubmissionStatusType.FAILED,
    errorData: GENERIC_ERROR,
  }
  const failedService = createMockService(failedData)
</script>

<Story name="Submitted">
  {#snippet children()}
    <SubmissionInProgressPage service={submittedService} {i18n} />
  {/snippet}
</Story>

<Story name="Failed">
  {#snippet children()}
    <SubmissionInProgressPage service={failedService} {i18n} />
  {/snippet}
</Story>
