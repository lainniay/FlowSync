import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import './assets/main.css'

if (import.meta.env.DEV && import.meta.env.VITE_ENABLE_MOCK === 'true') {
  const { worker } = await import('./mocks/browser')
  await worker.start({
    onUnhandledRequest: 'bypass',
  })
}

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
