import { onScopeDispose, ref, watch, type Ref } from 'vue'

/**
 * Debounces a reactive value.
 *
 * Used by the menu search box so typing does not fire a request per keystroke during the
 * 09:30-10:00 peak (PRD 3.2).
 */
export function useDebouncedRef<T>(source: Ref<T>, delayMs = 300): Ref<T> {
  const debounced = ref(source.value) as Ref<T>
  let handle: number | undefined

  watch(source, (value) => {
    if (handle !== undefined) {
      window.clearTimeout(handle)
    }
    handle = window.setTimeout(() => {
      debounced.value = value
    }, delayMs)
  })

  onScopeDispose(() => {
    if (handle !== undefined) {
      window.clearTimeout(handle)
    }
  })

  return debounced
}
