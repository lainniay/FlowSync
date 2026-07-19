import {
  createPinia,
  setActivePinia,
} from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'

import router from '@/router'
import { useAuthStore } from '@/stores/auth'
import type { User } from '@/shared/api/types'

const adminUser: User = {
  id: '1',
  username: 'admin',
  displayName: '系统管理员',
  phone: null,
  email: 'admin@flowsync.local',
  systemRole: 'ADMIN',
  active: true,
  createdAt: '2026-07-13T08:00:00Z',
  updatedAt: '2026-07-13T08:00:00Z',
}

const regularUser: User = {
  id: '2',
  username: 'zhangsan',
  displayName: '张三',
  phone: null,
  email: 'zhangsan@example.com',
  systemRole: 'USER',
  active: true,
  createdAt: '2026-07-13T08:01:00Z',
  updatedAt: '2026-07-13T08:01:00Z',
}

beforeEach(() => {
  setActivePinia(createPinia())
})

describe('router authentication guard', () => {
  it('redirects an unauthenticated user to login', async () => {
    const authStore = useAuthStore()
    authStore.clearSession()

    await router.push('/overview?source=test')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect)
      .toBe('/overview?source=test')
  })

  it('redirects USER away from an ADMIN route', async () => {
    const authStore = useAuthStore()
    authStore.currentUser = regularUser
    authStore.initialized = true

    const removeRoute = router.addRoute({
      path: '/test/admin-only',
      name: 'test-admin-only-user',
      component: {
        template: '<div>ADMIN only</div>',
      },
      meta: {
        requiresAuth: true,
        roles: ['ADMIN'],
      },
    })

    await router.push('/test/admin-only')

    expect(router.currentRoute.value.name).toBe('forbidden')

    removeRoute()
  })

  it('allows ADMIN to enter an ADMIN route', async () => {
    const authStore = useAuthStore()
    authStore.currentUser = adminUser
    authStore.initialized = true

    const removeRoute = router.addRoute({
      path: '/test/admin-allowed',
      name: 'test-admin-only-admin',
      component: {
        template: '<div>ADMIN allowed</div>',
      },
      meta: {
        requiresAuth: true,
        roles: ['ADMIN'],
      },
    })

    await router.push('/test/admin-allowed')

    expect(router.currentRoute.value.name)
      .toBe('test-admin-only-admin')

    removeRoute()
  })
})