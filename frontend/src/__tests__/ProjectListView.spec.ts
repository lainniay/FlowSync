import {
  flushPromises,
  shallowMount,
} from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getProjects } from '@/views/projects/api'
import ProjectListView from '@/views/projects/ProjectListView.vue'

vi.mock('@/views/projects/api', () => ({
  createProject: vi.fn<typeof import('@/views/projects/api').createProject>(),
  getProjects: vi.fn<typeof getProjects>(),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    currentUser: {
      id: '2',
      username: 'zhangsan',
      displayName: '张三',
      systemRole: 'USER',
      active: true,
    },
  }),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: vi.fn<(path: unknown) => Promise<void>>(),
  }),
}))

vi.mock('@/views/admin/api', () => ({
  getUsers: vi.fn<typeof import('@/views/admin/api').getUsers>(),
}))

const project = {
  id: '101',
  owner: {
    id: '2',
    displayName: '张三',
  },
  name: 'FlowSync',
  description: '小组任务协同系统',
  status: 'IN_PROGRESS' as const,
  priority: 'HIGH' as const,
  startDate: '2026-07-13',
  endDate: '2026-08-01',
  archivedAt: null,
  memberCount: 2,
  taskStats: {
    total: 2,
    completed: 0,
  },
  createdAt: '2026-07-13T08:10:00Z',
  updatedAt: '2026-07-13T09:30:00Z',
}

beforeEach(() => {
  vi.mocked(getProjects).mockReset()
})

describe('ProjectListView', () => {
  it('loads the first active-project page', async () => {
    vi.mocked(getProjects).mockResolvedValue({
      items: [project],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })

    const wrapper = shallowMount(ProjectListView)

    await flushPromises()

    expect(getProjects).toHaveBeenCalledWith({
      q: undefined,
      status: undefined,
      ownerId: undefined,
      archived: false,
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
    })

    expect(
      wrapper.get('[data-testid="project-content"]')
        .attributes('data-state'),
    ).toBe('success')
  })

  it('shows empty after a successful empty response', async () => {
    vi.mocked(getProjects).mockResolvedValue({
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })

    const wrapper = shallowMount(ProjectListView)

    await flushPromises()

    expect(
      wrapper.get('[data-testid="project-content"]')
        .attributes('data-state'),
    ).toBe('empty')
  })

  it('shows error when the request fails', async () => {
    vi.mocked(getProjects).mockRejectedValue(
      new Error('network failed'),
    )

    const wrapper = shallowMount(ProjectListView)

    await flushPromises()

    expect(
      wrapper.get('[data-testid="project-content"]')
        .attributes('data-state'),
    ).toBe('error')
  })
})