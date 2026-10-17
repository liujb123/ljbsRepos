import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api/auth'
import type { LoginRequest, RegisterRequest, UserResponse } from '@/types/api'
import { StorageKeys, readString, remove, writeString } from '@/utils/storage'

/**
 * Session state.
 *
 * The token lives in localStorage so a page refresh keeps the employee signed in (PRD 3.1).
 * On cold start {@link restore} calls `/auth/me`; a network failure keeps the token rather than
 * silently signing the user out, while a 401 clears it.
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(readString(StorageKeys.token))
  const user = ref<UserResponse | null>(null)
  const restored = ref(false)

  const isAuthenticated = computed(() => Boolean(token.value && user.value))
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const displayName = computed(() => user.value?.displayName ?? '')

  function setSession(newToken: string, newUser: UserResponse): void {
    token.value = newToken
    user.value = newUser
    writeString(StorageKeys.token, newToken)
  }

  function clear(): void {
    token.value = null
    user.value = null
    remove(StorageKeys.token)
  }

  async function restore(): Promise<void> {
    if (restored.value) return
    if (!token.value) {
      restored.value = true
      return
    }
    try {
      user.value = await authApi.me()
    } catch (error) {
      const status = (error as { status?: number }).status
      // Only an authentication failure invalidates the session; a transient error does not.
      if (status === 401 || status === 403) {
        clear()
      }
    } finally {
      restored.value = true
    }
  }

  async function login(payload: LoginRequest): Promise<UserResponse> {
    const response = await authApi.login(payload)
    setSession(response.token, response.user)
    restored.value = true
    return response.user
  }

  async function register(payload: RegisterRequest): Promise<UserResponse> {
    const response = await authApi.register(payload)
    setSession(response.token, response.user)
    restored.value = true
    return response.user
  }

  function logout(): void {
    clear()
  }

  return {
    token,
    user,
    restored,
    isAuthenticated,
    isAdmin,
    displayName,
    restore,
    login,
    register,
    logout
  }
})
