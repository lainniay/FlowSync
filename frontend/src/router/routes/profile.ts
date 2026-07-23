import type { RouteRecordRaw } from 'vue-router'

export const profileRoutes: readonly RouteRecordRaw[] = [
  {
    path: 'profile',
    name: 'profile',
    component: () => import('@/views/profile/ProfileView.vue'),
  },
]
