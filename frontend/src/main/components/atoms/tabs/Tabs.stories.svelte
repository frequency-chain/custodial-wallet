<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import { Tabs } from "./index"

  const { Story } = defineMeta({
    title: "Atoms/Tabs",
    component: Tabs.Root,
    args: {},
  })

  let value: string = $state("ITEM 1")
</script>

<Story name="Primary">
  {#snippet children()}
    <Tabs.Root {value}>
      <Tabs.List
        tabs={[
          { title: "ITEM 1", id: "ITEM 1" },
          { title: "ITEM 2", id: "ITEM 2" },
          { title: "ITEM 3", id: "ITEM 3" },
        ]}
      ></Tabs.List>
      <Tabs.Content value="ITEM 1">
        <div></div>
        <div>ITEM 1 Content Example</div>
      </Tabs.Content>
      <Tabs.Content value="ITEM 2">
        <div></div>
        <div>ITEM 2 Content Example</div>
      </Tabs.Content>
      <Tabs.Content value="ITEM 3">
        <div></div>
        <div>ITEM 3 Content Example</div>
      </Tabs.Content>
    </Tabs.Root>
  {/snippet}
</Story>
