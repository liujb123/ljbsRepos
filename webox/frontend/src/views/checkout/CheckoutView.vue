<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { orderApi } from '@/api/orders'
import { preferenceApi } from '@/api/preferences'
import { useCartStore } from '@/stores/cart'
import { useUiStore } from '@/stores/ui'
import PriceText from '@/components/PriceText.vue'
import EmptyState from '@/components/EmptyState.vue'
import type { AddressResponse, ApiError, MealSlot, OrderResponse } from '@/types/api'
import { newIdempotencyKey } from '@/utils/idempotency'
import { formatCny, parseCents } from '@/utils/money'
import { validateAddress } from '@/utils/validation'

const router = useRouter()
const cart = useCartStore()
const ui = useUiStore()

/**
 * Generated once per checkout session and reused on every retry. This is the client half of
 * "submitting repeatedly still creates exactly one order".
 */
const idempotencyKey = newIdempotencyKey()

const deliveryDate = ref<string>(cart.menuDate ?? '')
const mealSlot = ref<MealSlot>('Lunch')
const address = ref('')
const note = ref('')
const addresses = ref<AddressResponse[]>([])
const submitting = ref(false)
const submitError = ref<string | null>(null)
const placedOrder = ref<OrderResponse | null>(null)

const orderLines = computed(() => cart.toOrderLines())
const linesKey = computed(() => JSON.stringify(orderLines.value))

const previewQuery = useQuery({
  queryKey: computed(() => ['checkout-preview', linesKey.value, deliveryDate.value, mealSlot.value]),
  enabled: computed(() => !cart.isEmpty),
  queryFn: () =>
    orderApi.preview({
      items: orderLines.value,
      deliveryDate: deliveryDate.value || undefined,
      mealSlot: mealSlot.value
    })
})

const preview = computed(() => previewQuery.data.value ?? null)
const resolved = computed(() => preview.value?.resolved ?? null)
const existingOrder = computed(() => preview.value?.existingOrder ?? null)
const unavailable = computed(() => preview.value?.unavailableItems ?? [])
const budget = computed(() => preview.value?.budget ?? null)

const slotNotice = computed(() => {
  if (!resolved.value?.adjusted) return null
  if (resolved.value.reasonCode === 'LUNCH_CUTOFF_PASSED') {
    return 'Lunch orders must be placed before 10:00. Your order will be for Dinner instead.'
  }
  if (resolved.value.reasonCode === 'DINNER_CUTOFF_PASSED') {
    return 'Dinner orders must be placed before 15:00. Your order will be for Lunch on the next day instead.'
  }
  return null
})

const addressError = computed(() => (address.value ? validateAddress(address.value) : null))

const canSubmit = computed(
  () =>
    !cart.isEmpty &&
    !submitting.value &&
    unavailable.value.length === 0 &&
    !addressError.value &&
    address.value.trim().length > 0 &&
    preview.value !== null
)

onMounted(async () => {
  if (cart.isEmpty) return
  deliveryDate.value = cart.menuDate ?? ''
  try {
    addresses.value = await preferenceApi.addresses()
    if (addresses.value.length > 0) {
      address.value = addresses.value[0].addressText
    }
  } catch {
    addresses.value = []
  }
})

/** The meal select writes straight through to the preview query; the server decides the rest. */

async function placeOrder(): Promise<void> {
  if (!canSubmit.value || !resolved.value) return
  submitError.value = null
  submitting.value = true
  try {
    const order = await orderApi.place(idempotencyKey, {
      items: orderLines.value,
      deliveryDate: resolved.value.mealDate,
      mealSlot: resolved.value.mealSlot,
      deliveryAddress: address.value.trim(),
      note: note.value.trim() || null
    })
    placedOrder.value = order
    cart.clear()
    await router.push({ name: 'order-success', params: { id: String(order.id) } })
  } catch (error) {
    const apiError = error as ApiError
    submitError.value = apiError.message
    if (apiError.code === 'ORDER_SLOT_CONFLICT') {
      // Refresh so the button switches to "View Existing Order".
      await previewQuery.refetch()
    }
    ui.error(apiError.message)
  } finally {
    submitting.value = false
  }
}

function viewExistingOrder(): void {
  if (existingOrder.value) {
    void router.push({ name: 'order-detail', params: { id: String(existingOrder.value.id) } })
  }
}

function goToMenu(): void {
  void router.push({ name: 'menu' })
}

const bookableSlots = computed(() => {
  const dates = preview.value?.bookableDates ?? []
  const today = dates[0]
  const tomorrow = dates[1]
  const options: { value: string; date: string; slot: MealSlot; label: string }[] = []
  if (today) {
    options.push({ value: `${today}|Lunch`, date: today, slot: 'Lunch', label: `Today (${today}) · Lunch` })
    options.push({ value: `${today}|Dinner`, date: today, slot: 'Dinner', label: `Today (${today}) · Dinner` })
  }
  if (tomorrow) {
    options.push({
      value: `${tomorrow}|Lunch`,
      date: tomorrow,
      slot: 'Lunch',
      label: `Tomorrow (${tomorrow}) · Lunch`
    })
  }
  return options
})

