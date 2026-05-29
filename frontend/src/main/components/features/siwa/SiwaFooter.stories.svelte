<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import { initI18n } from "$lib/i18n"
  import SiwaFooter from "$components/features/siwa/SiwaFooter.svelte"

  const { Story } = defineMeta({
    title: "Features/Siwa/SiwaFooter",
    component: SiwaFooter,
  })

  const i18n = initI18n({
    "siwa.footer.privacy-policy": "Privacy Policy",
    "siwa.footer.terms-of-service": "Terms of Service",
    "siwa.footer.copyright": "2025 Project Liberty LLC",
  })
</script>

<Story name="Primary">
  {#snippet children()}
    <SiwaFooter {i18n} />
  {/snippet}
</Story>
