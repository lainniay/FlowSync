import type { RouteRecordRaw } from 'vue-router'

export const invitationRoutes: readonly RouteRecordRaw[] = [
  {
    path: 'invitations',
    name: 'invitations',
    component: () => import('@/views/invitations/InvitationListView.vue'),
    meta: {
      roles: ['USER'],
    },
  },
]
