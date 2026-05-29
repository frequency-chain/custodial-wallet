<script lang="ts">
  import { type Snippet } from "svelte"
  import type { Testable } from "$components/interfaces/Testable"
  import FrequencyWaveBackground from "$components/icons/backgrounds/FrequencyWaveBackground.svelte"

  export type Props = {
    title: Snippet
    children: Snippet
  } & Testable

  const { title, children }: Props = $props()
</script>

<div class="relative">
  <div
    class="absolute top-0 -z-100 flex h-65 w-full items-center justify-center overflow-hidden sm:items-baseline lg:h-100"
  >
    <div class="min-h-full min-w-110 flex-auto">
      <FrequencyWaveBackground />
    </div>
  </div>
  <div class="pb-16 lg:px-20">
    <div class="text-c-text-negative min-h-50 py-10 text-center">
      {@render title()}
    </div>
    <div class={"bg-c-white rounded-sb-xxl px-4 py-6 lg:px-16 lg:py-16"}>
      {@render children()}
    </div>
  </div>
</div>
