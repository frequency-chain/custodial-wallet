import { Accordion as BitsAccordion } from "bits-ui"
import { default as Item } from "./Item.svelte"

export const Accordion = {
  Root: BitsAccordion.Root,
  Item: Item,
} as const
