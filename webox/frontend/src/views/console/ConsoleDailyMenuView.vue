<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { adminApi } from '@/api/admin'
import { useUiStore } from '@/stores/ui'
import { useDebouncedRef } from '@/composables/useDebouncedRef'
import type { ApiError, DishAdminView } from '@/types/api'

const queryClient = useQueryClient()
const ui = useUiStore()

/** The PRD asks for tomorrow by default, and the same screen adjusts today's menu. */
function isoDate(offsetDays: number): string {
  const date = new Date()
  date.setDate(date.getDate() + offsetDays)
  return date.toISOString().slice(0, 10)
}

const selectedDate = ref(isoDate(1))
const quantities = ref<Record<number, number>>({})
const selectedIds = ref<number[]>([])
const keyword = ref('')
const debouncedKeyword = useDebouncedRef(keyword, 300)
const page = ref(0)
const pageSize = 20

const catalogQuery = useQuery({
  queryKey: computed(() => ['admin-dishes', 'daily-menu-picker', debouncedKeyword.value, page.value]),
  queryFn: () =>
    adminApi.listDishes({
      keyword: debouncedKeyword.value,
      category: null,
      available: null,
      page: page.value,
      size: pageSize
    })
})

const catalog = computed<DishAdminView[]>(() => catalogQuery.data.value?.items ?? [])
const totalPages = computed(() => catalogQuery.data.value?.totalPages ?? 0)

const existingMenuQuery = useQuery({
  queryKey: computed(() => ['admin-daily-menu', selectedDate.value]),
  queryFn: () => adminApi.getDailyMenu(selectedDate.value)
})

watch(
  () => existingMenuQuery.data.value,
  (menu) => {
    if (!menu) return
    const ids: number[] = []
    const qty: Record<number, number> = {}
    for (const item of menu.items) {
      ids.push(item.dishId)
      qty[item.dishId] = item.totalQty
    }
    selectedIds.value = ids
    quantities.value = qty
  },
  { immediate: true }
)

watch(debouncedKeyword, () => {
  page.value = 0
})

const saveMutation = useMutation({
  mutationFn: () =>
    adminApi.saveDailyMenu(
      selectedDate.value,
      selectedIds.value.map((dishId) => ({
        dishId,
        totalQty: quantities.value[dishId] ?? 0
      }))
    ),
  onSuccess: async () => {
    ui.success('Daily menu saved.')
    await queryClient.invalidateQueries({ queryKey: ['admin-daily-menu', selectedDate.value] })
    await queryClient.invalidateQueries({ queryKey: ['menu'] })
  },
  onError: (error: ApiError) => ui.error(error.message)
})

function isSelected(dishId: number): boolean {
  return selectedIds.value.includes(dishId)
}

function toggleDish(dish: DishAdminView): void {
  if (isSelected(dish.id)) {
    selectedIds.value = selectedIds.value.filter((id) => id !== dish.id)
    delete quantities.value[dish.id]
  } else {
    selectedIds.value = [...selectedIds.value, dish.id]
    quantities.value[dish.id] = quantities.value[dish.id] ?? 50
  }
}

function setQuantity(dishId: number, value: number): void {
  quantities.value[dishId] = Number.isFinite(value) && value >= 0 ? Math.floor(value) : 0
}

const selectedCount = computed(() => selectedIds.value.length)

function save(): void {
  if (selectedCount.value === 0) {
    ui.error('Select at least one dish for this date.')
    return
  }
  saveMutation.mutate()
}

const today = isoDate(0)
const tomorrow = isoDate(1)
</script>

