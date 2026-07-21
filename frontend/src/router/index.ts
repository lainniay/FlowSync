import {
  createRouter,
  createWebHistory,
} from 'vue-router'

import AppLayout from '@/layouts/AppLayout.vue'
import {
  getSafeRedirect,
  hasRequiredRole,
} from '@/router/navigation'
import type { SystemRole } from '@/shared/api/types'
import { useAuthStore } from '@/stores/auth'

import { aiRoutes } from './routes/ai'
import { projectRoutes } from './routes/projects'
import { summaryRoutes } from './routes/summaries'
import { taskRoutes } from './routes/tasks'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: {
        guestOnly: true,
      },
    },
    {
      path: '/',
      component: AppLayout,
      meta: {
        requiresAuth: true,
      },
      children: [
        {
          path: '',
          redirect: {
            name: 'overview',
          },
        },
        {
          path: 'overview',
          name: 'overview',
          component: () => import('@/views/HomeView.vue'),
        },
        ...projectRoutes,
        ...taskRoutes,
        ...summaryRoutes,
        ...aiRoutes,
        {
          path: '403',
          name: 'forbidden',
          component: () => import('@/views/ForbiddenView.vue'),
        },
        {
          path: ':pathMatch(.*)*',
          name: 'not-found',
          component: () => import('@/views/NotFoundView.vue'),
        },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()

  await authStore.initialize()

  if (to.meta.guestOnly && authStore.currentUser) {
    return getSafeRedirect(to.query.redirect)
  }

  if (to.meta.requiresAuth && !authStore.currentUser) {
    return {
      name: 'login',
      query: {
        redirect: to.fullPath,
      },
    }
  }

  const allowedRoles = to.meta.roles as
    | readonly SystemRole[]
    | undefined

  if (
    authStore.currentUser
    && !hasRequiredRole(
      authStore.currentUser.systemRole,
      allowedRoles,
    )
  ) {
    return {
      name: 'forbidden',
    }
  }

  return true
})

export default router