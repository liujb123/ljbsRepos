<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useCartStore } from '@/stores/cart'
import EmptyState from '@/components/EmptyState.vue'
import { formatCny } from '@/utils/money'

const router = useRouter()
const cart = useCartStore()

const lines = computed(() => cart.lines)
const total = computed(() => formatCny(cart.totalCents))
const canAddMore = computed(() => cart.canAddMore)

function goToMenu(): void {
  void router.push({ name: 'menu' })
}

function goToCheckout(): void {
  void router.push({ name: 'checkout' })
}
</script>

<template>
  <section class="space-y-5">
    <header class="flex items-center justify-between">
      <h1 class="text-xl font-semibold text-slate-800">Your Cart</h1>
      <span v-if="!cart.isEmpty" class="text-sm text-slate-500">
        {{ cart.totalQuantity }} / {{ cart.itemLimit }} items
      </span>
    </header>

    <EmptyState
      v-if="cart.isEmpty"
      message="Your cart is empty."
      action-label="Continue shopping"
      @action="goToMenu"
    />

    <template v-else>
      <ul class="space-y-3">
        <li v-for="line in lines" :key="line.lineKey" class="wb-card flex gap-4 p-4">
          <div class="h-20 w-20 shrink-0 overflow-hidden rounded-lg bg-slate-100">
            <img
              v-if="line.imageUrl"
              :src="line.imageUrl"
              :alt="line.name"
              loading="lazy"
              class="h-full w-full object-cover"
            />
          </div>

          <div class="flex min-w-0 flex-1 flex-col gap-2">
            <div class="flex items-start justify-between gap-3">
              <div class="min-w-0">
                <p class="truncate font-medium text-slate-800">{{ line.name }}</p>
                <!-- The chosen configuration is shown so identical dishes stay distinguishable. -->
                <p v-if="line.options.length > 0" class="text-xs text-slate-500">
                  {{ cart.optionSummary(line) }}
                </p>
              </div>
              <p class="whitespace-nowrap font-semibold tabular-nums text-slate-800">
                {{ formatCny(line.unitPriceCents * line.quantity) }}
              </p>
            </div>

            <div class="flex flex-wrap items-center gap-3">
              <div class="flex items-center gap-1">
                <button
                  type="button"
                  class="wb-btn-secondary h-8 w-8 p-0"
                  :aria-label="`Decrease quantity of ${line.name}`"
                  @click="cart.decrement(line.lineKey)"
                >
                  &minus;
                </button>
                <input
                  type="number"
                  class="wb-input h-8 w-16 text-center"
                  :value="line.quantity"
                  min="1"
                  :max="cart.itemLimit"
                  :aria-label="`Quantity of ${line.name}`"
                  @change="cart.setQuantity(line.lineKey, Number(($event.target as HTMLInputElement).value))"
                />
                <button
                  type="button"
                  class="wb-btn-secondary h-8 w-8 p-0"
                  :disabled="!canAddMore"
                  :title="canAddMore ? '' : 'You can order at most 5 items per order.'"
                  :aria-label="`Increase quantity of ${line.name}`"
                  @click="cart.increment(line.lineKey)"
                >
                  +
                </button>
              </div>

              <span class="text-xs tabular-nums text-slate-400">
                {{ formatCny(line.unitPriceCents) }} each
              </span>

              <button
                type="button"
                class="ml-auto text-sm font-medium text-red-600 hover:underline"
                @click="cart.remove(line.lineKey)"
              >
                Remove
              </button>
            </div>
          </div>
        </li>
      </ul>

      <p v-if="!canAddMore" class="rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-800">
        You can order at most 5 items per order.
      </p>

      <div class="wb-card flex flex-col gap-4 p-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p class="text-xs uppercase tracking-wide text-slate-500">Total</p>
          <p class="text-2xl font-semibold tabular-nums text-slate-800">{{ total }}</p>
        </div>
        <div class="flex flex-col gap-2 sm:flex-row">
          <button type="button" class="wb-btn-secondary" @click="goToMenu">Continue shopping</button>
          <button type="button" class="wb-btn-primary" @click="goToCheckout">Checkout</button>
        </div>
      </div>
    </template>
  </section>
</template>
