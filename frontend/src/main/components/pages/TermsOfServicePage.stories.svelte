<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import TermsOfServicePage from "./TermsOfServicePage.svelte"
  import { initI18n } from "$lib/i18n"
  import { HeaderButtonActionType } from "$components/enums/HeaderButtonActionType"
  import { createLoginService } from "$lib/services/loginService"
  import { getSessionId } from "$lib/utils"

  const { Story } = defineMeta({
    title: "Pages/TermsOfServicePage",
    component: TermsOfServicePage,
    args: {},
  })

  // ************************************
  // *********** I18N Service ***********
  // ************************************

  const i18n = initI18n({
    "website-header.home": "HOME.",
    "website-header.faqs": "FAQs.",
    "website-header.account": "My Account",
    "website-header.login": "Login",
    "termsPage.title": "Frequency Access Terms of Service",
    "website-footer.pp": "Privacy Policy",
    "website-footer.tos": "Terms",
    "website-footer.helpdesk": "Help Center",
    "website-footer.copyright": "2025 Project Liberty LLC",
  })
</script>

<Story name="Logged In" parameters={{ layout: "fullscreen" }}>
  {#snippet children()}
    <TermsOfServicePage {i18n} buttonAction={{ type: HeaderButtonActionType.NAVIGATE }} isDeveloperTerms={false} />
  {/snippet}
</Story>

<Story name="Not Logged In" parameters={{ layout: "fullscreen" }}>
  {#snippet children()}
    <TermsOfServicePage
      {i18n}
      buttonAction={{
        type: HeaderButtonActionType.LOGIN,
        loginService: createLoginService(),
        sessionIdGetter: { getSessionId },
        hCaptcha: { siteKey: "10000000-ffff-ffff-ffff-000000000001" },
      }}
      isDeveloperTerms={false}
    />
  {/snippet}
</Story>

<Story name="IsDeveloperTerms" parameters={{ layout: "fullscreen" }}>
  {#snippet children()}
    <TermsOfServicePage {i18n} buttonAction={{ type: HeaderButtonActionType.NAVIGATE }} isDeveloperTerms={true} />
  {/snippet}
</Story>