const selectedSlotValue = computed({
  get: () => `${deliveryDate.value}|${mealSlot.value}`,
  set: (value: string) => {
    const [date, slot] = value.split('|')
    deliveryDate.value = date ?? ''
    mealSlot.value = (slot as MealSlot) ?? 'Lunch'
  }
})
</script>

<template>
  <section class="space-y-5">
    <h1 class="text-xl font-semibold text-slate-800">Checkout</h1>

    <EmptyState
      v-if="cart.isEmpty && !placedOrder"
      message="Your cart is empty."
      action-label="Continue shopping"
      @action="goToMenu"
    />

    <template v-else-if="!cart.isEmpty">
      <!-- Cut-off auto-switch is explained rather than applied silently (PRD 4.2). -->
      <p v-if="slotNotice" class="rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-800">
        {{ slotNotice }}
      </p>

      <p
        v-if="unavailable.length > 0"
        class="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700"
      >
        Some dishes are not available for {{ resolved?.mealDate }} {{ resolved?.mealSlot }}:
        {{ unavailable.map((item) => item.name ?? `#${item.dishId}`).join(', ') }}. Please adjust
        your cart.
      </p>

      <p
        v-if="budget?.exceedsMax"
        class="rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-800"
      >
        Your cart total ({{ formatCny(parseCents(preview?.total)) }}) is above your per-meal budget
        ({{ formatCny(parseCents(budget.max)) }}). You can still place the order.
      </p>

      <div class="grid gap-5 lg:grid-cols-3">
        <div class="space-y-5 lg:col-span-2">
          <div class="wb-card p-4">
            <h2 class="mb-3 text-base font-semibold text-slate-800">Order summary</h2>
            <ul class="divide-y divide-slate-100">
              <li
                v-for="line in cart.lines"
                :key="line.lineKey"
                class="flex items-start justify-between gap-4 py-3"
              >
                <div class="min-w-0">
                  <p class="font-medium text-slate-800">{{ line.name }}</p>
                  <p v-if="line.options.length > 0" class="text-xs text-slate-500">
                    {{ cart.optionSummary(line) }}
                  </p>
                  <p class="text-xs text-slate-500">Quantity: {{ line.quantity }}</p>
                </div>
                <p class="whitespace-nowrap font-semibold tabular-nums text-slate-800">
                  {{ formatCny(line.unitPriceCents * line.quantity) }}
                </p>
              </li>
            </ul>
          </div>

          <div class="wb-card space-y-4 p-4">
            <h2 class="text-base font-semibold text-slate-800">Delivery</h2>

            <div>
              <label class="wb-label" for="meal">Meal</label>
              <select id="meal" v-model="selectedSlotValue" class="wb-input">
                <option v-for="option in bookableSlots" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
            </div>

            <div>
              <label class="wb-label" for="address">Delivery address</label>
              <select
                v-if="addresses.length > 0"
                class="wb-input mb-2"
                aria-label="Select a saved address"
                @change="address = ($event.target as HTMLSelectElement).value"
              >
                <option value="">Select a saved address</option>
                <option v-for="saved in addresses" :key="saved.id" :value="saved.addressText">
                  {{ saved.addressText }}
                </option>
              </select>
              <input
                id="address"
                v-model="address"
                type="text"
                class="wb-input"
                placeholder="Or enter a new address"
                maxlength="200"
              />
              <p v-if="addressError" class="mt-1 text-xs text-red-600">{{ addressError }}</p>
            </div>

            <div>
              <label class="wb-label" for="note">Order note (optional)</label>
              <input
                id="note"
                v-model="note"
                type="text"
                class="wb-input"
                maxlength="200"
                placeholder="Anything the kitchen should know?"
              />
            </div>
          </div>
        </div>

        <div class="space-y-4">
          <div class="wb-card space-y-3 p-4">
            <div>
              <p class="text-xs uppercase tracking-wide text-slate-500">Total</p>
              <PriceText :amount="preview?.total ?? '0.00'" size="lg" />
            </div>

            <p v-if="resolved" class="text-xs text-slate-500">
              {{ resolved.mealDate }} · {{ resolved.mealSlot }}
            </p>

            <!-- PRD 4.2: an existing active order replaces the submit action entirely. -->
            <button
              v-if="existingOrder"
              type="button"
              class="wb-btn-primary w-full"
              @click="viewExistingOrder"
            >
              View Existing Order
            </button>
            <button
              v-else
              type="button"
              class="wb-btn-primary w-full"
              :disabled="!canSubmit"
              @click="placeOrder"
            >
              {{ submitting ? 'Placing order...' : 'Place Order' }}
            </button>

            <p v-if="existingOrder" class="text-xs text-slate-500">
              You already have an active order for this meal ({{ existingOrder.orderNo }}).
            </p>

            <p v-if="submitError" class="rounded-lg bg-red-50 px-3 py-2 text-xs text-red-700">
              {{ submitError }}
            </p>
          </div>
        </div>
      </div>
    </template>
  </section>
</template>
