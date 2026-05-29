<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import { initI18n } from "$lib/i18n"
  import SiwaLoading from "$components/features/siwa/SiwaLoading.svelte"

  const { Story } = defineMeta({
    title: "Features/Siwa/SiwaLoading",
    component: SiwaLoading,
  })

  const i18n = initI18n({
    "siwa.submission.loading": "Finalizing your account ...",
  })
</script>

<Story name="Primary">
  {#snippet children()}
    <SiwaLoading {i18n} />
  {/snippet}
</Story>
