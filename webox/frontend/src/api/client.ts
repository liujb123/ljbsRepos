import axios, { AxiosError, type AxiosInstance, type AxiosRequestConfig } from 'axios'
import type { ApiError, ApiResponse } from '@/types/api'
import { StorageKeys, readString } from '@/utils/storage'

/**
 * Single axios instance for the whole app.
 *
 * Responsibilities: attach the bearer token, and normalize every failure into an {@link ApiError}
 * so views can render the server's English message without knowing about axios.
 */
export const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

/** Registered by the router bootstrap to avoid a circular import with the auth store. */
let unauthorizedHandler: (() => void) | null = null

export function setUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler
}

http.interceptors.request.use((config) => {
  const token = readString(StorageKeys.token)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiResponse<unknown>>) => {
    const normalized = normalizeError(error)
    if (normalized.status === 401 && unauthorizedHandler) {
      unauthorizedHandler()
    }
    return Promise.reject(normalized)
  }
)

function normalizeError(error: AxiosError<ApiResponse<unknown>>): ApiError {
  const response = error.response
  if (!response) {
    return {
      status: 0,
      code: 'NETWORK_ERROR',
      message: 'Something went wrong. Please try again.',
      data: null
    }
  }
  const body = response.data
  return {
    status: response.status,
    code: body?.code ?? 'INTERNAL_ERROR',
    message: body?.message ?? 'Something went wrong. Please try again.',
    data: (body?.data as Record<string, unknown> | undefined) ?? null
  }
}

/** Unwraps the envelope and returns `data`, so callers never repeat `.data.data`. */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiResponse<T>>(config)
  return response.data.data
}
