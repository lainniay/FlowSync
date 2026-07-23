import type { RouteRecordRaw } from 'vue-router'

export const taskRoutes: readonly RouteRecordRaw[] = [
  {
    path: 'tasks',
    name: 'tasks',
    component: () => import('@/views/tasks/TaskListView.vue'),
  },
  {
    path: 'tasks/:taskId',
    name: 'task-detail',
    component: () => import('@/views/tasks/TaskDetailView.vue'),
    props: true,
  },
]
