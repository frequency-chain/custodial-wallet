<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import Input from "$components/atoms/Input.svelte"
  import { InputType } from "$components/enums/InputType"

  const { Story } = defineMeta({
    title: "Atoms/Input",
    component: Input,
    argTypes: {
      id: { control: "text" },
      label: { control: "text" },
      isRequired: { control: "boolean" },
      type: {
        control: {
          type: "select",
          labels: [InputType.TEXT, InputType.NUMBER],
        },
      },
      placeholder: { control: "text" },
      errorMessage: { control: "text" },
    },
  })

  let inputValue = $state("")
</script>

<Story name="Default" args={{ id: "test", label: "Label" }}>
  {#snippet children(args)}
    <Input {...args} />
  {/snippet}
</Story>

<Story name="Default isRequired" args={{ id: "test", label: "Label", isRequired: true }}>
  {#snippet children(args)}
    <Input {...args} />
  {/snippet}
</Story>

<Story name="Default with placeholder" args={{ id: "test", label: "Label", placeholder: "placeholder" }}>
  {#snippet children(args)}
    <Input {...args} />
  {/snippet}
</Story>

<Story name="Default with hint" args={{ id: "test", label: "Label", hint: "Hint message here" }}>
  {#snippet children(args)}
    <Input {...args} />
  {/snippet}
</Story>

<Story name="Error" args={{ id: "test", label: "Label", errorMessage: "error message here" }}>
  {#snippet children(args)}
    <Input {...args} />
  {/snippet}
</Story>

<Story
  name="Phone Number"
  args={{
    id: "test",
    label: "Phone Number",
    type: InputType.PHONE,
    hint: "Please enter your phone number",
    errorMessage: "Error Message",
  }}
>
  {#snippet children(args)}
    <Input {...args} />
  {/snippet}
</Story>

<Story
  name="OTP"
  args={{
    id: "test",
    label: "One Time Passcode",
    type: InputType.CODE,
    hint: "Please enter the 6-digit code sent to you via SMS",
    errorMessage: "Error Message",
  }}
>
  {#snippet children(args)}
    <Input {...args} bind:inputValue />
  {/snippet}
</Story>

<Story
  name="Default but Number type"
  args={{
    id: "test",
    label: "Label",
    type: InputType.NUMBER,
    hint: "There's a little increment/decrement component at the end of the label you can use to increase/decrease a number by 1",
    placeholder: "number goes here",
  }}
>
  {#snippet children(args)}
    <Input {...args} />
  {/snippet}
</Story>
