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

import { getProject } from '@/views/projects/api'
import ProjectDetailView from '@/views/projects/ProjectDetailView.vue'

const project = {
  id: '101',
  owner: { id: '2', displayName: '张三' },
  name: 'FlowSync',
  description: '小组任务协同系统',
  status: 'IN_PROGRESS' as const,
  priority: 'HIGH' as const,
  startDate: '2026-07-13',
  endDate: '2026-08-01',
  archivedAt: null,
  memberCount: 2,
  taskStats: { total: 2, completed: 0 },
  createdAt: '2026-07-13T08:10:00Z',
  updatedAt: '2026-07-13T09:30:00Z',
}

vi.mock('@/views/projects/api', () => ({
  archiveProject: vi.fn<typeof import('@/views/projects/api').archiveProject>(),
  createProject: vi.fn<typeof import('@/views/projects/api').createProject>(),
  deleteProject: vi.fn<typeof import('@/views/projects/api').deleteProject>(),
  getProject: vi.fn<typeof getProject>(),
  getProjects: vi.fn<typeof import('@/views/projects/api').getProjects>(),
  restoreProject: vi.fn<typeof import('@/views/projects/api').restoreProject>(),
  transferProjectOwner: vi.fn<
    typeof import('@/views/projects/api').transferProjectOwner
  >(),
  updateProject: vi.fn<typeof import('@/views/projects/api').updateProject>(),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    currentUser: {
      id: '2',
      username: 'zhangsan',
      displayName: '张三',
      phone: null,
      email: 'zhangsan@example.com',
      systemRole: 'USER',
      active: true,
      createdAt: '2026-07-13T08:01:00Z',
      updatedAt: '2026-07-13T08:01:00Z',
    },
  }),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({
    params: { projectId: '101' },
  }),
  useRouter: () => ({
    replace: vi.fn<(name: { name: string }) => Promise<void>>(),
    push: vi.fn<(path: unknown) => Promise<void>>(),
  }),
}))

beforeEach(() => {
  vi.mocked(getProject).mockReset()
})

describe('ProjectDetailView', () => {
  it('loads project detail successfully', async () => {
    vi.mocked(getProject).mockResolvedValue(project)

    const wrapper = shallowMount(ProjectDetailView)

    await flushPromises()

    expect(getProject).toHaveBeenCalledWith('101')
    expect(
      wrapper.get('[data-testid="project-detail-content"]')
        .attributes('data-state'),
    ).toBe('success')
  })

  it('shows error when project detail fails', async () => {
    vi.mocked(getProject).mockRejectedValue(new Error('network failed'))

    const wrapper = shallowMount(ProjectDetailView)

    await flushPromises()

    expect(
      wrapper.get('[data-testid="project-detail-content"]')
        .attributes('data-state'),
    ).toBe('error')
  })
})
