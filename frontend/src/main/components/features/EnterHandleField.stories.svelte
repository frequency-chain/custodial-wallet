<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import { initI18n } from "$lib/i18n"
  import EnterHandleField from "$components/features/EnterHandleField.svelte"

  const { Story } = defineMeta({
    title: "Features/EnterHandleField",
    component: EnterHandleField,
    args: {},
  })

  const i18n = initI18n({
    "enter-handle-field.label": "Handle",
    "enter-handle-field.help.title": "Handle requirements",
    "enter-handle-field.help.requirements.1.before-link":
      "Keep it appropriate - no offensive language. For additional rules, please see ",
    "enter-handle-field.help.requirements.1.after-link": ".",
    "enter-handle-field.help.requirements.1.link-text": "Terms of Service",
    "enter-handle-field.help.requirements.2":
      "Frequency will automatically add a few numbers to the end of your handle to make sure it's unique.",
  })
</script>

<Story name="Default">
  {#snippet children(args)}
    <EnterHandleField {i18n} {...args} />
  {/snippet}
</Story>

<Story name="Error" args={{ errorMessage: "Invalid handle" }}>
  {#snippet children(args)}
    <EnterHandleField {i18n} {...args} />
  {/snippet}
</Story>
