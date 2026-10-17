/** Namespaced, failure-tolerant access to localStorage. */

const PREFIX = 'webox.'

export const StorageKeys = {
  token: `${PREFIX}token`,
  cart: `${PREFIX}cart`,
  recommend: `${PREFIX}recommend`
} as const

export function readString(key: string): string | null {
  try {
    return window.localStorage.getItem(key)
  } catch {
    return null
  }
}

export function writeString(key: string, value: string): void {
  try {
    window.localStorage.setItem(key, value)
  } catch {
    // Private browsing or a full quota: the app still works, it just will not persist.
  }
}

export function remove(key: string): void {
  try {
    window.localStorage.removeItem(key)
  } catch {
    // ignored
  }
}

export function readJson<T>(key: string, fallback: T): T {
  const raw = readString(key)
  if (!raw) return fallback
  try {
    return JSON.parse(raw) as T
  } catch {
    return fallback
  }
}

export function writeJson(key: string, value: unknown): void {
  try {
    writeString(key, JSON.stringify(value))
  } catch {
    // ignored
  }
}
