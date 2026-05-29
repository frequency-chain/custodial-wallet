<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import CaretRight from "$components/icons/CaretRight.svelte"

  const { Story } = defineMeta({ title: "Icons" })
</script>

<script>
  import Delete from "$components/icons/Delete.svelte"
  import Exit from "$components/icons/Exit.svelte"
  import CreateNew from "$components/icons/CreateNew.svelte"
  import Warning from "$components/icons/Warning.svelte"
  import Token from "$components/icons/Token.svelte"
  import Wallet from "$components/icons/Wallet.svelte"
  import Copy from "$components/icons/Copy.svelte"
  import Graph from "$components/icons/Graph.svelte"
  import Like from "$components/icons/Like.svelte"
  import Person from "$components/icons/Person.svelte"
  import Reply from "$components/icons/Reply.svelte"
  import CircledCheckmark from "$components/icons/CircledCheckmark.svelte"
  import PersonKey from "$components/icons/PersonKey.svelte"
  import FrequencyLogo from "$components/icons/FrequencyLogo.svelte"
  import UniversalHandle from "$components/icons/UniversalHandle.svelte"
  import SocialFingerprint from "$components/icons/SocialFingerprint.svelte"
  import Edit from "$components/icons/Edit.svelte"
  import Info from "$components/icons/Info.svelte"
  import SmallSpinner from "$components/icons/SmallSpinner.svelte"
  import { CircledCheckmarkColor } from "$components/enums/CircledCheckmarkColor.js"
  import Spinner from "$components/icons/Spinner.svelte"
</script>

<Story name="All Icons">
  {#snippet children()}
    <h5>Large icons</h5>
    <div class="flex items-center gap-8 py-8">
      <CircledCheckmark />
      <CircledCheckmark color={CircledCheckmarkColor.GREEN} />
      <PersonKey />
      <FrequencyLogo />
      <UniversalHandle />
      <SocialFingerprint />
    </div>
    <h5>Small icons</h5>
    <div class="flex items-center gap-4 py-8">
      <CaretRight />
      <Copy />
      <CreateNew />
      <Delete />
      <Edit style={"negative"} />
      <Exit />
      <Graph />
      <Info />
      <Like />
      <Person />
      <Reply />
      <Token />
      <Wallet />
      <Warning />
    </div>
    <h5>Loading animations</h5>
    <div class="flex items-center gap-4 py-8">
      <Spinner />
      <SmallSpinner />
    </div>
  {/snippet}
</Story>
