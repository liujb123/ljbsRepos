<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { usePreferenceStore } from '@/stores/preference'
import { useUiStore } from '@/stores/ui'
import EmptyState from '@/components/EmptyState.vue'
import type { Allergen, ApiError, DishCategory, SpiceLevel, TastePreference } from '@/types/api'
import {
  ALLERGENS,
  DISH_CATEGORIES,
  SPICE_LEVELS,
  TASTE_PREFERENCES
} from '@/types/api'
import { centsToAmount, parseCents } from '@/utils/money'

const preferences = usePreferenceStore()
const ui = useUiStore()

const loading = ref(true)
const saving = ref(false)
const loadError = ref<string | null>(null)

const allergens = ref<Allergen[]>([])
const preferredCategories = ref<DishCategory[]>([])
const spicePreference = ref<SpiceLevel | ''>('')
const tastePreference = ref<TastePreference | ''>('')
const budgetMin = ref('')
const budgetMax = ref('')
const budgetError = ref<string | null>(null)

function applyLoaded(): void {
  const current = preferences.preferences
  if (!current) return
  allergens.value = [...current.allergens]
  preferredCategories.value = [...current.preferredCategories]
  spicePreference.value = current.spicePreference ?? ''
  tastePreference.value = current.tastePreference ?? ''
  budgetMin.value = current.budgetMin ? centsToAmount(parseCents(current.budgetMin)) : ''
  budgetMax.value = current.budgetMax ? centsToAmount(parseCents(current.budgetMax)) : ''
}

onMounted(async () => {
  try {
    await preferences.load()
    applyLoaded()
  } catch (error) {
    loadError.value = (error as ApiError).message
  } finally {
    loading.value = false
  }
})

watch(
  [budgetMin, budgetMax],
  () => {
    if (budgetMin.value && budgetMax.value && parseCents(budgetMin.value) > parseCents(budgetMax.value)) {
      budgetError.value = 'Minimum budget cannot be greater than maximum budget.'
    } else {
      budgetError.value = null
    }
  }
)

function toggle<T>(list: T[], value: T): T[] {
  return list.includes(value) ? list.filter((item) => item !== value) : [...list, value]
}

async function save(): Promise<void> {
  if (saving.value || budgetError.value) return
  saving.value = true
  try {
    await preferences.save({
      allergens: allergens.value,
      preferredCategories: preferredCategories.value,
      spicePreference: spicePreference.value || null,
      tastePreference: tastePreference.value || null,
      budgetMin: budgetMin.value ? centsToAmount(parseCents(budgetMin.value)) : null,
      budgetMax: budgetMax.value ? centsToAmount(parseCents(budgetMax.value)) : null
    })
    applyLoaded()
    ui.success('Preferences saved.')
  } catch (error) {
    ui.error((error as ApiError).message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="mx-auto max-w-2xl space-y-5">
    <header class="flex flex-wrap items-center justify-between gap-3">
      <h1 class="text-xl font-semibold text-slate-800">Dietary preferences</h1>
      <label class="flex items-center gap-2 text-sm text-slate-600">
        <input
          type="checkbox"
          class="h-4 w-4 accent-brand-600"
          :checked="preferences.recommendEnabled"
          @change="preferences.setRecommendEnabled(($event.target as HTMLInputElement).checked)"
        />
        Recommend for me
      </label>
    </header>

    <div v-if="loading" class="wb-card animate-pulse p-6">
      <div class="h-5 w-1/2 rounded bg-slate-200"></div>
    </div>

    <EmptyState v-else-if="loadError" :message="loadError" action-label="Try again" @action="$router.go(0)" />

    <template v-else>
      <div class="wb-card space-y-3 p-5">
        <h2 class="text-base font-semibold text-slate-800">Allergens</h2>
        <p class="text-xs text-slate-500">
          We'll warn you when you add a dish containing these allergens. Dishes are never hidden.
        </p>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="allergen in ALLERGENS"
            :key="allergen"
            type="button"
            class="wb-chip border transition-colors"
            :class="
              allergens.includes(allergen)
                ? 'border-brand-500 bg-brand-600 text-white'
                : 'border-slate-300 bg-white text-slate-600 hover:bg-slate-50'
            "
            @click="allergens = toggle(allergens, allergen)"
          >
            {{ allergen }}
          </button>
        </div>
      </div>

      <div class="wb-card space-y-3 p-5">
        <h2 class="text-base font-semibold text-slate-800">Preferred cuisines</h2>
        <p class="text-xs text-slate-500">
          Used to sort and highlight dishes when "Recommend for me" is on.
        </p>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="category in DISH_CATEGORIES"
            :key="category"
            type="button"
            class="wb-chip border transition-colors"
            :class="
              preferredCategories.includes(category)
                ? 'border-brand-500 bg-brand-600 text-white'
                : 'border-slate-300 bg-white text-slate-600 hover:bg-slate-50'
            "
            @click="preferredCategories = toggle(preferredCategories, category)"
          >
            {{ category }}
          </button>
        </div>
      </div>

      <div class="wb-card grid gap-4 p-5 sm:grid-cols-2">
        <div>
          <label class="wb-label" for="spice">Spice preference</label>
          <select id="spice" v-model="spicePreference" class="wb-input">
            <option value="">No preference</option>
            <option v-for="level in SPICE_LEVELS" :key="level" :value="level">{{ level }}</option>
          </select>
        </div>
        <div>
          <label class="wb-label" for="taste">Taste preference</label>
          <select id="taste" v-model="tastePreference" class="wb-input">
            <option value="">No preference</option>
            <option v-for="taste in TASTE_PREFERENCES" :key="taste" :value="taste">{{ taste }}</option>
          </select>
          <p class="mt-1 text-xs text-slate-500">Used for personalized recommendations.</p>
        </div>
      </div>

      <div class="wb-card space-y-3 p-5">
        <h2 class="text-base font-semibold text-slate-800">Per-meal budget</h2>
        <div class="grid gap-4 sm:grid-cols-2">
          <div>
            <label class="wb-label" for="budget-min">Minimum</label>
            <input
              id="budget-min"
              v-model="budgetMin"
              type="number"
              min="0"
              step="0.01"
              class="wb-input"
              placeholder="15.00"
            />
          </div>
          <div>
            <label class="wb-label" for="budget-max">Maximum</label>
            <input
              id="budget-max"
              v-model="budgetMax"
              type="number"
              min="0"
              step="0.01"
              class="wb-input"
              placeholder="30.00"
            />
          </div>
        </div>
        <p v-if="budgetError" class="text-xs text-red-600">{{ budgetError }}</p>
      </div>

      <div class="flex justify-end">
        <button type="button" class="wb-btn-primary" :disabled="saving || !!budgetError" @click="save">
          {{ saving ? 'Saving...' : 'Save preferences' }}
        </button>
      </div>
    </template>
  </section>
</template>
