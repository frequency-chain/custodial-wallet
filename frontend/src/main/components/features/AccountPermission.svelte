<script lang="ts">
  import { type Readable } from "svelte/store"
  import { type I18nGetter } from "$lib/i18n"
  import Warning from "$components/icons/Warning.svelte"
  import Graph from "$components/icons/Graph.svelte"
  import Reply from "$components/icons/Reply.svelte"
  import Person from "$components/icons/Person.svelte"
  import CreateNew from "$components/icons/CreateNew.svelte"
  import Delete from "$components/icons/Delete.svelte"
  import Like from "$components/icons/Like.svelte"

  interface Props {
    key: string
    i18n: Readable<I18nGetter>
  }

  const { key, i18n }: Props = $props()

  const Icon = $derived.by(() => {
    switch (key) {
      case "permission.update":
      case "permission.broadcast":
        return CreateNew
      case "permission.tombstone":
        return Delete
      case "permission.account.graph":
        return Graph
      case "permission.reaction":
        return Like
      case "permission.reply":
        return Reply
      case "permission.account.profile-resources":
      case "permission.account.update-identity":
      default:
        return Person
    }
  })
</script>

<li class="flex gap-4 align-middle">
  <div class="content-center">
    <Icon class="flex-shrink-0" />
  </div>
  <p>{$i18n(key)}</p>
</li>