<template>
  <section class="space-y-5">
    <header class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 class="text-xl font-semibold text-slate-800">Daily Menu</h1>
        <p class="text-sm text-slate-500">
          Choose which dishes are served on a date and how many of each.
        </p>
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <label class="text-sm text-slate-600" for="menu-date">Date</label>
        <input id="menu-date" v-model="selectedDate" type="date" class="wb-input w-auto" />
        <button type="button" class="wb-btn-secondary" @click="selectedDate = today">Today</button>
        <button type="button" class="wb-btn-secondary" @click="selectedDate = tomorrow">
          Tomorrow
        </button>
      </div>
    </header>

    <div class="grid gap-5 lg:grid-cols-3">
      <div class="space-y-4 lg:col-span-2">
        <div class="wb-card space-y-3 p-4">
          <div class="flex flex-wrap items-center gap-3">
            <input
              v-model="keyword"
              type="search"
              class="wb-input sm:max-w-xs"
              placeholder="Search dishes"
              maxlength="50"
              aria-label="Search dishes"
            />
            <span class="text-sm text-slate-500">{{ selectedCount }} selected</span>
          </div>

          <div v-if="catalogQuery.isPending.value" class="py-6 text-center text-sm text-slate-500">
            Loading...
          </div>

          <ul v-else class="divide-y divide-slate-100">
            <li v-for="dish in catalog" :key="dish.id" class="flex items-center gap-3 py-2">
              <input
                type="checkbox"
                class="h-4 w-4 shrink-0 accent-brand-600"
                :checked="isSelected(dish.id)"
                :aria-label="`Include ${dish.name}`"
                @change="toggleDish(dish)"
              />

              <div class="h-9 w-9 shrink-0 overflow-hidden rounded bg-slate-100">
                <img
                  v-if="dish.imageUrl"
                  :src="dish.imageUrl"
                  :alt="dish.name"
                  loading="lazy"
                  class="h-full w-full object-cover"
                />
              </div>

              <div class="min-w-0 flex-1">
                <p class="truncate text-sm font-medium text-slate-800">{{ dish.name }}</p>
                <p class="text-xs text-slate-500">
                  {{ dish.category }} ·
                  <span v-if="!dish.available" class="text-amber-700">
                    This dish is currently unavailable and will not be shown to employees.
                  </span>
                  <span v-else>Available</span>
                </p>
              </div>

              <div class="flex shrink-0 items-center gap-2">
                <label class="text-xs text-slate-500" :for="`qty-${dish.id}`">Daily quantity</label>
                <input
                  :id="`qty-${dish.id}`"
                  type="number"
                  min="0"
                  class="wb-input w-20"
                  :disabled="!isSelected(dish.id)"
                  :value="quantities[dish.id] ?? 50"
                  @change="setQuantity(dish.id, Number(($event.target as HTMLInputElement).value))"
                />
              </div>
            </li>
          </ul>

          <div v-if="totalPages > 1" class="flex items-center justify-center gap-3 pt-2">
            <button
              type="button"
              class="wb-btn-secondary"
              :disabled="page === 0"
              @click="page -= 1"
            >
              Previous
            </button>
            <span class="text-sm text-slate-500">Page {{ page + 1 }} of {{ totalPages }}</span>
            <button
              type="button"
              class="wb-btn-secondary"
              :disabled="page >= totalPages - 1"
              @click="page += 1"
            >
              Next
            </button>
          </div>
        </div>
      </div>

      <aside class="space-y-4">
        <div class="wb-card space-y-3 p-4">
          <h2 class="text-base font-semibold text-slate-800">
            Selected for {{ selectedDate }}
          </h2>

          <p v-if="selectedCount === 0" class="text-sm text-slate-500">
            No dishes selected for this date.
          </p>

          <ul v-else class="space-y-2 text-sm">
            <li
              v-for="dishId in selectedIds"
              :key="dishId"
              class="flex items-center justify-between gap-2"
            >
              <span class="truncate text-slate-700">
                {{ catalog.find((dish) => dish.id === dishId)?.name ?? `#${dishId}` }}
              </span>
              <span class="whitespace-nowrap tabular-nums text-slate-500">
                {{ quantities[dishId] ?? 0 }} portions
              </span>
            </li>
          </ul>

          <button
            type="button"
            class="wb-btn-primary w-full"
            :disabled="saveMutation.isPending.value"
            @click="save"
          >
            {{ saveMutation.isPending.value ? 'Saving...' : 'Save daily menu' }}
          </button>

          <p class="text-xs text-slate-500">
            Saving replaces the dish list for this date, keeping the quantities you set.
          </p>
        </div>
      </aside>
    </div>
  </section>
</template>
