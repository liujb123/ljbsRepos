import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import type { Allergen, DishCategory, DishDetailView, MenuItemView } from '@/types/api'
import { parseCents, sumCents } from '@/utils/money'
import { StorageKeys, readJson, writeJson } from '@/utils/storage'

/** One chosen option on a cart line. Prices are integer cents. */
export interface CartOption {
  groupName: string
  optionName: string
  optionId: number
  priceDeltaCents: number
}

export interface CartLine {
  lineKey: string
  dishId: number
  menuDate: string
  name: string
  imageUrl: string | null
  category: DishCategory
  basePriceCents: number
  options: CartOption[]
  unitPriceCents: number
  quantity: number
  allergens: Allergen[]
}

export interface AddToCartInput {
  dishId: number
  name: string
  imageUrl: string | null
  category: DishCategory
  basePrice: string
  allergens: Allergen[]
  menuDate: string
  options: CartOption[]
}

const MAX_ITEMS_PER_ORDER = 5

/**
 * The cart.
 *
 * It is client state by design (see docs/ARCHITECTURE.md ADR-04): the PRD has no cross-device
 * cart requirement, and every rule it implies is re-validated server-side when the order is
 * submitted. Lines are keyed by dish *and* configuration, so a whole-wheat burger and a plain
 * one are separate rows (PRD 3.3).
 */
export const useCartStore = defineStore('cart', () => {
  const lines = ref<CartLine[]>(readJson<CartLine[]>(StorageKeys.cart, []))

  watch(
    lines,
    (value) => writeJson(StorageKeys.cart, value),
    { deep: true }
  )

  const totalCents = computed(() =>
    sumCents(lines.value.map((line) => line.unitPriceCents * line.quantity))
  )

  /** Total portions, not distinct dishes - this is the number the 5-item cap applies to. */
  const totalQuantity = computed(() =>
    lines.value.reduce((total, line) => total + line.quantity, 0)
  )

  const isEmpty = computed(() => lines.value.length === 0)
  const itemLimit = MAX_ITEMS_PER_ORDER
  const canAddMore = computed(() => totalQuantity.value < MAX_ITEMS_PER_ORDER)
  const remainingAllowance = computed(() =>
    Math.max(0, MAX_ITEMS_PER_ORDER - totalQuantity.value)
  )

  /** The menu date the cart belongs to; dishes are only orderable from their own date's menu. */
  const menuDate = computed(() => lines.value[0]?.menuDate ?? null)

  function buildLineKey(dishId: number, options: CartOption[]): string {
    const optionIds = options
      .map((option) => option.optionId)
      .sort((a, b) => a - b)
      .join('-')
    return `${dishId}#${optionIds}`
  }

  function findLine(lineKey: string): CartLine | undefined {
    return lines.value.find((line) => line.lineKey === lineKey)
  }

  function add(input: AddToCartInput): CartLine {
    const lineKey = buildLineKey(input.dishId, input.options)
    const existing = findLine(lineKey)
    if (existing) {
      // Repeated adds of the same configuration increment the quantity (PRD 3.3).
      existing.quantity += 1
      return existing
    }
    const basePriceCents = parseCents(input.basePrice)
    const unitPriceCents = sumCents([
      basePriceCents,
      ...input.options.map((option) => option.priceDeltaCents)
    ])
    const line: CartLine = {
      lineKey,
      dishId: input.dishId,
      menuDate: input.menuDate,
      name: input.name,
      imageUrl: input.imageUrl,
      category: input.category,
      basePriceCents,
      options: input.options,
      unitPriceCents,
      quantity: 1,
      allergens: input.allergens
    }
    lines.value.push(line)
    return line
  }

  function increment(lineKey: string): void {
    const line = findLine(lineKey)
    if (line && canAddMore.value) {
      line.quantity += 1
    }
  }

  function decrement(lineKey: string): void {
    const line = findLine(lineKey)
    if (!line) return
    line.quantity -= 1
    if (line.quantity <= 0) {
      remove(lineKey)
    }
  }

  function setQuantity(lineKey: string, quantity: number): void {
    const line = findLine(lineKey)
    if (!line) return
    const others = totalQuantity.value - line.quantity
    const bounded = Math.max(1, Math.min(quantity, MAX_ITEMS_PER_ORDER - others))
    line.quantity = bounded
  }

  function remove(lineKey: string): void {
    lines.value = lines.value.filter((line) => line.lineKey !== lineKey)
  }

  function clear(): void {
    lines.value = []
  }

  /** Builds the payload the order endpoints expect. */
  function toOrderLines() {
    return lines.value.map((line) => ({
      lineKey: line.lineKey,
      dishId: line.dishId,
      quantity: line.quantity,
      optionIds: line.options.map((option) => option.optionId)
    }))
  }

  /** Description shown in the cart, e.g. "Whole Wheat + Mustard". */
  function optionSummary(line: CartLine): string {
    return line.options.map((option) => option.optionName).join(' + ')
  }

  /** Adapters so menu cards and the detail page share one code path. */
  function toAddInput(
    source: MenuItemView | DishDetailView,
    menuDate: string,
    options: CartOption[]
  ): AddToCartInput {
    return {
      dishId: source.dishId,
      name: source.name,
      imageUrl: source.imageUrl,
      category: source.category,
      basePrice: source.price,
      allergens: source.allergens,
      menuDate,
      options
    }
  }

  return {
    lines,
    totalCents,
    totalQuantity,
    isEmpty,
    itemLimit,
    canAddMore,
    remainingAllowance,
    menuDate,
    findLine,
    add,
    increment,
    decrement,
    setQuantity,
    remove,
    clear,
    toOrderLines,
    optionSummary,
    toAddInput
  }
})
