<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <div class="flex min-h-screen flex-col items-center justify-center gap-4 bg-slate-100 px-4 text-center">
    <h1 class="text-2xl font-semibold text-slate-800">Page not found</h1>
    <p class="text-sm text-slate-500">The page you are looking for does not exist.</p>
    <RouterLink :to="{ name: 'menu' }" class="wb-btn-primary">Back to home</RouterLink>
  </div>
</template>
