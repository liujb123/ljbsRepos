<script setup lang="ts">
/** Minimal accessible modal shell shared by the confirm and allergen dialogs. */
defineProps<{
  title: string
  open: boolean
}>()

const emit = defineEmits<{ (event: 'close'): void }>()
</script>

<template>
  <Transition
    enter-active-class="transition duration-150"
    enter-from-class="opacity-0"
    leave-active-class="transition duration-100"
    leave-to-class="opacity-0"
  >
    <div
      v-if="open"
      class="fixed inset-0 z-50 flex items-end justify-center bg-slate-900/40 p-4 sm:items-center"
      role="dialog"
      aria-modal="true"
      @click.self="emit('close')"
    >
      <div class="wb-card w-full max-w-md p-5">
        <h2 class="mb-3 text-lg font-semibold text-slate-800">{{ title }}</h2>
        <slot />
      </div>
    </div>
  </Transition>
</template>
