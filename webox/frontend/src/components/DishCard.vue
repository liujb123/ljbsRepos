<script setup lang="ts">
import { computed } from 'vue'
import PriceText from './PriceText.vue'
import type { Allergen, MenuItemView } from '@/types/api'

const props = defineProps<{
  item: MenuItemView
  canAddMore: boolean
  flaggedAllergens: Allergen[]
}>()

const emit = defineEmits<{
  (event: 'add'): void
  (event: 'open'): void
}>()

const spiceBadgeClass = computed(() => {
  switch (props.item.spiceLevel) {
    case 'Hot':
      return 'bg-red-100 text-red-700'
    case 'Medium':
      return 'bg-orange-100 text-orange-700'
    case 'Mild':
      return 'bg-amber-100 text-amber-700'
    default:
      return ''
  }
})

const hasAllergenWarning = computed(() => props.flaggedAllergens.length > 0)
</script>

<template>
  <article
    class="wb-card flex flex-col overflow-hidden transition-shadow hover:shadow-md"
    :class="item.recommended ? 'ring-2 ring-brand-300' : ''"
  >
    <button
      type="button"
      class="block w-full text-left"
      :aria-label="`View details for ${item.name}`"
      @click="emit('open')"
    >
      <div class="h-36 w-full overflow-hidden bg-slate-100">
        <img
          v-if="item.imageUrl"
          :src="item.imageUrl"
          :alt="item.name"
          loading="lazy"
          class="h-full w-full object-cover"
        />
        <div v-else class="flex h-full items-center justify-center text-xs text-slate-400">
          Image unavailable
        </div>
      </div>
    </button>

    <div class="flex flex-1 flex-col gap-2 p-4">
      <div class="flex items-start justify-between gap-2">
        <h3 class="text-base font-semibold leading-tight text-slate-800" :title="item.name">
          {{ item.name }}
        </h3>
        <PriceText :amount="item.price" />
      </div>

      <p class="line-clamp-2 text-xs text-slate-500">{{ item.description }}</p>

      <div class="flex flex-wrap items-center gap-1.5">
        <span class="wb-chip bg-slate-100 text-slate-600">{{ item.category }}</span>
        <span v-if="item.spiceLevel !== 'None'" class="wb-chip" :class="spiceBadgeClass">
          {{ item.spiceLevel }}
        </span>
        <span
          v-for="tag in item.matchTags"
          :key="tag"
          class="wb-chip bg-brand-50 text-brand-700"
        >
          {{ tag }}
        </span>
      </div>

      <p v-if="hasAllergenWarning" class="text-xs font-medium text-amber-700">
        Contains: {{ flaggedAllergens.join(', ') }}
      </p>

      <div class="mt-auto pt-2">
        <button
          type="button"
          class="wb-btn-primary w-full"
          :disabled="!canAddMore"
          :title="canAddMore ? '' : 'You can order at most 5 items per order.'"
          @click="emit('add')"
        >
          Add to Cart
        </button>
        <p v-if="!canAddMore" class="mt-1 text-center text-xs text-slate-500">
          You can order at most 5 items per order.
        </p>
      </div>
    </div>
  </article>
</template>
