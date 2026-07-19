import type { RouteRecordRaw } from 'vue-router'

export const projectRoutes: readonly RouteRecordRaw[] = [
  {
    path: 'projects',
    name: 'projects',
    component: () => import(
      '@/views/projects/ProjectListView.vue'
    ),
  },
]