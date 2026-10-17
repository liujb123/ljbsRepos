<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import type { ApiError } from '@/types/api'
import { validateEmail } from '@/utils/validation'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const email = ref('')
const password = ref('')
const emailError = ref<string | null>(null)
const formError = ref<string | null>(null)
const submitting = ref(false)

async function onSubmit(): Promise<void> {
  if (submitting.value) return
  formError.value = null
  emailError.value = validateEmail(email.value)
  if (emailError.value || !password.value) {
    if (!password.value) formError.value = 'Password is required.'
    return
  }

  submitting.value = true
  try {
    const user = await auth.login({ email: email.value.trim(), password: password.value })
    const redirect = route.query.redirect
    if (typeof redirect === 'string' && redirect) {
      await router.push(redirect)
    } else {
      await router.push({ name: user.role === 'ADMIN' ? 'console-dishes' : 'menu' })
    }
  } catch (error) {
    // The server message is already user-facing English ("Incorrect email or password.").
    formError.value = (error as ApiError).message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-slate-100 px-4 py-10">
    <div class="w-full max-w-sm">
      <h1 class="mb-1 text-center text-2xl font-bold text-brand-700">WeBox</h1>
      <p class="mb-6 text-center text-sm text-slate-500">Sign in to WeBox</p>

      <form class="wb-card space-y-4 p-6" novalidate @submit.prevent="onSubmit">
        <div>
          <label class="wb-label" for="login-email">Work email</label>
          <input
            id="login-email"
            v-model="email"
            type="email"
            class="wb-input"
            placeholder="you@company.com"
            maxlength="200"
            autocomplete="email"
            @blur="emailError = validateEmail(email)"
          />
          <p v-if="emailError" class="mt-1 text-xs text-red-600">{{ emailError }}</p>
        </div>

        <div>
          <label class="wb-label" for="login-password">Password</label>
          <input
            id="login-password"
            v-model="password"
            type="password"
            class="wb-input"
            maxlength="64"
            autocomplete="current-password"
          />
        </div>

        <p v-if="formError" class="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
          {{ formError }}
        </p>

        <button type="submit" class="wb-btn-primary w-full" :disabled="submitting">
          {{ submitting ? 'Signing in...' : 'Sign in' }}
        </button>

        <p class="text-center text-sm text-slate-500">
          New to WeBox?
          <RouterLink :to="{ name: 'register' }" class="font-medium text-brand-700 hover:underline">
            Create an account
          </RouterLink>
        </p>
      </form>
    </div>
  </div>
</template>
