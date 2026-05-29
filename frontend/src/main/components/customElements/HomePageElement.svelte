<svelte:options customElement={{ tag: "home-page", shadow: "none" }} />

<script lang="ts">
  import HomePage from "../pages/HomePage.svelte"
  import { initI18nFromMessagesJson } from "$lib/utils/server"
  import type { CwProps } from "$components/customElements/interfaces/CwProps"

  // All types come in as strings.
  type Props = {} & CwProps

  let { "messages-json": localizedMessagesJson }: Props = $props()

  const i18n = initI18nFromMessagesJson(localizedMessagesJson)
</script>

<HomePage {i18n} />
