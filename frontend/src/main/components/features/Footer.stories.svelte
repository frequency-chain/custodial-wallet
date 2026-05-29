<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import { initI18n } from "$lib/i18n"
  import Footer from "$components/features/Footer.svelte"

  const { Story } = defineMeta({
    title: "Features/Footer",
    component: Footer,
  })

  const i18n = initI18n({
    "website-footer.pp": "Privacy Policy",
    "website-footer.tos": "Terms",
    "website-footer.helpdesk": "Help Center",
    "website-footer.copyright": "2025 Project Liberty LLC",
  })
</script>

<Story name="Primary">
  {#snippet children()}
    <Footer {i18n} />
  {/snippet}
</Story>
