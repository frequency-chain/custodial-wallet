<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import Styleguide from "./Styleguide.svelte"
  import { StyleguideSection } from "$components/enums/StyleguideSection"

  const { Story } = defineMeta({
    title: "Styleguide",
    component: Styleguide,
  })
</script>

<Story name="Brand Colors" args={{ section: StyleguideSection.BRAND_COLORS }}>
  {#snippet children(args)}
    <Styleguide {...args} />
  {/snippet}
</Story>

<Story name="Typefaces" args={{ section: StyleguideSection.TYPEFACES }}>
  {#snippet children(args)}
    <Styleguide {...args} />
  {/snippet}
</Story>

<Story name="Corners" args={{ section: StyleguideSection.CORNER_RADIUSES }}>
  {#snippet children(args)}
    <Styleguide {...args} />
  {/snippet}
</Story>

<Story name="Shadows" args={{ section: StyleguideSection.SHADOWS }}>
  {#snippet children(args)}
    <Styleguide {...args} />
  {/snippet}
</Story>

<Story name="Buttons" args={{ section: StyleguideSection.BUTTONS }}>
  {#snippet children(args)}
    <Styleguide {...args} />
  {/snippet}
</Story>
