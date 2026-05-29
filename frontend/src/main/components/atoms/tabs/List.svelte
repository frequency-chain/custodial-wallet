<script lang="ts">
  import type { Testable } from "$components/interfaces/Testable"
  import { Tabs } from "bits-ui"
  import { cn } from "$lib/utils/util"
  import type { Tab } from "$components/atoms/tabs/index"

  export type Props = {
    tabs: Tab[]
  } & Testable

  let { tabs, ...restProps }: Props = $props()
</script>

<Tabs.List class="rounded-toggle-default flex h-11 justify-between border-2" {...restProps}>
  {#each tabs as tab}
    <Tabs.Trigger
      value={tab.id}
      class={cn(
        "text-normal rounded-toggle-default -mx-0.5 w-full cursor-pointer items-center justify-center p-1 font-bold",
        "active:bg-c-button-primary data-[state=active]:bg-c-button-primary data-[state=active]:text-c-button-primary-text transition-all",
      )}
    >
      {tab.title}
    </Tabs.Trigger>
  {/each}
</Tabs.List>
