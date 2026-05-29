<script lang="ts">
  import { StyleguideSection } from "$components/enums/StyleguideSection"
  import Typefaces from "$components/styleguide/sections/Typefaces.svelte"
  import BrandColors from "$components/styleguide/sections/BrandColors.svelte"
  import Buttons from "$components/styleguide/sections/Buttons.svelte"
  import CornerRadiuses from "$components/styleguide/sections/CornerRadiuses.svelte"
  import Shadows from "$components/styleguide/sections/Shadows.svelte"

  interface Props {
    section?: StyleguideSection
  }

  const { section = StyleguideSection.BRAND_COLORS }: Props = $props()
</script>

{#if section === StyleguideSection.BRAND_COLORS}
  <BrandColors />
{:else if section === StyleguideSection.TYPEFACES}
  <Typefaces />
{:else if section === StyleguideSection.CORNER_RADIUSES}
  <CornerRadiuses />
{:else if section === StyleguideSection.SHADOWS}
  <Shadows />
{:else if section === StyleguideSection.BUTTONS}
  <Buttons />
{/if}
