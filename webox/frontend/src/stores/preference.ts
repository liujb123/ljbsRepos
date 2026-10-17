import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { preferenceApi } from '@/api/preferences'
import type { Allergen, PreferencesRequest, PreferencesResponse } from '@/types/api'
import { StorageKeys, readString, writeString } from '@/utils/storage'

/**
 * Dietary preferences (PRD 4.1).
 *
 * The server copy is the authority; this store keeps a local mirror so the add-to-cart allergen
 * check is instant. The "Recommend for me" switch is a view preference and is stored locally
 * only - turning it off must not erase the preferences themselves.
 */
export const usePreferenceStore = defineStore('preference', () => {
  const preferences = ref<PreferencesResponse | null>(null)
  const recommendEnabled = ref(readString(StorageKeys.recommend) === 'true')

  const allergens = computed<Allergen[]>(() => preferences.value?.allergens ?? [])
  const hasAllergens = computed(() => allergens.value.length > 0)

  async function load(): Promise<void> {
    preferences.value = await preferenceApi.get()
  }

  async function save(payload: PreferencesRequest): Promise<void> {
    preferences.value = await preferenceApi.save(payload)
  }

  function setRecommendEnabled(enabled: boolean): void {
    recommendEnabled.value = enabled
    writeString(StorageKeys.recommend, String(enabled))
  }

  /** Allergens of a dish that the employee has flagged - the set shown in the warning dialog. */
  function matchingAllergens(dishAllergens: Allergen[]): Allergen[] {
    if (!hasAllergens.value) return []
    return dishAllergens.filter((allergen) => allergens.value.includes(allergen))
  }

  function reset(): void {
    preferences.value = null
  }

  return {
    preferences,
    recommendEnabled,
    allergens,
    hasAllergens,
    load,
    save,
    setRecommendEnabled,
    matchingAllergens,
    reset
  }
})
