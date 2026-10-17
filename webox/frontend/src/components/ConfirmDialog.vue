<script setup lang="ts">
import ModalShell from './ModalShell.vue'

defineProps<{
  open: boolean
  title: string
  body: string
  confirmLabel: string
  dismissLabel: string
  tone?: 'primary' | 'danger'
}>()

const emit = defineEmits<{
  (event: 'confirm'): void
  (event: 'cancel'): void
}>()
</script>

<template>
  <ModalShell :open="open" :title="title" @close="emit('cancel')">
    <p class="mb-5 text-sm text-slate-600">{{ body }}</p>
    <div class="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
      <button type="button" class="wb-btn-secondary" @click="emit('cancel')">
        {{ dismissLabel }}
      </button>
      <button
        type="button"
        :class="tone === 'danger' ? 'wb-btn-danger' : 'wb-btn-primary'"
        @click="emit('confirm')"
      >
        {{ confirmLabel }}
      </button>
    </div>
  </ModalShell>
</template>
