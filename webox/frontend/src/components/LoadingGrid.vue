<script setup lang="ts">
/** Simple skeleton grid used while the menu loads, so the layout never jumps. */
withDefaults(defineProps<{ count?: number }>(), { count: 6 })
</script>

<template>
  <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
    <div v-for="index in count" :key="index" class="wb-card animate-pulse overflow-hidden">
      <div class="h-36 w-full bg-slate-200"></div>
      <div class="space-y-2 p-4">
        <div class="h-4 w-3/4 rounded bg-slate-200"></div>
        <div class="h-3 w-full rounded bg-slate-100"></div>
        <div class="h-3 w-2/3 rounded bg-slate-100"></div>
      </div>
    </div>
  </div>
</template>
