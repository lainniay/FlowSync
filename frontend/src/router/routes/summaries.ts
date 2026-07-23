import type { RouteRecordRaw } from 'vue-router'

export const summaryRoutes: readonly RouteRecordRaw[] = [
  {
    path: 'summaries',
    name: 'summaries',
    component: () => import('@/views/summaries/SummaryListView.vue'),
  },
  {
    path: 'summaries/:summaryId',
    name: 'summary-detail',
    component: () => import('@/views/summaries/SummaryDetailView.vue'),
    props: true,
  },
]
