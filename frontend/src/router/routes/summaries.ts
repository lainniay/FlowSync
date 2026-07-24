import type { RouteRecordRaw } from 'vue-router'

export const summaryRoutes: readonly RouteRecordRaw[] = [
  {
    path: 'summaries/:summaryId',
    name: 'summary-detail',
    component: () => import('@/views/summaries/SummaryDetailView.vue'),
    props: true,
  },
]
