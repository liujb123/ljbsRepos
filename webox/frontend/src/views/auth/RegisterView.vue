<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import type { ApiError } from '@/types/api'
import { validateDisplayName, validateEmail, validatePassword } from '@/utils/validation'

const router = useRouter()
const auth = useAuthStore()

const email = ref('')
const displayName = ref('')
const password = ref('')
const confirmPassword = ref('')

const emailError = ref<string | null>(null)
const displayNameError = ref<string | null>(null)
const passwordError = ref<string | null>(null)
const confirmError = ref<string | null>(null)
const formError = ref<string | null>(null)
const submitting = ref(false)

function validate(): boolean {
  emailError.value = validateEmail(email.value)
  displayNameError.value = validateDisplayName(displayName.value)
  passwordError.value = validatePassword(password.value)
  confirmError.value = password.value !== confirmPassword.value ? 'Passwords do not match.' : null
  return !emailError.value && !displayNameError.value && !passwordError.value && !confirmError.value
}

async function onSubmit(): Promise<void> {
  if (submitting.value) return
  formError.value = null
  if (!validate()) return

  submitting.value = true
  try {
    await auth.register({
      email: email.value.trim(),
      password: password.value,
      displayName: displayName.value.trim()
    })
    await router.push({ name: 'menu' })
  } catch (error) {
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
      <p class="mb-6 text-center text-sm text-slate-500">Create your WeBox account</p>

      <form class="wb-card space-y-4 p-6" novalidate @submit.prevent="onSubmit">
        <div>
          <label class="wb-label" for="reg-email">Work email</label>
          <input
            id="reg-email"
            v-model="email"
            type="email"
            class="wb-input"
            placeholder="you@company.com"
            maxlength="200"
            autocomplete="email"
          />
          <p v-if="emailError" class="mt-1 text-xs text-red-600">{{ emailError }}</p>
        </div>

        <div>
          <label class="wb-label" for="reg-name">Display name</label>
          <input
            id="reg-name"
            v-model="displayName"
            type="text"
            class="wb-input"
            placeholder="e.g. Alex Chen"
            maxlength="50"
            autocomplete="name"
          />
          <p v-if="displayNameError" class="mt-1 text-xs text-red-600">{{ displayNameError }}</p>
        </div>

        <div>
          <label class="wb-label" for="reg-password">Password</label>
          <input
            id="reg-password"
            v-model="password"
            type="password"
            class="wb-input"
            placeholder="At least 8 characters, with letters and numbers"
            maxlength="64"
            autocomplete="new-password"
          />
          <p v-if="passwordError" class="mt-1 text-xs text-red-600">{{ passwordError }}</p>
        </div>

        <div>
          <label class="wb-label" for="reg-confirm">Confirm password</label>
          <input
            id="reg-confirm"
            v-model="confirmPassword"
            type="password"
            class="wb-input"
            maxlength="64"
            autocomplete="new-password"
          />
          <p v-if="confirmError" class="mt-1 text-xs text-red-600">{{ confirmError }}</p>
        </div>

        <p v-if="formError" class="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
          {{ formError }}
        </p>

        <button type="submit" class="wb-btn-primary w-full" :disabled="submitting">
          {{ submitting ? 'Creating account...' : 'Create account' }}
        </button>

        <p class="text-center text-sm text-slate-500">
          Already have an account?
          <RouterLink :to="{ name: 'login' }" class="font-medium text-brand-700 hover:underline">
            Sign in
          </RouterLink>
        </p>
      </form>
    </div>
  </div>
</template>
