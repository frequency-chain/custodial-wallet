<script lang="ts">
  import { type Snippet } from "svelte"
  import type { Testable } from "$components/interfaces/Testable"
  import type { Readable } from "svelte/store"
  import type { I18nGetter } from "$lib/i18n"
  import SiwaFooter from "$components/features/siwa/SiwaFooter.svelte"
  import FrequencyLogoNegative from "$components/icons/FrequencyLogoNegative.svelte"

  export type Props = {
    i18n: Readable<I18nGetter>
    splash: Snippet
    title: Snippet
    children: Snippet
  } & Testable

  const { i18n, splash, title, children }: Props = $props()
</script>

<div class="flex">
  <!--  LARGE SCREEN SPLASH SECTION -->

  <div class="bg-frequency-logo hidden bg-cover bg-center lg:inline lg:flex-6 xl:flex-7">
    <div class="flex h-full flex-col justify-center lg:px-[15%] xl:px-[25%]">
      <div class="flex w-full content-center justify-center">
        <FrequencyLogoNegative class="h-full w-[90%]" />
      </div>
      <div class="text-md text-c-text-negative pt-5 text-center">
        {@render splash()}
      </div>
    </div>
  </div>
  <!--  LARGE SCREEN SPLASH SECTION -->

  <!--  MAIN PAGE SECTION -->
  <div class="flex min-h-dvh flex-4 flex-col justify-center lg:mx-10 xl:flex-3">
    <div class="flex-1 lg:pt-30">
      <!--  MAIN PAGE TITLE  -->
      <div class="bg-frequency-logo bg-cover bg-center px-5 md:px-20 lg:bg-none lg:px-0">
        <div class="relative pb-5 lg:hidden">
          <div class="pt-20">
            <FrequencyLogoNegative class="h-full w-[50%]" />
          </div>
        </div>
        <div class="lg:text-c-text-default text-c-text-negative pt-5 pb-20 lg:p-0 lg:text-center">
          {@render title()}
        </div>
      </div>
      <!--  MAIN PAGE TITLE  -->

      <div class="px-5 pt-10 md:px-20 lg:px-5">
        {@render children()}
      </div>
    </div>
    <div class="">
      <SiwaFooter {i18n} />
    </div>
  </div>

  <!--  MAIN PAGE SECTION -->
</div>
