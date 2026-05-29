<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import PageOutline from "$components/features/PageOutline.svelte"
  import CircledCheckmark from "$components/icons/CircledCheckmark.svelte"
  import Input from "$components/atoms/Input.svelte"

  const { Story } = defineMeta({
    title: "Features/PageOutline",
    component: PageOutline,
  })
</script>

<Story name="Primary" parameters={{ layout: "fullscreen" }}>
  {#snippet children()}
    <PageOutline>
      {#snippet title()}
        <h3>This is an Awesome Title</h3>
      {/snippet}
      {#snippet children()}
        <p>This is the body of the page inside the outline. It can have anything you want including a list!</p>
        <br />
        <ol>
          <li>Item 1</li>
          <li>Item 2</li>
          <li>Item 3</li>
          <li>Item 4</li>
        </ol>
        <br />
        <p>Even an icon!</p>
        <CircledCheckmark />
        <br />
        <p>and if you are feeling really wild and crazy, maybe even an input:</p>
        <Input id="{'test'}," label={"Label"} />
        <br />
        <p>Truly we live in the future</p>
      {/snippet}
    </PageOutline>
  {/snippet}
</Story>
