import { FeatureFlagData } from "$lib/types/data/FeatureFlagData"
import { Readable, readonly, writable } from "svelte/store"

export interface FeatureFlagService {
  data: Readable<FeatureFlagData>
}

export const createFeatureFlagService = (featureFlagList: FeatureFlagData): FeatureFlagService => {
  const dataStore = writable<FeatureFlagData>(featureFlagList)

  return {
    data: readonly(dataStore),
  }
}
