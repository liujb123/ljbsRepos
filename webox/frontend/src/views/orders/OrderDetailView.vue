<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { orderApi } from '@/api/orders'
import PriceText from '@/components/PriceText.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { useUiStore } from '@/stores/ui'
import type { ApiError, OrderStatus } from '@/types/api'
import { formatCny, parseCents } from '@/utils/money'

const props = defineProps<{ id: string }>()

const queryClient = useQueryClient()
const ui = useUiStore()
const confirmOpen = ref(false)
const cancelling = ref(false)

const orderQuery = useQuery({
  queryKey: computed(() => ['order', props.id]),
  queryFn: () => orderApi.detail(Number(props.id))
})

const order = computed(() => orderQuery.data.value ?? null)
const canCancel = computed(() => order.value?.status === 'Pending')

const statusClass: Record<OrderStatus, string> = {
  Pending: 'bg-amber-100 text-amber-800',
  Confirmed: 'bg-blue-100 text-blue-800',
  Completed: 'bg-brand-100 text-brand-800',
  Cancelled: 'bg-slate-200 text-slate-600'
}

async function cancelOrder(): Promise<void> {
  if (!order.value || cancelling.value) return
  cancelling.value = true
  try {
    await orderApi.cancel(order.value.id)
    ui.success('Order cancelled.')
    confirmOpen.value = false
    await queryClient.invalidateQueries({ queryKey: ['order', props.id] })
    await queryClient.invalidateQueries({ queryKey: ['orders'] })
  } catch (error) {
    ui.error((error as ApiError).message)
  } finally {
    cancelling.value = false
  }
}

/** Falls back to a zero amount when the order has not loaded yet. */
const displayTotal = computed(() => order.value?.totalAmount ?? formatCny(0))
</script>

<template>
  <section class="mx-auto max-w-3xl space-y-5">
    <RouterLink :to="{ name: 'orders' }" class="text-sm font-medium text-brand-700 hover:underline">
      &larr; My Orders
    </RouterLink>

    <div v-if="orderQuery.isPending.value" class="wb-card animate-pulse p-6">
      <div class="h-6 w-1/2 rounded bg-slate-200"></div>
    </div>

    <div v-else-if="orderQuery.isError.value" class="wb-card p-6 text-center">
      <p class="text-sm text-red-600">This order could not be found.</p>
    </div>

    <template v-else-if="order">
      <div class="wb-card space-y-4 p-5">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h1 class="text-xl font-semibold text-slate-800">Order details</h1>
            <p class="text-sm text-slate-500">{{ order.orderNo }}</p>
          </div>
          <span class="wb-chip" :class="statusClass[order.status]">{{ order.status }}</span>
        </div>

        <dl class="grid gap-3 text-sm sm:grid-cols-2">
          <div>
            <dt class="text-slate-500">Delivery date</dt>
            <dd class="text-slate-800">{{ order.mealDate }}</dd>
          </div>
          <div>
            <dt class="text-slate-500">Meal</dt>
            <dd class="text-slate-800">{{ order.mealSlot }}</dd>
          </div>
          <div class="sm:col-span-2">
            <dt class="text-slate-500">Delivery address</dt>
            <dd class="text-slate-800">{{ order.deliveryAddress }}</dd>
          </div>
          <div v-if="order.note" class="sm:col-span-2">
            <dt class="text-slate-500">Note</dt>
            <dd class="text-slate-800">{{ order.note }}</dd>
          </div>
        </dl>
      </div>

      <div class="wb-card p-5">
        <h2 class="mb-3 text-base font-semibold text-slate-800">Items</h2>
        <ul class="divide-y divide-slate-100">
          <li v-for="line in order.items" :key="line.id" class="flex items-start justify-between gap-4 py-3">
            <div class="min-w-0">
              <p class="font-medium text-slate-800">{{ line.dishName }}</p>
              <p v-if="line.options.length > 0" class="text-xs text-slate-500">
                {{ line.options.map((option) => option.optionName).join(' + ') }}
              </p>
              <p class="text-xs text-slate-500">
                {{ formatCny(parseCents(line.unitPrice)) }} × {{ line.quantity }}
              </p>
            </div>
            <PriceText :amount="line.subtotal" />
          </li>
        </ul>

        <div class="mt-4 flex items-center justify-between border-t border-slate-200 pt-4">
          <span class="font-medium text-slate-700">Total</span>
          <PriceText :amount="displayTotal" size="lg" />
        </div>
      </div>

      <div class="flex justify-end">
        <button v-if="canCancel" type="button" class="wb-btn-danger" @click="confirmOpen = true">
          Cancel order
        </button>
      </div>
    </template>

    <ConfirmDialog
      :open="confirmOpen"
      title="Cancel this order?"
      :body="`This will cancel order ${order?.orderNo ?? ''}. This cannot be undone.`"
      confirm-label="Cancel order"
      dismiss-label="Keep order"
      tone="danger"
      @confirm="cancelOrder"
      @cancel="confirmOpen = false"
    />
  </section>
</template>
