import type { RouteRecordRaw } from 'vue-router'

export const profileRoutes: readonly RouteRecordRaw[] = [
  {
    path: 'profile',
    name: 'profile',
    component: () => import('@/views/profile/ProfileView.vue'),
  },
  {
    path: 'users/:userId/profile',
    name: 'user-profile',
    component: () => import('@/views/profile/UserProfileView.vue'),
    props: true,
  },
]
