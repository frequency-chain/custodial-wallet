<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import Timer from "$components/atoms/Timer.svelte"

  const { Story } = defineMeta({
    title: "Atoms/Timer",
    component: Timer,
    argTypes: {
      millis: { control: "number" },
      startOnLoad: { control: "boolean" },
    },
  })
</script>

<Story name="DefaultTimer">
  {#snippet children(args)}
    <Timer millis={60000} {...args} />
  {/snippet}
</Story>
