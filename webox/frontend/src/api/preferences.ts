import { request } from './client'
import type { AddressResponse, PreferencesRequest, PreferencesResponse } from '@/types/api'

export const preferenceApi = {
  get(): Promise<PreferencesResponse> {
    return request<PreferencesResponse>({ method: 'GET', url: '/users/me/preferences' })
  },

  save(payload: PreferencesRequest): Promise<PreferencesResponse> {
    return request<PreferencesResponse>({ method: 'PUT', url: '/users/me/preferences', data: payload })
  },

  addresses(): Promise<AddressResponse[]> {
    return request<AddressResponse[]>({ method: 'GET', url: '/users/me/addresses' })
  }
}
