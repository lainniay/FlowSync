import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getPublicUserProfile } from '@/views/profile/api'
import UserProfileView from '@/views/profile/UserProfileView.vue'

const routerReplace = vi.hoisted(() => (
  vi.fn<(location: { name: string }) => Promise<void>>().mockResolvedValue()
))

vi.mock('@/views/profile/api', () => ({
  getPublicUserProfile: vi.fn<typeof getPublicUserProfile>(),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ currentUser: { id: '2' } }),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: routerReplace }),
}))

beforeEach(() => {
  vi.mocked(getPublicUserProfile).mockReset()
  routerReplace.mockReset()
})

describe('UserProfileView', () => {
  it('shows another users contact profile without displaying their user id', async () => {
    vi.mocked(getPublicUserProfile).mockResolvedValue({
      username: 'devseed-user-0001',
      displayName: '测试用户 001',
      phone: '13900000001',
      email: 'devseed-user-0001@example.test',
      systemRole: 'USER',
      active: true,
    })

    const wrapper = shallowMount(UserProfileView, {
      props: { userId: '3' },
    })
    await flushPromises()

    expect(getPublicUserProfile).toHaveBeenCalledWith('3')
    expect(wrapper.get('[data-testid="public-profile-content"]').attributes('data-state'))
      .toBe('success')
    expect(wrapper.text()).toContain('测试用户 001')
    expect(wrapper.text()).toContain('13900000001')
    expect(wrapper.text()).toContain('devseed-user-0001@example.test')
    expect(wrapper.text()).not.toContain('用户 ID')
  })

  it('redirects to personal center when opening the current users public route', async () => {
    const wrapper = shallowMount(UserProfileView, {
      props: { userId: '2' },
    })
    await flushPromises()

    expect(routerReplace).toHaveBeenCalledWith({ name: 'profile' })
    expect(getPublicUserProfile).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
