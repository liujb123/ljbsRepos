<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { menuApi } from '@/api/menu'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { usePreferenceStore } from '@/stores/preference'
import { useUiStore } from '@/stores/ui'
import { useDebouncedRef } from '@/composables/useDebouncedRef'
import { usePendingAction } from '@/composables/usePendingAction'
import DishCard from '@/components/DishCard.vue'
import LoadingGrid from '@/components/LoadingGrid.vue'
import EmptyState from '@/components/EmptyState.vue'
import Pagination from '@/components/Pagination.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import AllergenAlertDialog from '@/components/AllergenAlertDialog.vue'
import { DISH_CATEGORIES, type DishCategory, type MenuItemView } from '@/types/api'

const router = useRouter()
const auth = useAuthStore()
const cart = useCartStore()
const preferences = usePreferenceStore()
const ui = useUiStore()

const selectedDate = ref<string>('')
const selectedCategories = ref<DishCategory[]>([])
const keyword = ref('')
const debouncedKeyword = useDebouncedRef(keyword, 300)
const page = ref(0)
const pageSize = 12

const dates = ref<string[]>([])
const allergenDialog = ref<{ open: boolean; item: MenuItemView | null }>({ open: false, item: null })
const flaggedForDialog = ref<string[]>([])
const dateSwitchDialog = ref(false)
const pendingDateChange = ref<string | null>(null)

const addAction = usePendingAction(400)

const menuQuery = useQuery({
  queryKey: computed(() => [
    'menu',
    selectedDate.value,
    [...selectedCategories.value].sort().join(','),
    debouncedKeyword.value,
    page.value,
    preferences.recommendEnabled
  ]),
  queryFn: () =>
    menuApi.list({
      date: selectedDate.value || undefined,
      categories: selectedCategories.value,
      keyword: debouncedKeyword.value,
      page: page.value,
      size: pageSize,
      prefer: preferences.recommendEnabled
    })
})

const items = computed(() => menuQuery.data.value?.items ?? [])
const totalPages = computed(() => menuQuery.data.value?.totalPages ?? 0)
const total = computed(() => menuQuery.data.value?.total ?? 0)

onMounted(async () => {
  try {
    dates.value = await menuApi.dates()
    selectedDate.value = dates.value[0] ?? ''
  } catch {
    dates.value = []
  }
  if (!preferences.preferences) {
    try {
      await preferences.load()
    } catch {
      // Preferences are optional for browsing.
    }
  }
})

// Any change to the filters restarts paging, so the user never lands on an out-of-range page.
watch([selectedCategories, debouncedKeyword, () => preferences.recommendEnabled], () => {
  page.value = 0
})

function toggleCategory(category: DishCategory): void {
  const index = selectedCategories.value.indexOf(category)
  if (index >= 0) {
    selectedCategories.value.splice(index, 1)
  } else {
    selectedCategories.value.push(category)
  }
}

function clearFilters(): void {
  selectedCategories.value = []
  keyword.value = ''
  page.value = 0
}

function requestDateChange(next: string): void {
  if (!next || next === selectedDate.value) return
  // Dishes belong to one date's menu, so mixing dates in a cart is not meaningful.
  if (!cart.isEmpty && cart.menuDate && cart.menuDate !== next) {
    pendingDateChange.value = next
    dateSwitchDialog.value = true
    return
  }
  selectedDate.value = next
  page.value = 0
}

function confirmDateChange(): void {
  cart.clear()
  if (pendingDateChange.value) {
    selectedDate.value = pendingDateChange.value
    page.value = 0
  }
  pendingDateChange.value = null
  dateSwitchDialog.value = false
}

function cancelDateChange(): void {
  pendingDateChange.value = null
  dateSwitchDialog.value = false
}

function openDetail(item: MenuItemView): void {
  void router.push({ name: 'dish-detail', params: { id: String(item.dishId) }, query: { date: selectedDate.value } })
}

/** Menu-page add flow: dishes with choices go to the detail page to collect them. */
function onAdd(item: MenuItemView): void {
  if (!cart.canAddMore) {
    ui.error('You can order at most 5 items per order.')
    return
  }
  if (item.hasOptions) {
    openDetail(item)
    return
  }
  const flagged = preferences.matchingAllergens(item.allergens)
  if (flagged.length > 0) {
    flaggedForDialog.value = flagged
    allergenDialog.value = { open: true, item }
    return
  }
  commitAdd(item)
}

