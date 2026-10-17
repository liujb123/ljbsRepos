<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { orderApi } from '@/api/orders'
import PriceText from '@/components/PriceText.vue'

const props = defineProps<{ id: string }>()

const orderQuery = useQuery({
  queryKey: computed(() => ['order', props.id]),
  queryFn: () => orderApi.detail(Number(props.id))
})

const order = computed(() => orderQuery.data.value ?? null)
</script>

<template>
  <section class="mx-auto max-w-2xl space-y-5">
    <div v-if="orderQuery.isPending.value" class="wb-card animate-pulse p-6">
      <div class="h-6 w-2/3 rounded bg-slate-200"></div>
    </div>

    <div v-else-if="orderQuery.isError.value" class="wb-card p-6 text-center">
      <p class="text-sm text-red-600">This order could not be found.</p>
    </div>

    <template v-else-if="order">
      <div class="wb-card space-y-4 p-6 text-center">
        <div
          class="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-brand-100 text-xl text-brand-700"
        >
          &#10003;
        </div>
        <h1 class="text-xl font-semibold text-slate-800">Order placed successfully</h1>

        <dl class="space-y-2 text-left text-sm">
          <div class="flex justify-between gap-4 border-b border-slate-100 pb-2">
            <dt class="text-slate-500">Order number</dt>
            <dd class="font-semibold text-slate-800">{{ order.orderNo }}</dd>
          </div>
          <div class="flex justify-between gap-4 border-b border-slate-100 pb-2">
            <dt class="text-slate-500">Delivery date</dt>
            <dd class="text-slate-800">{{ order.mealDate }}</dd>
          </div>
          <div class="flex justify-between gap-4 border-b border-slate-100 pb-2">
            <dt class="text-slate-500">Meal</dt>
            <dd class="text-slate-800">{{ order.mealSlot }}</dd>
          </div>
          <div class="flex justify-between gap-4 border-b border-slate-100 pb-2">
            <dt class="text-slate-500">Delivery address</dt>
            <dd class="max-w-[60%] text-right text-slate-800">{{ order.deliveryAddress }}</dd>
          </div>
          <div class="flex justify-between gap-4">
            <dt class="text-slate-500">Total</dt>
            <dd><PriceText :amount="order.totalAmount" /></dd>
          </div>
        </dl>

        <p v-if="order.slotAdjusted" class="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800">
          Your requested meal had already closed for ordering, so this order was placed for
          {{ order.mealDate }} {{ order.mealSlot }} instead.
        </p>

        <div class="flex flex-col gap-2 pt-2 sm:flex-row sm:justify-center">
          <RouterLink :to="{ name: 'orders' }" class="wb-btn-primary">View my orders</RouterLink>
          <RouterLink :to="{ name: 'menu' }" class="wb-btn-secondary">Back to menu</RouterLink>
        </div>
      </div>
    </template>
  </section>
</template>
