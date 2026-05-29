import { Tabs as BitsTabs } from "bits-ui"
import { default as List } from "./List.svelte"

export const Tabs = {
  Root: BitsTabs.Root,
  List: List,
  Content: BitsTabs.Content,
} as const

export interface Tab {
  title: string
  id: string
}
