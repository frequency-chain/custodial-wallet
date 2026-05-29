<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import TermCard from "$components/atoms/TermCard.svelte"

  const { Story } = defineMeta({
    title: "Atoms/TermCard",
    component: TermCard,
    argTypes: {
      title: { control: "text" },
      img: {
        control: { type: "select" },
        options: ["primary", "secondary"],
      },
      desc: { control: "text" },
    },
  })
</script>

<script>
  import CircledCheckmark from "$components/icons/CircledCheckmark.svelte"
  import Warning from "$components/icons/Warning.svelte"
  import Reply from "$components/icons/Reply.svelte"
</script>

<Story name="Primary">
  {#snippet children()}
    <div class="bg-c-cream p-5">
      <TermCard title="Lorem Ipsum" background="bg-c-white">
        {#snippet img(imgSize)}
          <CircledCheckmark class={imgSize} />
        {/snippet}
        {#snippet desc()}
          <p>
            Consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad
            minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.
          </p>
        {/snippet}
      </TermCard>
    </div>
  {/snippet}
</Story>

<Story name="Triple">
  {#snippet children()}
    <div class="bg-c-cream flex flex-col gap-5 p-5 lg:flex-row">
      <TermCard title="Lorem Ipsum" background="bg-c-white">
        {#snippet img()}
          <CircledCheckmark />
        {/snippet}
        {#snippet desc()}
          <p>
            Consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad
            minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.
          </p>
        {/snippet}
      </TermCard>

      <TermCard title="Lorem Ipsum" background="bg-c-white">
        {#snippet img()}
          <Reply />
        {/snippet}
        {#snippet desc()}
          <p>
            Consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad
            minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.
          </p>
        {/snippet}
      </TermCard>

      <TermCard title="Lorem Ipsum" background="bg-c-white">
        {#snippet img()}
          <Warning />
        {/snippet}
        {#snippet desc()}
          <p>
            Consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad
            minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.
          </p>
        {/snippet}
      </TermCard>
    </div>
  {/snippet}
</Story>
