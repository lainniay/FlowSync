import type { RouteRecordRaw } from 'vue-router'

export const adminRoutes: readonly RouteRecordRaw[] = [
  {
    path: 'admin/users',
    name: 'admin-users',
    component: () => import('@/views/admin/UserListView.vue'),
    meta: {
      roles: ['ADMIN'],
    },
  },
]
