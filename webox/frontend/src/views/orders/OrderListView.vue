<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { orderApi } from '@/api/orders'
import PriceText from '@/components/PriceText.vue'
import EmptyState from '@/components/EmptyState.vue'
import Pagination from '@/components/Pagination.vue'
import type { OrderStatus } from '@/types/api'

const page = ref(0)
const pageSize = 10

const ordersQuery = useQuery({
  queryKey: computed(() => ['orders', page.value]),
  queryFn: () => orderApi.list(page.value, pageSize)
})

const orders = computed(() => ordersQuery.data.value?.items ?? [])
const totalPages = computed(() => ordersQuery.data.value?.totalPages ?? 0)

const statusClass: Record<OrderStatus, string> = {
  Pending: 'bg-amber-100 text-amber-800',
  Confirmed: 'bg-blue-100 text-blue-800',
  Completed: 'bg-brand-100 text-brand-800',
  Cancelled: 'bg-slate-200 text-slate-600'
}

function formatDate(value: string): string {
  return value ? value.slice(0, 10) : ''
}
</script>

<template>
  <section class="space-y-5">
    <h1 class="text-xl font-semibold text-slate-800">My Orders</h1>

    <div v-if="ordersQuery.isPending.value" class="wb-card animate-pulse p-6">
      <div class="h-5 w-1/2 rounded bg-slate-200"></div>
    </div>

    <EmptyState
      v-else-if="orders.length === 0"
      message="You have no orders yet."
      action-label="Browse the menu"
      @action="$router.push({ name: 'menu' })"
    />

    <template v-else>
      <ul class="space-y-3">
        <li v-for="order in orders" :key="order.id">
          <RouterLink
            :to="{ name: 'order-detail', params: { id: String(order.id) } }"
            class="wb-card flex flex-wrap items-center justify-between gap-3 p-4 transition-shadow hover:shadow-md"
          >
            <div class="min-w-0">
              <p class="font-medium text-slate-800">{{ order.orderNo }}</p>
              <p class="text-xs text-slate-500">
                {{ order.mealDate }} · {{ order.mealSlot }} · {{ order.itemCount }} item<span
                  v-if="order.itemCount !== 1"
                  >s</span
                >
              </p>
              <p class="text-xs text-slate-400">Placed {{ formatDate(order.createdAt) }}</p>
            </div>
            <div class="flex items-center gap-3">
              <span class="wb-chip" :class="statusClass[order.status]">{{ order.status }}</span>
              <PriceText :amount="order.totalAmount" />
            </div>
          </RouterLink>
        </li>
      </ul>

      <Pagination :page="page" :total-pages="totalPages" @change="page = $event" />
    </template>
  </section>
</template>
