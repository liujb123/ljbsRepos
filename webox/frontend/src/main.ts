import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'
import App from './App.vue'
import { router } from './router'
import { setUnauthorizedHandler } from './api/client'
import { useAuthStore } from './stores/auth'
import { usePreferenceStore } from './stores/preference'
import './style.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(VueQueryPlugin, {
  queryClientConfig: {
    defaultOptions: {
      queries: {
        // The menu changes rarely and is read by everyone at once: keep it warm briefly,
        // which is the client half of the "high-frequency menu" story (PRD 3.2).
        staleTime: 30_000,
        retry: 1,
        refetchOnWindowFocus: false
      }
    }
  }
})
app.use(router)

// A 401 from any request ends the session and returns the user to the sign-in page.
setUnauthorizedHandler(() => {
  const auth = useAuthStore(pinia)
  const preferences = usePreferenceStore(pinia)
  auth.logout()
  preferences.reset()
  if (router.currentRoute.value.name !== 'login') {
    void router.push({ name: 'login' })
  }
})

app.mount('#app')
