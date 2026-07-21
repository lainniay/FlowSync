import type { RouteRecordRaw } from 'vue-router'

export const aiRoutes: readonly RouteRecordRaw[] = [
  {
    path: 'projects/:projectId/ai-plan',
    name: 'ai-task-plan',
    component: () => import('@/views/ai/AiTaskPlanView.vue'),
    props: true,
  },
]
