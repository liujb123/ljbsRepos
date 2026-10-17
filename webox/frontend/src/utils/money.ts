/**
 * Money handling.
 *
 * Every amount is carried as an integer number of cents inside the SPA. The API delivers
 * decimal strings ("22.50"), which are parsed once at the boundary; no arithmetic ever happens
 * on a float, so surcharges and totals cannot drift (PRD section 6).
 */

const CURRENCY_SYMBOL = '\u00A5'

/** Parses "22.50" / "22.5" / 22.5 into 2250 cents. Returns 0 for null/undefined. */
export function parseCents(value: string | number | null | undefined): number {
  if (value === null || value === undefined || value === '') {
    return 0
  }
  const text = typeof value === 'number' ? value.toFixed(2) : String(value).trim()
  const negative = text.startsWith('-')
  const digits = negative ? text.slice(1) : text
  const [integerPart = '0', fractionPart = ''] = digits.split('.')
  const fraction = (fractionPart + '00').slice(0, 2)
  const cents = Number(integerPart || '0') * 100 + Number(fraction || '0')
  if (!Number.isFinite(cents)) {
    return 0
  }
  return negative ? -cents : cents
}

/** Renders cents as "\u00A522.50" - always two decimal places. */
export function formatCny(cents: number): string {
  const negative = cents < 0
  const absolute = Math.abs(Math.round(cents))
  const yuan = Math.floor(absolute / 100)
  const fen = absolute % 100
  return `${negative ? '-' : ''}${CURRENCY_SYMBOL}${yuan}.${String(fen).padStart(2, '0')}`
}

/** Converts cents back to the decimal string the API expects. */
export function centsToAmount(cents: number): string {
  const negative = cents < 0
  const absolute = Math.abs(Math.round(cents))
  const yuan = Math.floor(absolute / 100)
  const fen = absolute % 100
  return `${negative ? '-' : ''}${yuan}.${String(fen).padStart(2, '0')}`
}

/** Sums a list of cent values. */
export function sumCents(values: number[]): number {
  return values.reduce((total, value) => total + value, 0)
}
