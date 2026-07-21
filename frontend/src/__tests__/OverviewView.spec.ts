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

import { getOverview } from '@/views/overview/api'
import { getProjects } from '@/views/projects/api'
import OverviewView from '@/views/overview/OverviewView.vue'

vi.mock('@/views/overview/api', () => ({
  getOverview: vi.fn<typeof getOverview>(),
}))

vi.mock('@/views/projects/api', () => ({
  getProjects: vi.fn<typeof getProjects>(),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    currentUser: {
      id: '2',
      displayName: '张三',
      systemRole: 'USER',
    },
  }),
}))

const overview = {
  counts: {
    projects: 1,
    tasks: 2,
    completedTasks: 0,
    overdueTasks: 0,
    summaries: 0,
    members: 2,
  },
  tasksByStatus: [
    { status: 'NOT_STARTED' as const, count: 1 },
    { status: 'IN_PROGRESS' as const, count: 1 },
    { status: 'BLOCKED' as const, count: 0 },
    { status: 'COMPLETED' as const, count: 0 },
    { status: 'CANCELLED' as const, count: 0 },
  ],
  recentActivities: [],
}

beforeEach(() => {
  vi.mocked(getOverview).mockReset()
  vi.mocked(getProjects).mockReset()
})

describe('OverviewView', () => {
  it('loads overview data successfully', async () => {
    vi.mocked(getProjects).mockResolvedValue({
      items: [],
      page: 0,
      size: 100,
      totalElements: 0,
      totalPages: 0,
    })
    vi.mocked(getOverview).mockResolvedValue(overview)

    const wrapper = shallowMount(OverviewView)

    await flushPromises()

    expect(getOverview).toHaveBeenCalledWith({ projectId: undefined })
    expect(
      wrapper.get('[data-testid="overview-content"]')
        .attributes('data-state'),
    ).toBe('success')
  })

  it('shows error when overview request fails', async () => {
    vi.mocked(getProjects).mockResolvedValue({
      items: [],
      page: 0,
      size: 100,
      totalElements: 0,
      totalPages: 0,
    })
    vi.mocked(getOverview).mockRejectedValue(new Error('network failed'))

    const wrapper = shallowMount(OverviewView)

    await flushPromises()

    expect(
      wrapper.get('[data-testid="overview-content"]')
        .attributes('data-state'),
    ).toBe('error')
  })

  it('still loads overview when project options fail', async () => {
    vi.mocked(getProjects).mockRejectedValue(new Error('options failed'))
    vi.mocked(getOverview).mockResolvedValue(overview)

    const wrapper = shallowMount(OverviewView)

    await flushPromises()

    expect(getOverview).toHaveBeenCalledWith({ projectId: undefined })
    expect(
      wrapper.get('[data-testid="overview-content"]')
        .attributes('data-state'),
    ).toBe('success')

    const alert = wrapper.find('el-alert-stub')
    expect(alert.exists()).toBe(true)
    expect(alert.attributes('title')).toContain('项目筛选项加载失败')
  })

  it('loads all project option pages', async () => {
    vi.mocked(getProjects)
      .mockResolvedValueOnce({
        items: Array.from({ length: 100 }, (_, index) => ({
          id: String(index + 1),
          owner: { id: '2', displayName: '张三' },
          name: `项目 ${index + 1}`,
          description: null,
          status: 'IN_PROGRESS' as const,
          priority: 'MEDIUM' as const,
          startDate: null,
          endDate: null,
          archivedAt: null,
          memberCount: 1,
          taskStats: { total: 0, completed: 0 },
          createdAt: '2026-07-13T08:10:00Z',
          updatedAt: '2026-07-13T09:30:00Z',
        })),
        page: 0,
        size: 100,
        totalElements: 120,
        totalPages: 2,
      })
      .mockResolvedValueOnce({
        items: Array.from({ length: 20 }, (_, index) => ({
          id: String(index + 101),
          owner: { id: '2', displayName: '张三' },
          name: `项目 ${index + 101}`,
          description: null,
          status: 'IN_PROGRESS' as const,
          priority: 'MEDIUM' as const,
          startDate: null,
          endDate: null,
          archivedAt: null,
          memberCount: 1,
          taskStats: { total: 0, completed: 0 },
          createdAt: '2026-07-13T08:10:00Z',
          updatedAt: '2026-07-13T09:30:00Z',
        })),
        page: 1,
        size: 100,
        totalElements: 120,
        totalPages: 2,
      })
    vi.mocked(getOverview).mockResolvedValue(overview)

    shallowMount(OverviewView)

    await flushPromises()

    expect(getProjects).toHaveBeenCalledTimes(2)
    expect(getProjects).toHaveBeenNthCalledWith(1, {
      archived: false,
      sort: 'name,asc',
      page: 0,
      size: 100,
    })
    expect(getProjects).toHaveBeenNthCalledWith(2, {
      archived: false,
      sort: 'name,asc',
      page: 1,
      size: 100,
    })
  })
})
