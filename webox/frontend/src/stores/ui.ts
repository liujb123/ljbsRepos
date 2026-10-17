import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface Toast {
  id: number
  message: string
  tone: 'success' | 'error' | 'info'
}

/** Transient feedback. Every message is English, supplied by the caller or the API. */
export const useUiStore = defineStore('ui', () => {
  const toasts = ref<Toast[]>([])
  let nextId = 1

  function push(message: string, tone: Toast['tone'] = 'info', timeoutMs = 3200): void {
    const id = nextId++
    toasts.value.push({ id, message, tone })
    window.setTimeout(() => dismiss(id), timeoutMs)
  }

  function success(message: string): void {
    push(message, 'success')
  }

  function error(message: string): void {
    push(message, 'error', 5000)
  }

  function info(message: string): void {
    push(message, 'info')
  }

  function dismiss(id: number): void {
    toasts.value = toasts.value.filter((toast) => toast.id !== id)
  }

  return { toasts, push, success, error, info, dismiss }
})
