/**
 * Generates the key that makes order submission idempotent.
 *
 * It is created once when the checkout page mounts and reused for every retry of the same
 * submission, so repeatedly pressing "Place Order" cannot create a second order (PRD 3.4).
 * The server also enforces this with a unique index; the key is what lets the server recognise
 * a retry as a retry rather than as a new order.
 */
export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  // Fallback for browsers without crypto.randomUUID (non-secure contexts).
  const random = (): string => Math.floor(Math.random() * 0x10000).toString(16).padStart(4, '0')
  return `${random()}${random()}-${random()}-${random()}-${random()}-${random()}${random()}${random()}`
}
