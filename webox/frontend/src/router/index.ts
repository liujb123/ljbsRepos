import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/**
 * Route tree.
 *
 * The employee app and the Console are separate layout components with separate navigation,
 * as required by PRD 4.3. `requiresAdmin` is defence in depth only - the real boundary is
 * `hasRole('ADMIN')` on `/api/admin/**` in the backend.
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { public: true }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('@/layouts/EmployeeLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', name: 'menu', component: () => import('@/views/menu/MenuView.vue') },
      {
        path: 'menu/dishes/:id',
        name: 'dish-detail',
        component: () => import('@/views/menu/DishDetailView.vue'),
        props: true
      },
      { path: 'cart', name: 'cart', component: () => import('@/views/cart/CartView.vue') },
      {
        path: 'checkout',
        name: 'checkout',
        component: () => import('@/views/checkout/CheckoutView.vue')
      },
      {
        path: 'order-success/:id',
        name: 'order-success',
        component: () => import('@/views/checkout/OrderSuccessView.vue'),
        props: true
      },
      { path: 'orders', name: 'orders', component: () => import('@/views/orders/OrderListView.vue') },
      {
        path: 'orders/:id',
        name: 'order-detail',
        component: () => import('@/views/orders/OrderDetailView.vue'),
        props: true
      },
      {
        path: 'settings',
        name: 'preferences',
        component: () => import('@/views/settings/PreferencesView.vue')
      }
    ]
  },
  {
    path: '/console',
    component: () => import('@/layouts/ConsoleLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      { path: '', redirect: { name: 'console-dishes' } },
      {
        path: 'dishes',
        name: 'console-dishes',
        component: () => import('@/views/console/ConsoleDishListView.vue')
      },
      {
        path: 'dishes/new',
        name: 'console-dish-new',
        component: () => import('@/views/console/ConsoleDishFormView.vue')
      },
      {
        path: 'dishes/:id/edit',
        name: 'console-dish-edit',
        component: () => import('@/views/console/ConsoleDishFormView.vue'),
        props: true
      },
      {
        path: 'daily-menu',
        name: 'console-daily-menu',
        component: () => import('@/views/console/ConsoleDailyMenuView.vue')
      }
    ]
  },
  {
    path: '/403',
    name: 'forbidden',
    component: () => import('@/views/ForbiddenView.vue'),
    meta: { public: true }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { public: true }
  }
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  // Rehydrate the session once per page load before deciding anything.
  await auth.restore()

  if (to.meta.requiresAdmin && !auth.isAdmin) {
    return { name: 'forbidden' }
  }
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if ((to.name === 'login' || to.name === 'register') && auth.isAuthenticated) {
    return { name: auth.isAdmin ? 'console-dishes' : 'menu' }
  }
  return true
})
