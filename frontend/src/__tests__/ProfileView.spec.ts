import {
  flushPromises,
  shallowMount,
} from '@vue/test-utils'
import {
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest'

import type { User } from '@/shared/api/types'
import ProfileView from '@/views/profile/ProfileView.vue'

const mockCurrentUser: User = {
  id: '2',
  username: 'zhangsan',
  displayName: '张三',
  phone: '13800000000',
  email: 'zhangsan@example.com',
  systemRole: 'USER',
  active: true,
  createdAt: '2026-07-13T08:01:00Z',
  updatedAt: '2026-07-13T08:01:00Z',
}

const authStore = {
  currentUser: mockCurrentUser as User | null,
  loading: false,
  initialized: true,
  errorMessage: '',
  loadCurrentUser: vi.fn<() => Promise<void>>(async () => {
    authStore.currentUser = mockCurrentUser
  }),
  clearSession: vi.fn<() => void>(),
}

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authStore,
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    replace: vi.fn<(name: { name: string }) => Promise<void>>(),
  }),
}))

vi.mock('@/views/profile/api', () => ({
  updateProfile: vi.fn<typeof import('@/views/profile/api').updateProfile>(),
  changePassword: vi.fn<typeof import('@/views/profile/api').changePassword>(),
}))

beforeEach(() => {
  authStore.currentUser = mockCurrentUser
  authStore.loading = false
  authStore.initialized = true
  authStore.errorMessage = ''
})

describe('ProfileView', () => {
  it('renders the profile form when the current user is available', async () => {
    const wrapper = shallowMount(ProfileView)

    await flushPromises()

    expect(
      wrapper.get('[data-testid="profile-content"]')
        .attributes('data-state'),
    ).toBe('success')
    expect(wrapper.text()).toContain('zhangsan')
    expect(wrapper.text()).toContain('修改资料')
  })

  it('shows error when the current user is missing', async () => {
    authStore.currentUser = null

    const wrapper = shallowMount(ProfileView)

    await flushPromises()

    expect(
      wrapper.get('[data-testid="profile-content"]')
        .attributes('data-state'),
    ).toBe('error')
  })
})
