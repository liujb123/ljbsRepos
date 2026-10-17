<script setup lang="ts">
defineProps<{
  message: string
  actionLabel?: string
}>()

const emit = defineEmits<{ (event: 'action'): void }>()
</script>

<template>
  <div class="wb-card flex flex-col items-center gap-3 px-6 py-12 text-center">
    <p class="text-sm text-slate-500">{{ message }}</p>
    <button v-if="actionLabel" type="button" class="wb-btn-secondary" @click="emit('action')">
      {{ actionLabel }}
    </button>
  </div>
</template>
