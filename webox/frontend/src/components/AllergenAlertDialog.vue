<script setup lang="ts">
import ModalShell from './ModalShell.vue'

/**
 * Allergen warning shown when a dish contains something the employee flagged (PRD 4.1).
 *
 * The wording is the one given in the PRD. The dish is never hidden or blocked - the employee
 * decides.
 */
defineProps<{
  open: boolean
  allergens: string[]
}>()

const emit = defineEmits<{
  (event: 'confirm'): void
  (event: 'cancel'): void
}>()
</script>

<template>
  <ModalShell :open="open" title="Allergen alert" @close="emit('cancel')">
    <p class="mb-5 text-sm text-slate-600">
      This dish contains an allergen you flagged: [{{ allergens.join(', ') }}]. Add anyway?
    </p>
    <div class="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
      <button type="button" class="wb-btn-secondary" @click="emit('cancel')">Cancel</button>
      <button type="button" class="wb-btn-primary" @click="emit('confirm')">Add anyway</button>
    </div>
  </ModalShell>
</template>
