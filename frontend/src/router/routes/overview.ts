import type { RouteRecordRaw } from 'vue-router'

export const overviewRoutes: readonly RouteRecordRaw[] = [
  {
    path: 'overview',
    name: 'overview',
    component: () => import('@/views/overview/OverviewView.vue'),
  },
]
