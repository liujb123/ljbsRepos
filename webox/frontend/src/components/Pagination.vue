<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  page: number
  totalPages: number
}>()

const emit = defineEmits<{ (event: 'change', page: number): void }>()

const canGoBack = computed(() => props.page > 0)
const canGoForward = computed(() => props.page < props.totalPages - 1)
const label = computed(() => `Page ${props.page + 1} of ${Math.max(props.totalPages, 1)}`)

function go(target: number): void {
  if (target < 0 || target >= props.totalPages) return
  emit('change', target)
}
</script>

<template>
  <nav v-if="totalPages > 1" class="flex items-center justify-center gap-3 py-4" aria-label="Pagination">
    <button
      type="button"
      class="wb-btn-secondary"
      :disabled="!canGoBack"
      @click="go(page - 1)"
    >
      Previous
    </button>
    <span class="text-sm text-slate-500">{{ label }}</span>
    <button
      type="button"
      class="wb-btn-secondary"
      :disabled="!canGoForward"
      @click="go(page + 1)"
    >
      Next
    </button>
  </nav>
</template>
