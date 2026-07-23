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

import { getUsers } from '@/views/admin/api'
import UserListView from '@/views/admin/UserListView.vue'

vi.mock('@/views/admin/api', () => ({
  createUser: vi.fn<typeof import('@/views/admin/api').createUser>(),
  getUsers: vi.fn<typeof getUsers>(),
  resetUserPassword: vi.fn<typeof import('@/views/admin/api').resetUserPassword>(),
  updateUser: vi.fn<typeof import('@/views/admin/api').updateUser>(),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()

  return {
    ...actual,
    ElMessage: {
      success: vi.fn<(message: string) => void>(),
      error: vi.fn<(message: string) => void>(),
    },
    ElMessageBox: {
      confirm: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
    },
  }
})

const user = {
  id: '2',
  username: 'zhangsan',
  displayName: '张三',
  phone: '13800000000',
  email: 'zhangsan@example.com',
  systemRole: 'USER' as const,
  active: true,
  createdAt: '2026-07-13T08:01:00Z',
  updatedAt: '2026-07-13T08:01:00Z',
}

beforeEach(() => {
  vi.mocked(getUsers).mockReset()
})

describe('UserListView', () => {
  it('caps the detail drawer at the viewport width', async () => {
    vi.mocked(getUsers).mockResolvedValue({
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })

    const wrapper = shallowMount(UserListView)
    await flushPromises()

    expect(wrapper.get('el-drawer-stub').attributes('size'))
      .toBe('min(400px, 100%)')
  })

  it('loads the first active-user page', async () => {
    vi.mocked(getUsers).mockResolvedValue({
      items: [user],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })

    const wrapper = shallowMount(UserListView)

    await flushPromises()

    expect(getUsers).toHaveBeenCalledWith({
      q: undefined,
      systemRole: undefined,
      active: true,
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
    })

    expect(
      wrapper.get('[data-testid="user-content"]')
        .attributes('data-state'),
    ).toBe('success')
    expect(wrapper.text()).not.toContain('用户 ID')
    expect(wrapper.get('el-pagination-stub').attributes('layout'))
      .toBe('total, prev, pager, next')
    expect(wrapper.get('el-pagination-stub').attributes('page-sizes'))
      .toBeUndefined()
  })

  it('shows empty after a successful empty response', async () => {
    vi.mocked(getUsers).mockResolvedValue({
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })

    const wrapper = shallowMount(UserListView)

    await flushPromises()

    expect(
      wrapper.get('[data-testid="user-content"]')
        .attributes('data-state'),
    ).toBe('empty')
  })

  it('shows error when the request fails', async () => {
    vi.mocked(getUsers).mockRejectedValue(
      new Error('network failed'),
    )

    const wrapper = shallowMount(UserListView)

    await flushPromises()

    expect(
      wrapper.get('[data-testid="user-content"]')
        .attributes('data-state'),
    ).toBe('error')
  })
})
