<script lang="ts">
  import { type Readable } from "svelte/store"
  import { type I18nGetter } from "$lib/i18n"
  import { Accordion } from "$components/atoms/accordion"
  import TermCard from "$components/atoms/TermCard.svelte"
  import SocialFingerprint from "$components/icons/SocialFingerprint.svelte"
  import UniversalHandle from "$components/icons/UniversalHandle.svelte"
  import FrequencyLogo from "$components/icons/FrequencyLogo.svelte"
  import FrequencyWave from "$components/icons/FrequencyWave.svelte"
  import Footer from "$components/features/Footer.svelte"

  interface Props {
    i18n: Readable<I18nGetter>
  }

  let { i18n }: Props = $props()
</script>

<!-- TODO(1726): `Header` goes in `HomePage` component -->
<section class="bg-c-cream flex flex-row py-18! lg:justify-center">
  <!-- remove ! after materialize removed -->
  <div class="mr-15 hidden w-full max-w-180 flex-grow-1 lg:inline xl:mr-30">
    <FrequencyWave class="w-full" />
  </div>
  <div class="content-center">
    <h1 class="md:text-nowrap">{$i18n("home.splash.title.1")}<br />{$i18n("home.splash.title.2")}</h1>
    <p class="wrap-normal">{$i18n("home.splash.subtitle")}</p>
  </div>
</section>
<section class="py-10 md:max-w-[85%] lg:max-w-[60%]">
  <h2>{$i18n("home.social-web.title")}</h2>
  <h4 class="py-3">{$i18n("home.social-web.subtitle")}</h4>
  <p>{$i18n("home.social-web.description")}</p>
</section>
<section class="bg-c-white py-10">
  <h3 class="pb-5">{$i18n("home.terms.title")}</h3>
  <div class="flex flex-col gap-5 lg:flex lg:basis-auto lg:flex-row lg:*:flex-1">
    <TermCard title={$i18n("home.terms.1.title")} background="bg-c-white">
      {#snippet img()}
        <SocialFingerprint />
      {/snippet}
      {#snippet desc()}
        <p>{$i18n("home.terms.1.text")}</p>
      {/snippet}
    </TermCard>
    <TermCard title={$i18n("home.terms.2.title")} background="bg-c-white">
      {#snippet img()}
        <UniversalHandle />
      {/snippet}
      {#snippet desc()}
        <p>{$i18n("home.terms.2.text")}</p>
      {/snippet}
    </TermCard>
    <TermCard title={$i18n("home.terms.3.title")} background="bg-c-white">
      {#snippet img()}
        <FrequencyLogo />
      {/snippet}
      {#snippet desc()}
        <p>{$i18n("home.terms.3.text")}</p>
      {/snippet}
    </TermCard>
  </div>
</section>
<section class="bg-c-cream p-2">
  <h3 class="pb-1">{$i18n("home.faq.title")}</h3>
  <Accordion.Root type="single">
    <Accordion.Item id="faq-1" title={$i18n("home.faq.1.title")} titleClass="heading-5">
      <p>{$i18n("home.faq.1.text")}</p>
    </Accordion.Item>
    <Accordion.Item id="faq-2" title={$i18n("home.faq.2.title")} titleClass="heading-5">
      <p>{$i18n("home.faq.2.text")}</p>
    </Accordion.Item>
    <Accordion.Item id="faq-3" title={$i18n("home.faq.3.title")} titleClass="heading-5">
      <p>{$i18n("home.faq.3.text")}</p>
    </Accordion.Item>
    <Accordion.Item id="faq-4" title={$i18n("home.faq.4.title")} titleClass="heading-5">
      <p>
        {$i18n("home.faq.4.text.1")}
        <a href="/terms.html">{$i18n("home.faq.4.text.terms")}</a>
        {$i18n("home.faq.4.text.2")}
      </p>
    </Accordion.Item>
    <Accordion.Item id="faq-5" title={$i18n("home.faq.5.title")} titleClass="heading-5">
      <p>{$i18n("home.faq.5.text.1")}</p>
      <br />
      <p>{$i18n("home.faq.5.text.2")}</p>
    </Accordion.Item>
    <Accordion.Item id="faq-6" title={$i18n("home.faq.6.title")} titleClass="heading-5">
      <p>{$i18n("home.faq.6.text")}</p>
    </Accordion.Item>
    <Accordion.Item id="faq-7" title={$i18n("home.faq.7.title")} titleClass="heading-5">
      <p>
        {$i18n("home.faq.7.text")}
        <a href="https://projectliberty.zendesk.com/hc/en-us/requests/new">{$i18n("home.faq.7.form-link")}</a>
      </p>
    </Accordion.Item>
  </Accordion.Root>
</section>
<Footer {i18n} />
