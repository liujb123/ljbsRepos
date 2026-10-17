<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { menuApi } from '@/api/menu'
import { useCartStore, type CartOption } from '@/stores/cart'
import { usePreferenceStore } from '@/stores/preference'
import { useUiStore } from '@/stores/ui'
import { usePendingAction } from '@/composables/usePendingAction'
import OptionGroupPicker from '@/components/OptionGroupPicker.vue'
import AllergenAlertDialog from '@/components/AllergenAlertDialog.vue'
import { formatCny, parseCents, sumCents } from '@/utils/money'

const route = useRoute()
const router = useRouter()
const cart = useCartStore()
const preferences = usePreferenceStore()
const ui = useUiStore()

const dishId = computed(() => Number(route.params.id))
const menuDate = computed(() => (route.query.date as string) ?? '')
const selectedOptions = ref<number[]>([])
const allergenDialogOpen = ref(false)
const flaggedAllergens = ref<string[]>([])
const addAction = usePendingAction(400)

const dishQuery = useQuery({
  queryKey: computed(() => ['dish', dishId.value, menuDate.value]),
  queryFn: () => menuApi.detail(dishId.value, menuDate.value || undefined)
})

const dish = computed(() => dishQuery.data.value ?? null)

// Reset the selection when navigating between dishes.
watch(dishId, () => {
  selectedOptions.value = []
})

/** Live price: base price plus the surcharge of every chosen option (PRD 3.2). */
const unitPriceCents = computed(() => {
  if (!dish.value) return 0
  const deltas = selectedOptions.value.map((optionId) => {
    for (const group of dish.value!.optionGroups) {
      const option = group.options.find((candidate) => candidate.optionId === optionId)
      if (option) return parseCents(option.priceDelta)
    }
    return 0
  })
  return sumCents([parseCents(dish.value.price), ...deltas])
})

const chosenOptions = computed<CartOption[]>(() => {
  if (!dish.value) return []
  const chosen: CartOption[] = []
  for (const group of dish.value.optionGroups) {
    for (const option of group.options) {
      if (selectedOptions.value.includes(option.optionId)) {
        chosen.push({
          groupName: group.name,
          optionName: option.name,
          optionId: option.optionId,
          priceDeltaCents: parseCents(option.priceDelta)
        })
      }
    }
  }
  return chosen
})

const missingRequiredGroup = computed(() => {
  if (!dish.value) return null
  for (const group of dish.value.optionGroups) {
    if (!group.required) continue
    const answered = group.options.some((option) => selectedOptions.value.includes(option.optionId))
    if (!answered) return group.name
  }
  return null
})

const canSubmit = computed(
  () => Boolean(dish.value) && !missingRequiredGroup.value && cart.canAddMore
)

function addToCart(): void {
  if (!dish.value || !canSubmit.value) return
  const flagged = preferences.matchingAllergens(dish.value.allergens)
  if (flagged.length > 0) {
    flaggedAllergens.value = flagged
    allergenDialogOpen.value = true
    return
  }
  commitAdd()
}

function commitAdd(): void {
  const current = dish.value
  if (!current) return
  void addAction.run(() => {
    cart.add(cart.toAddInput(current, menuDate.value, chosenOptions.value))
    ui.success('Added to cart.')
  })
}

function confirmAllergenAdd(): void {
  allergenDialogOpen.value = false
  commitAdd()
}
</script>

<template>
  <section class="space-y-4">
    <button type="button" class="wb-btn-secondary" @click="router.back()">Back to menu</button>

    <div v-if="dishQuery.isPending.value" class="wb-card animate-pulse p-6">
      <div class="h-6 w-1/3 rounded bg-slate-200"></div>
    </div>

    <div v-else-if="dishQuery.isError.value" class="wb-card p-6 text-center">
      <p class="text-sm text-red-600">This dish could not be found.</p>
    </div>

    <article v-else-if="dish" class="wb-card overflow-hidden">
      <div class="grid gap-6 p-5 md:grid-cols-2">
        <div>
          <div class="h-56 w-full overflow-hidden rounded-lg bg-slate-100 sm:h-72">
            <img
              v-if="dish.imageUrl"
              :src="dish.imageUrl"
              :alt="dish.name"
              class="h-full w-full object-cover"
            />
            <div v-else class="flex h-full items-center justify-center text-sm text-slate-400">
              Image unavailable
            </div>
          </div>
        </div>

        <div class="space-y-4">
          <div>
            <h1 class="text-2xl font-semibold text-slate-800">{{ dish.name }}</h1>
            <div class="mt-2 flex flex-wrap items-center gap-2">
              <span class="wb-chip bg-slate-100 text-slate-600">{{ dish.category }}</span>
              <span class="wb-chip bg-slate-100 text-slate-600">Spice level: {{ dish.spiceLevel }}</span>
            </div>
          </div>

          <p class="text-sm text-slate-600">{{ dish.description }}</p>

          <dl class="space-y-2 text-sm">
            <div class="flex gap-2">
              <dt class="w-24 shrink-0 font-medium text-slate-600">Protein</dt>
              <dd class="text-slate-700">
                {{ dish.proteins.length > 0 ? dish.proteins.join(', ') : 'None' }}
              </dd>
            </div>
            <div class="flex gap-2">
              <dt class="w-24 shrink-0 font-medium text-slate-600">Allergens</dt>
              <dd class="text-slate-700">
                {{ dish.allergens.length > 0 ? dish.allergens.join(', ') : 'None' }}
              </dd>
            </div>
          </dl>

          <div class="rounded-lg bg-slate-50 p-3">
            <p class="text-xs uppercase tracking-wide text-slate-500">Price</p>
            <p class="text-xl font-semibold tabular-nums text-slate-800">
              {{ formatCny(unitPriceCents) }}
            </p>
          </div>
        </div>
      </div>

      <div v-if="dish.optionGroups.length > 0" class="border-t border-slate-200 p-5">
        <h2 class="mb-4 text-base font-semibold text-slate-800">Customization</h2>
        <OptionGroupPicker
          :groups="dish.optionGroups"
          :selected="selectedOptions"
          @update:selected="selectedOptions = $event"
        />
      </div>

      <div class="flex flex-col gap-2 border-t border-slate-200 bg-slate-50 p-5 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p class="text-xs text-slate-500">Total for 1</p>
          <p class="text-xl font-semibold tabular-nums text-slate-800">
            {{ formatCny(unitPriceCents) }}
          </p>
        </div>
        <div class="sm:text-right">
          <button type="button" class="wb-btn-primary sm:min-w-48" :disabled="!canSubmit" @click="addToCart">
            Add to Cart
          </button>
          <p v-if="missingRequiredGroup" class="mt-1 text-xs text-amber-700">
            Please choose an option for "{{ missingRequiredGroup }}".
          </p>
          <p v-else-if="!cart.canAddMore" class="mt-1 text-xs text-slate-500">
            You can order at most 5 items per order.
          </p>
        </div>
      </div>
    </article>

    <AllergenAlertDialog
      :open="allergenDialogOpen"
      :allergens="flaggedAllergens"
      @confirm="confirmAllergenAdd"
      @cancel="allergenDialogOpen = false"
    />
  </section>
</template>
