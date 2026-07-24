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
  deleteProject,
  getProject,
  getProjectInvitations,
} from '@/views/projects/api'
import ProjectDetailView from '@/views/projects/ProjectDetailView.vue'
import ProjectInvitationsPanel from '@/views/projects/ProjectInvitationsPanel.vue'
import MaterialIcon from '@/components/MaterialIcon.vue'

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
    query: {},
  }),
  useRouter: () => ({
    replace: vi.fn<(name: { name: string }) => Promise<void>>(),
    push: vi.fn<(path: unknown) => Promise<void>>(),
  }),
}))

beforeEach(() => {
  vi.mocked(getProject).mockReset()
  vi.mocked(getProjectInvitations).mockReset()
  vi.mocked(deleteProject).mockReset()
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
    SummaryListView: true,
    TaskListView: true,
    ProjectFormDialog: true,
    AiTaskPlanView: true,
    teleport: true,
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

  it('places project invitations in the members interface for the owner', async () => {
    vi.mocked(getProject).mockResolvedValue(project)

    const wrapper = mountProjectDetail()

    await flushPromises()

    expect(
      wrapper.find('project-invitations-panel-stub').exists(),
    ).toBe(true)
    expect(wrapper.find('[data-testid="project-invitations-tab"]').exists())
      .toBe(false)
    expect(wrapper.getComponent({ name: 'ProjectMembersPanel' }).props('canInviteMembers'))
      .toBe(true)
  })

  it('shows task, summary, and prominent AI entries for owner', async () => {
    vi.mocked(getProject).mockResolvedValue(project)

    const wrapper = mountProjectDetail()

    await flushPromises()

    expect(wrapper.find('[data-testid="project-tasks-entry"]').exists())
      .toBe(true)
    expect(wrapper.find('[data-testid="project-summaries-entry"]').exists())
      .toBe(true)
    expect(wrapper.find('[data-testid="project-ai-plan-entry"]').exists())
      .toBe(true)
    expect(wrapper.get('[data-testid="project-ai-plan-entry"]').text())
      .toContain('AI 任务计划')
    expect(
      wrapper.get('[data-testid="project-ai-plan-entry"]')
        .findComponent(MaterialIcon).props('name'),
    ).toBe('auto_awesome')
    await wrapper.get('[data-testid="project-ai-plan-entry"]').trigger('click')
    expect((wrapper.vm as unknown as { aiPlanDialogVisible: boolean }).aiPlanDialogVisible)
      .toBe(true)
    expect(wrapper.text()).not.toContain('将在后续接入')
  })

  it('shows project metrics, progress, description, and information in overview', async () => {
    const endDate = new Date()
    endDate.setDate(endDate.getDate() + 4)
    vi.mocked(getProject).mockResolvedValue({
      ...project,
      memberCount: 1,
      taskStats: { total: 8, completed: 3 },
      endDate: [
        endDate.getFullYear(),
        String(endDate.getMonth() + 1).padStart(2, '0'),
        String(endDate.getDate()).padStart(2, '0'),
      ].join('-'),
    })

    const wrapper = mountProjectDetail()
    await flushPromises()

    expect(wrapper.get('[data-testid="overview-member-count"]').text()).toBe('1')
    expect(wrapper.get('[data-testid="overview-task-count"]').text()).toBe('8')
    expect(wrapper.get('[data-testid="overview-completed-count"]').text()).toBe('3')
    expect(wrapper.get('[data-testid="overview-remaining-time"]').text()).toBe('4 天')
    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('38')
    expect(wrapper.text()).toContain('项目说明')
    expect(wrapper.text()).toContain('项目信息')
  })

  it('refreshes overview metrics when returning from another project tab', async () => {
    vi.mocked(getProject).mockResolvedValue(project)
    const wrapper = mountProjectDetail()
    await flushPromises()

    const vm = wrapper.vm as unknown as { activeTab: string }
    vm.activeTab = 'members'
    await wrapper.vm.$nextTick()
    vm.activeTab = 'overview'
    await flushPromises()

    expect(getProject).toHaveBeenCalledTimes(2)
  })

  it('hides AI plan entry when the project is archived', async () => {
    vi.mocked(getProject).mockResolvedValue({
      ...project,
      archivedAt: '2026-07-23T00:00:00Z',
    })

    const wrapper = mountProjectDetail()

    await flushPromises()

    expect(wrapper.find('[data-testid="project-tasks-entry"]').exists())
      .toBe(true)
    expect(wrapper.find('[data-testid="project-summaries-entry"]').exists())
      .toBe(true)
    expect(wrapper.find('[data-testid="project-ai-plan-entry"]').exists())
      .toBe(false)
  })

  it('requires the exact project name before permanent deletion', async () => {
    vi.mocked(getProject).mockResolvedValue({
      ...project,
      archivedAt: '2026-07-23T00:00:00Z',
    })
    vi.mocked(deleteProject).mockResolvedValue()

    const wrapper = mountProjectDetail()
    await flushPromises()

    await wrapper.findAll('button')
      .find((button) => button.text().includes('永久删除'))!
      .trigger('click')

    const confirmation = wrapper.get('#project-delete-confirmation')
    const deleteButton = () => {
      const buttons = wrapper.findAll('button')
        .filter((button) => button.text().includes('永久删除'))
      return buttons[buttons.length - 1]!
    }

    expect(deleteButton().attributes('disabled')).toBeDefined()

    await confirmation.setValue('wrong')
    await deleteButton().trigger('click')
    expect(deleteProject).not.toHaveBeenCalled()

    await confirmation.setValue('FlowSync')
    expect(deleteButton().attributes('disabled')).toBeUndefined()
    await deleteButton().trigger('click')
    await flushPromises()

    expect(deleteProject).toHaveBeenCalledExactlyOnceWith('101')
  })

  it('hides project invitations for regular members', async () => {
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

  it('shows invitations in the members interface for admin viewers', async () => {
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
    expect(wrapper.find('[data-testid="project-ai-plan-entry"]').exists())
      .toBe(false)
  })
})
