<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import HamburgerButton from "$components/atoms/HamburgerButton.svelte"

  let primaryIsOpen = $state(false)
  let primaryOnClick = () => (primaryIsOpen = !primaryIsOpen)

  let negativeIsOpen = $state(false)
  let negativeOnClick = () => (negativeIsOpen = !negativeIsOpen)

  const { Story } = defineMeta({
    title: "Atoms/HamburgerButton",
    component: HamburgerButton,
    argTypes: {
      style: {
        control: { type: "select" },
        options: ["primary", "negative"],
      },
    },
  })
</script>

<Story name="Primary">
  {#snippet children({ isOpen, ...args })}
    <HamburgerButton isOpen={isOpen ?? primaryIsOpen} onClick={primaryOnClick} {...args} />
  {/snippet}
</Story>

<Story name="Negative" args={{ style: "negative" }}>
  {#snippet children({ isOpen, ...args })}
    <div class="bg-c-sb-black p-8">
      <HamburgerButton isOpen={isOpen ?? negativeIsOpen} onClick={negativeOnClick} {...args} />
    </div>
  {/snippet}
</Story>
