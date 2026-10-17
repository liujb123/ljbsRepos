/**
 * Client-side input validation.
 *
 * These rules deliberately mirror the server's Bean Validation annotations (see
 * docs/BUSINESS-DESIGN.md section 10.1). The client copy exists only for instant feedback -
 * the server remains the authority and re-validates everything.
 */

export const MAX_EMAIL = 200
export const MAX_ADDRESS = 200
export const MAX_SEARCH_KEYWORD = 50
export const MAX_NOTE = 200
export const MAX_DISH_NAME = 100
export const MAX_DISH_DESCRIPTION = 500
export const MAX_OPTION_NAME = 60
export const MIN_PASSWORD = 8
export const MAX_PASSWORD = 64

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
// At least 8 characters, containing both a letter and a digit, no whitespace.
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d)\S{8,64}$/

export function isValidEmail(value: string): boolean {
  const trimmed = (value ?? '').trim()
  return trimmed.length > 0 && trimmed.length <= MAX_EMAIL && EMAIL_PATTERN.test(trimmed)
}

export function isValidPassword(value: string): boolean {
  return PASSWORD_PATTERN.test(value ?? '')
}

/** Returns the English message for the first violated rule, or null when the value is fine. */
export function validateEmail(value: string): string | null {
  const trimmed = (value ?? '').trim()
  if (!trimmed) return 'Email is required.'
  if (trimmed.length > MAX_EMAIL) return `Email must be ${MAX_EMAIL} characters or fewer.`
  if (!EMAIL_PATTERN.test(trimmed)) return 'Please enter a valid email address.'
  return null
}

export function validatePassword(value: string): string | null {
  if (!value) return 'Password is required.'
  if (!isValidPassword(value)) {
    return 'Password must be at least 8 characters and include both letters and numbers.'
  }
  return null
}

export function validateDisplayName(value: string): string | null {
  const trimmed = (value ?? '').trim()
  if (!trimmed) return 'Display name is required.'
  if (trimmed.length > 50) return 'Display name must be 50 characters or fewer.'
  return null
}

export function validateAddress(value: string): string | null {
  const trimmed = (value ?? '').trim()
  if (!trimmed) return 'Delivery address is required.'
  if (trimmed.length > MAX_ADDRESS) {
    return `Delivery address must be ${MAX_ADDRESS} characters or fewer.`
  }
  return null
}

/** Truncates a search keyword to the server-side limit so the request is never rejected. */
export function clampKeyword(value: string): string {
  return (value ?? '').slice(0, MAX_SEARCH_KEYWORD)
}
