<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import { initI18n } from "$lib/i18n"
  import EnterContactMethodField from "$components/features/EnterContactMethodField.svelte"

  const { Story } = defineMeta({
    title: "Features/EnterContactMethodField",
    component: EnterContactMethodField,
    args: {},
  })

  const i18n = initI18n({
    "email-input.email": "Email",
    "email-input.use-phone": "Use phone",
    "sms-input.phone-number": "Phone",
    "sms-input.use-email": "Use email",
  })
</script>

<Story name="Default">
  {#snippet children(args)}
    <EnterContactMethodField {i18n} {...args} />
  {/snippet}
</Story>
