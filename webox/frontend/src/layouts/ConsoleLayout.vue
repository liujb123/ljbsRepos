<script setup lang="ts">
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/**
 * Console layout.
 *
 * PRD 4.3 requires a navigation layout distinct from the employee app, so this is a separate
 * component with a persistent sidebar rather than a re-skinned employee header.
 */
const router = useRouter()
const auth = useAuthStore()

const navItems = [
  { name: 'console-dishes', label: 'Dishes' },
  { name: 'console-daily-menu', label: 'Daily Menu' }
] as const

function signOut(): void {
  auth.logout()
  void router.push({ name: 'login' })
}
</script>

<template>
  <div class="min-h-screen bg-slate-100">
    <div class="mx-auto flex max-w-7xl flex-col md:flex-row">
      <aside class="w-full shrink-0 border-b border-slate-200 bg-slate-900 md:min-h-screen md:w-60 md:border-b-0">
        <div class="flex items-center justify-between px-5 py-4">
          <span class="text-lg font-bold tracking-tight text-white">Console</span>
          <RouterLink
            :to="{ name: 'menu' }"
            class="text-xs font-medium text-slate-300 hover:text-white md:hidden"
          >
            Employee view
          </RouterLink>
        </div>

        <nav class="flex gap-1 overflow-x-auto px-3 pb-3 md:flex-col md:overflow-visible md:pb-0" aria-label="Console">
          <RouterLink
            v-for="item in navItems"
            :key="item.name"
            :to="{ name: item.name }"
            class="whitespace-nowrap rounded-lg px-3 py-2 text-sm font-medium text-slate-300 hover:bg-slate-800 hover:text-white"
            active-class="bg-slate-800 text-white"
          >
            {{ item.label }}
          </RouterLink>
        </nav>

        <div class="hidden px-5 py-4 md:block">
          <RouterLink
            :to="{ name: 'menu' }"
            class="text-xs font-medium text-slate-400 hover:text-white"
          >
            Employee view
          </RouterLink>
        </div>
      </aside>

      <div class="flex min-w-0 flex-1 flex-col">
        <header class="flex items-center justify-between gap-4 border-b border-slate-200 bg-white px-6 py-3">
          <h1 class="text-sm font-semibold text-slate-700">WeBox Administration</h1>
          <div class="flex items-center gap-3">
            <span class="hidden text-sm text-slate-500 sm:inline">{{ auth.displayName }}</span>
            <button type="button" class="wb-btn-secondary" @click="signOut">Sign out</button>
          </div>
        </header>

        <main class="flex-1 px-4 py-6 sm:px-6">
          <RouterView />
        </main>
      </div>
    </div>
  </div>
</template>
