<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <div class="flex min-h-screen flex-col items-center justify-center gap-4 bg-slate-100 px-4 text-center">
    <h1 class="text-2xl font-semibold text-slate-800">403</h1>
    <p class="text-sm text-slate-500">You don't have permission to access this page.</p>
    <RouterLink :to="{ name: 'menu' }" class="wb-btn-primary">Back to home</RouterLink>
  </div>
</template>