function commitAdd(item: MenuItemView): void {
  void addAction.run(() => {
    cart.add(
      cart.toAddInput(item, selectedDate.value, [])
    )
    ui.success('Added to cart.')
  })
}

function confirmAllergenAdd(): void {
  const item = allergenDialog.value.item
  allergenDialog.value = { open: false, item: null }
  if (item) {
    commitAdd(item)
  }
}

function cancelAllergenAdd(): void {
  allergenDialog.value = { open: false, item: null }
  flaggedForDialog.value = []
}

const formatDate = (value: string): string => {
  if (!value) return ''
  if (value === dates.value[0]) return `Today (${value})`
  if (dates.value[1] === value) return `Tomorrow (${value})`
  return value
}
</script>

<template>
  <section class="space-y-5">
    <header class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 class="text-xl font-semibold text-slate-800">
          {{ selectedDate && selectedDate !== dates[0] ? `Menu for ${selectedDate}` : "Today's Menu" }}
        </h1>
        <p class="text-sm text-slate-500">
          {{ total }} dish<span v-if="total !== 1">es</span> available
          <span v-if="auth.displayName"> · signed in as {{ auth.displayName }}</span>
        </p>
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <label class="text-sm text-slate-600" for="menu-date">Date</label>
        <select
          id="menu-date"
          class="wb-input w-auto"
          :value="selectedDate"
          @change="requestDateChange(($event.target as HTMLSelectElement).value)"
        >
          <option v-for="value in dates" :key="value" :value="value">
            {{ formatDate(value) }}
          </option>
        </select>
      </div>
    </header>

    <div class="wb-card space-y-4 p-4">
      <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
        <input
          v-model="keyword"
          type="search"
          class="wb-input sm:max-w-xs"
          placeholder="Search dishes by name or description"
          maxlength="50"
          aria-label="Search dishes"
        />
        <label class="flex items-center gap-2 text-sm text-slate-600">
          <input
            type="checkbox"
            class="h-4 w-4 accent-brand-600"
            :checked="preferences.recommendEnabled"
            @change="preferences.setRecommendEnabled(($event.target as HTMLInputElement).checked)"
          />
          Recommend for me
        </label>
        <button
          v-if="selectedCategories.length > 0 || keyword"
          type="button"
          class="wb-btn-secondary sm:ml-auto"
          @click="clearFilters"
        >
          Clear filters
        </button>
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <span class="text-sm font-medium text-slate-600">Cuisine</span>
        <button
          v-for="category in DISH_CATEGORIES"
          :key="category"
          type="button"
          class="wb-chip border transition-colors"
          :class="
            selectedCategories.includes(category)
              ? 'border-brand-500 bg-brand-600 text-white'
              : 'border-slate-300 bg-white text-slate-600 hover:bg-slate-50'
          "
          @click="toggleCategory(category)"
        >
          {{ category }}
        </button>
      </div>
    </div>

    <LoadingGrid v-if="menuQuery.isPending.value" :count="8" />

    <div v-else-if="menuQuery.isError.value" class="wb-card p-6 text-center">
      <p class="mb-3 text-sm text-red-600">Something went wrong. Please try again.</p>
      <button type="button" class="wb-btn-secondary" @click="menuQuery.refetch()">Try again</button>
    </div>

    <EmptyState
      v-else-if="items.length === 0"
      :message="
        keyword || selectedCategories.length > 0
          ? 'No dishes match your search.'
          : 'No menu has been published for this date.'
      "
      :action-label="keyword || selectedCategories.length > 0 ? 'Clear search' : undefined"
      @action="clearFilters"
    />

    <template v-else>
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        <DishCard
          v-for="item in items"
          :key="item.dishId"
          :item="item"
          :can-add-more="cart.canAddMore"
          :flagged-allergens="preferences.matchingAllergens(item.allergens)"
          @add="onAdd(item)"
          @open="openDetail(item)"
        />
      </div>

      <Pagination :page="page" :total-pages="totalPages" @change="page = $event" />
    </template>

    <AllergenAlertDialog
      :open="allergenDialog.open"
      :allergens="flaggedForDialog"
      @confirm="confirmAllergenAdd"
      @cancel="cancelAllergenAdd"
    />

    <ConfirmDialog
      :open="dateSwitchDialog"
      title="Switch menu date?"
      body="Switching the menu date will clear your cart. Continue?"
      confirm-label="Switch and clear"
      dismiss-label="Cancel"
      @confirm="confirmDateChange"
      @cancel="cancelDateChange"
    />
  </section>
</template>
