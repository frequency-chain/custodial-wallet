<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import SiwaPageOutline from "$components/features/siwa/SiwaPageOutline.svelte"
  import { initI18n } from "$lib/i18n"
  import CircledCheckmark from "$components/icons/CircledCheckmark.svelte"
  import Input from "$components/atoms/Input.svelte"

  const { Story } = defineMeta({
    title: "Features/Siwa/SiwaPageOutline",
    component: SiwaPageOutline,
  })

  const i18n = initI18n({
    "siwa.footer.privacy-policy": "Privacy Policy",
    "siwa.footer.terms-of-service": "Terms of Service",
    "siwa.footer.copyright": "2025 Project Liberty LLC",
  })
</script>

<Story name="Primary" parameters={{ layout: "fullscreen" }}>
  {#snippet children()}
    <SiwaPageOutline {i18n}>
      {#snippet splash()}
        <p>
          This is on the splash screen that should only show up on a full screen. If the screen is small this should be
          completely hidden along with the entire splash
        </p>
      {/snippet}
      {#snippet title()}
        <h4>Awesome Title</h4>
        <p>This is an awesome subtext that is easy to add!</p>
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
    </SiwaPageOutline>
  {/snippet}
</Story>
