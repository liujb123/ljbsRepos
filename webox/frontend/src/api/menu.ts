import { request } from './client'
import type {
  DishDetailView,
  MenuItemView,
  MenuQuery,
  PageResponse
} from '@/types/api'
import { clampKeyword } from '@/utils/validation'

export const menuApi = {
  /**
   * Builds the query string by hand so several cuisines can be sent as a repeated
   * `categories` parameter (?categories=Chinese&categories=Japanese).
   */
  list(query: MenuQuery): Promise<PageResponse<MenuItemView>> {
    const params = new URLSearchParams()
    if (query.date) params.set('date', query.date)
    for (const category of query.categories ?? []) {
      params.append('categories', category)
    }
    const keyword = clampKeyword(query.keyword ?? '').trim()
    if (keyword) params.set('keyword', keyword)
    params.set('page', String(query.page ?? 0))
    params.set('size', String(query.size ?? 12))
    if (query.prefer) params.set('prefer', 'true')

    return request<PageResponse<MenuItemView>>({ method: 'GET', url: `/menu?${params.toString()}` })
  },

  /** Dates that have a published menu, for the date pickers. */
  dates(): Promise<string[]> {
    return request<string[]>({ method: 'GET', url: '/menu/dates' })
  },

  detail(dishId: number, date?: string): Promise<DishDetailView> {
    const suffix = date ? `?date=${encodeURIComponent(date)}` : ''
    return request<DishDetailView>({ method: 'GET', url: `/menu/dishes/${dishId}${suffix}` })
  }
}
