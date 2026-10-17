<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { usePreferenceStore } from '@/stores/preference'

const router = useRouter()
const auth = useAuthStore()
const cart = useCartStore()
const preferences = usePreferenceStore()
const { totalQuantity } = storeToRefs(cart)

const navItems = [
  { name: 'menu', label: 'Menu' },
  { name: 'orders', label: 'My Orders' },
  { name: 'preferences', label: 'Settings' }
] as const

const cartBadge = computed(() => (totalQuantity.value > 99 ? '99+' : String(totalQuantity.value)))

function signOut(): void {
  auth.logout()
  preferences.reset()
  cart.clear()
  void router.push({ name: 'login' })
}
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <header class="sticky top-0 z-30 border-b border-slate-200 bg-white">
      <div class="mx-auto flex h-14 max-w-6xl items-center justify-between gap-4 px-4">
        <RouterLink :to="{ name: 'menu' }" class="text-lg font-bold tracking-tight text-brand-700">
          WeBox
        </RouterLink>

        <nav class="hidden items-center gap-1 md:flex" aria-label="Main">
          <RouterLink
            v-for="item in navItems"
            :key="item.name"
            :to="{ name: item.name }"
            class="rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100 hover:text-slate-900"
            active-class="bg-brand-50 text-brand-700"
          >
            {{ item.label }}
          </RouterLink>
        </nav>

        <div class="flex items-center gap-3">
          <RouterLink
            :to="{ name: 'cart' }"
            class="relative rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100"
          >
            Cart
            <span
              v-if="totalQuantity > 0"
              class="absolute -right-1 -top-1 flex h-5 min-w-[1.25rem] items-center justify-center rounded-full bg-brand-600 px-1 text-[11px] font-semibold text-white"
            >
              {{ cartBadge }}
            </span>
          </RouterLink>

          <RouterLink
            v-if="auth.isAdmin"
            :to="{ name: 'console-dishes' }"
            class="hidden text-sm font-medium text-brand-700 hover:underline sm:inline"
          >
            Console
          </RouterLink>

          <span class="hidden text-sm text-slate-500 sm:inline">{{ auth.displayName }}</span>
          <button type="button" class="wb-btn-secondary" @click="signOut">Sign out</button>
        </div>
      </div>
    </header>

    <main class="mx-auto w-full max-w-6xl flex-1 px-4 py-6 pb-24 md:pb-6">
      <RouterView />
    </main>

    <!-- Mobile navigation: same destinations, thumb-reachable. -->
    <nav
      class="fixed inset-x-0 bottom-0 z-30 border-t border-slate-200 bg-white md:hidden"
      aria-label="Main"
    >
      <div class="grid grid-cols-4">
        <RouterLink
          v-for="item in navItems"
          :key="item.name"
          :to="{ name: item.name }"
          class="flex flex-col items-center gap-0.5 py-2 text-xs font-medium text-slate-500"
          active-class="text-brand-700"
        >
          {{ item.label }}
        </RouterLink>
        <RouterLink
          :to="{ name: 'cart' }"
          class="flex flex-col items-center gap-0.5 py-2 text-xs font-medium text-slate-500"
          active-class="text-brand-700"
        >
          Cart
          <span v-if="totalQuantity > 0" class="text-[10px] text-brand-700">{{ cartBadge }}</span>
        </RouterLink>
      </div>
    </nav>
  </div>
</template>
