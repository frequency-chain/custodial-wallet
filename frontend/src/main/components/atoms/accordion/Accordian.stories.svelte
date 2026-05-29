<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import { Accordion } from "./index"

  const { Story } = defineMeta({
    title: "Atoms/Accordion",
    component: Accordion.Root,
    args: {},
  })
</script>

<Story name="Primary">
  {#snippet children()}
    <Accordion.Root type="single">
      <Accordion.Item id="item-1" title="Item 1">
        Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec eu augue tortor. Duis convallis mollis tortor ac
        dignissim. Nunc tristique elit turpis. Phasellus id nisi accumsan, accumsan sem rutrum, aliquet nisl. Morbi ante
        elit, consequat eu imperdiet vitae, convallis vitae ipsum. Praesent in iaculis libero, eu scelerisque ligula.
        Suspendisse potenti. Donec quis massa justo. Cras metus risus, auctor in felis non, dictum tristique metus.
      </Accordion.Item>
      <Accordion.Item id="item-2" title="Item 2">
        Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec eu augue tortor. Duis convallis mollis tortor ac
        dignissim. Nunc tristique elit turpis. Phasellus id nisi accumsan, accumsan sem rutrum, aliquet nisl. Morbi ante
        elit, consequat eu imperdiet vitae, convallis vitae ipsum. Praesent in iaculis libero, eu scelerisque ligula.
        Suspendisse potenti. Donec quis massa justo. Cras metus risus, auctor in felis non, dictum tristique metus.
      </Accordion.Item>
    </Accordion.Root>
  {/snippet}
</Story>
