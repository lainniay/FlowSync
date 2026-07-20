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
import OverviewView from '@/views/overview/OverviewView.vue'

vi.mock('@/views/overview/api', () => ({
  getOverview: vi.fn<typeof getOverview>(),
}))

vi.mock('@/views/projects/api', () => ({
  getProjects: vi.fn<typeof import('@/views/projects/api').getProjects>()
    .mockResolvedValue({
      items: [],
      page: 0,
      size: 100,
      totalElements: 0,
      totalPages: 0,
    }),
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
})

describe('OverviewView', () => {
  it('loads overview data successfully', async () => {
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
    vi.mocked(getOverview).mockRejectedValue(new Error('network failed'))

    const wrapper = shallowMount(OverviewView)

    await flushPromises()

    expect(
      wrapper.get('[data-testid="overview-content"]')
        .attributes('data-state'),
    ).toBe('error')
  })
})
