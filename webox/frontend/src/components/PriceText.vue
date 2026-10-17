<script setup lang="ts">
import { computed } from 'vue'
import { formatCny, parseCents } from '@/utils/money'

/**
 * Renders a monetary amount coming from the API.
 *
 * The API sends a decimal string; it is parsed into integer cents here and formatted, so the
 * displayed value is exact to the cent (PRD 3.2 / 6).
 */
const props = defineProps<{
  amount: string | number | null | undefined
  size?: 'sm' | 'md' | 'lg'
}>()

const sizeClass = computed(() => {
  switch (props.size) {
    case 'lg':
      return 'text-xl font-semibold'
    case 'sm':
      return 'text-sm'
    default:
      return 'text-base font-semibold'
  }
})

const display = computed(() => formatCny(parseCents(props.amount)))
</script>

<template>
  <span :class="sizeClass" class="tabular-nums">{{ display }}</span>
</template>
