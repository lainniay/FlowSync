import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './shared/api/http'
import { useAuthStore } from './stores/auth'
import { useThemeStore } from './stores/theme'
import './assets/main.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import { resolveInitialTheme, applyThemeToDocument } from './shared/theme'

applyThemeToDocument(resolveInitialTheme())

if (
  import.meta.env.DEV
  && import.meta.env.VITE_ENABLE_MOCK === 'true'
) {
  const { worker } = await import('./mocks/browser')

  await worker.start({
    onUnhandledRequest: 'bypass',
  })
}

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)

useThemeStore(pinia).initialize()

let redirectingToLogin = false

setUnauthorizedHandler(() => {
  const authStore = useAuthStore(pinia)
  const redirect = router.currentRoute.value.fullPath

  authStore.clearSession()

  if (
    router.currentRoute.value.name === 'login'
    || redirectingToLogin
  ) {
    return
  }

  redirectingToLogin = true

  void router.replace({
    name: 'login',
    query: {
      redirect,
    },
  }).finally(() => {
    redirectingToLogin = false
  })
})

app.use(router)

app.mount('#app')