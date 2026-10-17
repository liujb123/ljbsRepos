<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { adminApi } from '@/api/admin'
import { useUiStore } from '@/stores/ui'
import { useDebouncedRef } from '@/composables/useDebouncedRef'
import Pagination from '@/components/Pagination.vue'
import EmptyState from '@/components/EmptyState.vue'
import PriceText from '@/components/PriceText.vue'
import { DISH_CATEGORIES, type ApiError, type DishAdminView, type DishCategory } from '@/types/api'

const queryClient = useQueryClient()
const ui = useUiStore()

const keyword = ref('')
const debouncedKeyword = useDebouncedRef(keyword, 300)
const category = ref<DishCategory | ''>('')
const statusFilter = ref<'all' | 'available' | 'unavailable'>('all')
const page = ref(0)
const pageSize = 10

const dishesQuery = useQuery({
  queryKey: computed(() => [
    'admin-dishes',
    debouncedKeyword.value,
    category.value,
    statusFilter.value,
    page.value
  ]),
  queryFn: () =>
    adminApi.listDishes({
      keyword: debouncedKeyword.value,
      category: category.value || null,
      available:
        statusFilter.value === 'all' ? null : statusFilter.value === 'available',
      page: page.value,
      size: pageSize
    })
})

const dishes = computed(() => dishesQuery.data.value?.items ?? [])
const totalPages = computed(() => dishesQuery.data.value?.totalPages ?? 0)
const total = computed(() => dishesQuery.data.value?.total ?? 0)

watch([debouncedKeyword, category, statusFilter], () => {
  page.value = 0
})

const availabilityMutation = useMutation({
  mutationFn: (payload: { dish: DishAdminView; available: boolean }) =>
    adminApi.setAvailability(payload.dish.id, payload.available),
  onSuccess: async () => {
    ui.success('Dish updated.')
    await queryClient.invalidateQueries({ queryKey: ['admin-dishes'] })
  },
  onError: (error: ApiError) => ui.error(error.message)
})

function toggleAvailability(dish: DishAdminView): void {
  availabilityMutation.mutate({ dish, available: !dish.available })
}
</script>

<template>
  <section class="space-y-5">
    <header class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 class="text-xl font-semibold text-slate-800">All dishes</h1>
        <p class="text-sm text-slate-500">{{ total }} dish<span v-if="total !== 1">es</span></p>
      </div>
      <RouterLink :to="{ name: 'console-dish-new' }" class="wb-btn-primary">Add dish</RouterLink>
    </header>

    <div class="wb-card flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
      <input
        v-model="keyword"
        type="search"
        class="wb-input sm:max-w-xs"
        placeholder="Search dishes"
        maxlength="50"
        aria-label="Search dishes"
      />

      <select v-model="category" class="wb-input sm:w-48" aria-label="Category">
        <option value="">All categories</option>
        <option v-for="value in DISH_CATEGORIES" :key="value" :value="value">{{ value }}</option>
      </select>

      <select v-model="statusFilter" class="wb-input sm:w-44" aria-label="Status">
        <option value="all">All</option>
        <option value="available">Available</option>
        <option value="unavailable">Unavailable</option>
      </select>
    </div>

    <div v-if="dishesQuery.isPending.value" class="wb-card animate-pulse p-6">
      <div class="h-5 w-1/2 rounded bg-slate-200"></div>
    </div>

    <EmptyState v-else-if="dishes.length === 0" message="No dishes match your search." />

    <div v-else class="wb-card overflow-x-auto">
      <table class="wb-table min-w-[900px]">
        <thead>
          <tr>
            <th scope="col">Dish</th>
            <th scope="col">Category</th>
            <th scope="col">Price</th>
            <th scope="col">Spice</th>
            <th scope="col">Allergens</th>
            <th scope="col">Status</th>
            <th scope="col" class="text-right">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="dish in dishes" :key="dish.id">
            <td>
              <div class="flex items-center gap-3">
                <div class="h-10 w-10 shrink-0 overflow-hidden rounded bg-slate-100">
                  <img
                    v-if="dish.imageUrl"
                    :src="dish.imageUrl"
                    :alt="dish.name"
                    loading="lazy"
                    class="h-full w-full object-cover"
                  />
                </div>
                <div class="min-w-0">
                  <p class="font-medium text-slate-800">{{ dish.name }}</p>
                  <p class="line-clamp-1 text-xs text-slate-500">{{ dish.description }}</p>
                </div>
              </div>
            </td>
            <td class="whitespace-nowrap text-slate-600">{{ dish.category }}</td>
            <td class="whitespace-nowrap"><PriceText :amount="dish.price" size="sm" /></td>
            <td class="whitespace-nowrap text-slate-600">{{ dish.spiceLevel }}</td>
            <td class="text-xs text-slate-500">
              {{ dish.allergens.length > 0 ? dish.allergens.join(', ') : '—' }}
            </td>
            <td>
              <span
                class="wb-chip"
                :class="dish.available ? 'bg-brand-100 text-brand-800' : 'bg-slate-200 text-slate-600'"
              >
                {{ dish.available ? 'Available' : 'Unavailable' }}
              </span>
            </td>
            <td>
              <div class="flex justify-end gap-2 whitespace-nowrap">
                <RouterLink
                  :to="{ name: 'console-dish-edit', params: { id: String(dish.id) } }"
                  class="wb-btn-secondary"
                >
                  Edit
                </RouterLink>
                <button
                  type="button"
                  class="wb-btn-secondary"
                  :disabled="availabilityMutation.isPending.value"
                  @click="toggleAvailability(dish)"
                >
                  {{ dish.available ? 'Set unavailable' : 'Set available' }}
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <Pagination :page="page" :total-pages="totalPages" @change="page = $event" />
  </section>
</template>
