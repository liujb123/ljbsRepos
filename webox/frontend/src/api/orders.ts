import { request } from './client'
import type {
  CheckoutPreviewRequest,
  CheckoutPreviewResponse,
  OrderResponse,
  OrderSummaryResponse,
  PageResponse,
  PlaceOrderRequest
} from '@/types/api'

export const orderApi = {
  /** Prices and validates the cart before the employee commits to anything. */
  preview(payload: CheckoutPreviewRequest): Promise<CheckoutPreviewResponse> {
    return request<CheckoutPreviewResponse>({
      method: 'POST',
      url: '/orders/checkout-preview',
      data: payload
    })
  },

  /**
   * Submits an order.
   *
   * `idempotencyKey` is generated once when the checkout page mounts and reused for every
   * retry, so repeatedly pressing the button can only ever create one order (PRD 3.4).
   */
  place(idempotencyKey: string, payload: PlaceOrderRequest): Promise<OrderResponse> {
    return request<OrderResponse>({
      method: 'POST',
      url: '/orders',
      data: payload,
      headers: { 'Idempotency-Key': idempotencyKey }
    })
  },

  list(page = 0, size = 10): Promise<PageResponse<OrderSummaryResponse>> {
    return request<PageResponse<OrderSummaryResponse>>({
      method: 'GET',
      url: `/orders?page=${page}&size=${size}`
    })
  },

  detail(orderId: number): Promise<OrderResponse> {
    return request<OrderResponse>({ method: 'GET', url: `/orders/${orderId}` })
  },

  cancel(orderId: number): Promise<OrderResponse> {
    return request<OrderResponse>({ method: 'POST', url: `/orders/${orderId}/cancel` })
  }
}
