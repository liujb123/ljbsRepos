import { request } from './client'
import type { AuthResponse, LoginRequest, RegisterRequest, UserResponse } from '@/types/api'

export const authApi = {
  register(payload: RegisterRequest): Promise<AuthResponse> {
    return request<AuthResponse>({ method: 'POST', url: '/auth/register', data: payload })
  },

  login(payload: LoginRequest): Promise<AuthResponse> {
    return request<AuthResponse>({ method: 'POST', url: '/auth/login', data: payload })
  },

  /** Used on cold start so a page refresh does not lose the session (PRD 3.1). */
  me(): Promise<UserResponse> {
    return request<UserResponse>({ method: 'GET', url: '/auth/me' })
  }
}
