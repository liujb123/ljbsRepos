import { ref } from 'vue'

/**
 * Single-flight guard for a button action.
 *
 * During the ordering peak employees click "Add to Cart" repeatedly (PRD 3.3). While an action
 * is in flight this rejects further invocations, so a burst of clicks produces one update and
 * one visual transition instead of jitter.
 */
export function usePendingAction(cooldownMs = 400) {
  const pending = ref(false)

  async function run<T>(action: () => T | Promise<T>): Promise<T | undefined> {
    if (pending.value) {
      return undefined
    }
    pending.value = true
    try {
      return await action()
    } finally {
      window.setTimeout(() => {
        pending.value = false
      }, cooldownMs)
    }
  }

  return { pending, run }
}
