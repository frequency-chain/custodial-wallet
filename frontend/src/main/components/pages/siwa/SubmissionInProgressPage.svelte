<script lang="ts">
  import { type Readable } from "svelte/store"
  import { type I18nGetter } from "$lib/i18n"
  import SiwaLoading from "$components/features/siwa/SiwaLoading.svelte"
  import type { SiwaSubmissionService } from "$lib/services/siwaSubmissionService"
  import { SiwaSubmissionStatusType } from "$lib/types/data/SiwaSubmissionStatus"
  import CircledCheckmark from "$components/icons/CircledCheckmark.svelte"
  import { onMount } from "svelte"

  interface Props {
    service: SiwaSubmissionService
    i18n: Readable<I18nGetter>
  }

  let { service, i18n }: Props = $props()

  const submissionStatus = service.submissionStatus

  onMount(async () => {
    await service.startPolling()
  })

  // Trigger a redirect when the submission has finalized successfully
  $effect(() => {
    if ($submissionStatus.type === SiwaSubmissionStatusType.SUCCESS) {
      window.location.href = $submissionStatus.redirectUrl
    }
  })
</script>

<!-- A (sub-)page shown while awaiting a blockchain submission to finalize during the SIWA flow -->
{#if $submissionStatus.type === SiwaSubmissionStatusType.SUBMITTED}
  <SiwaLoading {i18n} />
  <!-- NOTE: Both indeterminate errors (e.g., network) and 'terminal' errors (e.g., blockchain) are rendered the same -->
  <!-- TODO(FI-23): May want to revisit this -->
{:else if $submissionStatus.type === SiwaSubmissionStatusType.UNKNOWN || $submissionStatus.type === SiwaSubmissionStatusType.FAILED}
  <div class="m-auto flex h-full flex-col items-center gap-4">
    <h3>{$i18n($submissionStatus.errorData.title)}</h3>
    <p>{$i18n($submissionStatus.errorData.description)}</p>
  </div>
{:else if $submissionStatus.type === SiwaSubmissionStatusType.SUCCESS}
  <div class="m-auto flex h-full flex-col items-center">
    <CircledCheckmark />
  </div>
{/if}
