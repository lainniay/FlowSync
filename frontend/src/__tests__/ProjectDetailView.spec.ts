import {
  flushPromises,
  mount,
  shallowMount,
} from '@vue/test-utils'
import {
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest'

import {
  getProject,
  getProjectInvitations,
} from '@/views/projects/api'
import ProjectDetailView from '@/views/projects/ProjectDetailView.vue'
import ProjectInvitationsPanel from '@/views/projects/ProjectInvitationsPanel.vue'

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

const authState = vi.hoisted(() => ({
  currentUser: {
    id: '2',
    username: 'zhangsan',
    displayName: '张三',
    phone: null as string | null,
    email: 'zhangsan@example.com' as string | null,
    systemRole: 'USER' as 'USER' | 'ADMIN',
    active: true,
    createdAt: '2026-07-13T08:01:00Z',
    updatedAt: '2026-07-13T08:01:00Z',
  },
}))

vi.mock('@/views/projects/api', () => ({
  archiveProject: vi.fn<typeof import('@/views/projects/api').archiveProject>(),
  createProject: vi.fn<typeof import('@/views/projects/api').createProject>(),
  deleteProject: vi.fn<typeof import('@/views/projects/api').deleteProject>(),
  getProject: vi.fn<typeof getProject>(),
  getProjectMembers: vi.fn<
    typeof import('@/views/projects/api').getProjectMembers
  >().mockResolvedValue([]),
  getProjectInvitations: vi.fn<typeof getProjectInvitations>(),
  getProjects: vi.fn<typeof import('@/views/projects/api').getProjects>(),
  restoreProject: vi.fn<typeof import('@/views/projects/api').restoreProject>(),
  transferProjectOwner: vi.fn<
    typeof import('@/views/projects/api').transferProjectOwner
  >(),
  updateProject: vi.fn<typeof import('@/views/projects/api').updateProject>(),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authState,
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
  vi.mocked(getProjectInvitations).mockReset()
  vi.mocked(getProjectInvitations).mockResolvedValue([])
  authState.currentUser = {
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
})

function mountProjectDetail(
  options?: { renderInvitationsPanel?: boolean },
) {
  const stubs: Record<string, boolean | { template: string }> = {
    RouterLink: {
      template: '<a><slot /></a>',
    },
    ProjectMembersPanel: true,
    ProjectFormDialog: true,
  }

  if (options?.renderInvitationsPanel !== true) {
    stubs.ProjectInvitationsPanel = true
  }

  return mount(ProjectDetailView, {
    global: { stubs },
  })
}

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

  it('shows invitations tab for project owner', async () => {
    vi.mocked(getProject).mockResolvedValue(project)

    const wrapper = mountProjectDetail()

    await flushPromises()

    expect(
      wrapper.find('project-invitations-panel-stub').exists(),
    ).toBe(true)
  })

  it('hides invitations tab for regular members', async () => {
    authState.currentUser = {
      ...authState.currentUser,
      id: '3',
      username: 'lisi',
      displayName: '李四',
    }

    vi.mocked(getProject).mockResolvedValue(project)

    const wrapper = mountProjectDetail({ renderInvitationsPanel: true })

    await flushPromises()

    expect(wrapper.findComponent(ProjectInvitationsPanel).exists())
      .toBe(false)
    expect(getProjectInvitations).not.toHaveBeenCalled()
  })

  it('shows invitations tab for admin viewers', async () => {
    authState.currentUser = {
      ...authState.currentUser,
      id: '1',
      username: 'admin',
      displayName: '管理员',
      systemRole: 'ADMIN',
    }

    vi.mocked(getProject).mockResolvedValue(project)

    const wrapper = mountProjectDetail()

    await flushPromises()

    expect(
      wrapper.find('project-invitations-panel-stub').exists(),
    ).toBe(true)
  })
})
