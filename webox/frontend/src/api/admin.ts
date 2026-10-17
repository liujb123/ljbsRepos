import { request } from './client'
import type {
  CacheStats,
  DailyMenuView,
  DishAdminView,
  DishCategory,
  DishUpsertRequest,
  PageResponse
} from '@/types/api'
import { clampKeyword } from '@/utils/validation'

export interface AdminDishQuery {
  keyword?: string
  category?: DishCategory | null
  available?: boolean | null
  page?: number
  size?: number
}

export const adminApi = {
  listDishes(query: AdminDishQuery): Promise<PageResponse<DishAdminView>> {
    const params = new URLSearchParams()
    const keyword = clampKeyword(query.keyword ?? '').trim()
    if (keyword) params.set('keyword', keyword)
    if (query.category) params.set('category', query.category)
    if (query.available !== null && query.available !== undefined) {
      params.set('available', String(query.available))
    }
    params.set('page', String(query.page ?? 0))
    params.set('size', String(query.size ?? 10))
    return request<PageResponse<DishAdminView>>({
      method: 'GET',
      url: `/admin/dishes?${params.toString()}`
    })
  },

  getDish(dishId: number): Promise<DishAdminView> {
    return request<DishAdminView>({ method: 'GET', url: `/admin/dishes/${dishId}` })
  },

  createDish(payload: DishUpsertRequest): Promise<DishAdminView> {
    return request<DishAdminView>({ method: 'POST', url: '/admin/dishes', data: payload })
  },

  updateDish(dishId: number, payload: DishUpsertRequest): Promise<DishAdminView> {
    return request<DishAdminView>({ method: 'PUT', url: `/admin/dishes/${dishId}`, data: payload })
  },

  setAvailability(dishId: number, available: boolean): Promise<DishAdminView> {
    return request<DishAdminView>({
      method: 'PATCH',
      url: `/admin/dishes/${dishId}/availability`,
      data: { available }
    })
  },

  cacheStats(): Promise<CacheStats> {
    return request<CacheStats>({ method: 'GET', url: '/admin/dishes/cache-stats' })
  },

  getDailyMenu(date: string): Promise<DailyMenuView> {
    return request<DailyMenuView>({ method: 'GET', url: `/admin/daily-menus/${date}` })
  },

  saveDailyMenu(date: string, items: { dishId: number; totalQty: number }[]): Promise<DailyMenuView> {
    return request<DailyMenuView>({
      method: 'PUT',
      url: `/admin/daily-menus/${date}`,
      data: { items }
    })
  },

  uploadImage(file: File): Promise<{ url: string }> {
    const form = new FormData()
    form.append('file', file)
    return request<{ url: string }>({
      method: 'POST',
      url: '/admin/files/images',
      data: form,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  }
}
