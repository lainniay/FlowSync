import { flushPromises, shallowMount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getOverview } from '@/views/overview/api'
import OverviewView from '@/views/overview/OverviewView.vue'
import AdminOverviewDashboard from '@/views/overview/AdminOverviewDashboard.vue'
import { getProjects } from '@/views/projects/api'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/views/overview/api', () => ({
  getOverview: vi.fn<typeof getOverview>(),
}))

vi.mock('@/views/projects/api', () => ({
  getProjects: vi.fn<typeof getProjects>(),
}))

const overview = {
  counts: {
    projects: 1,
    inProgressProjects: 1,
    tasks: 2,
    completedTasks: 0,
    overdueTasks: 0,
    blockedTasks: 0,
    dueSoonTasks: 1,
    myOverdueTasks: 0,
    myBlockedTasks: 0,
    myTodayDueTasks: 0,
    staleBlockedTasks: 0,
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
  projectHealth: [{
    id: '101',
    name: 'FlowSync',
    isOwner: true,
    status: 'IN_PROGRESS' as const,
    endDate: '2026-08-31',
    tasks: 2,
    completedTasks: 0,
    overdueTasks: 0,
    blockedTasks: 0,
  }],
  recentActivities: [{
    type: 'TASK_CREATED',
    resourceId: '501',
    summary: '创建任务「接口设计」',
    occurredAt: new Date().toISOString(),
  }],
}

const RouterLinkStub = {
  props: ['to'],
  template: '<a><slot /></a>',
}

beforeEach(() => {
  vi.mocked(getOverview).mockReset()
  vi.mocked(getProjects).mockReset()
  vi.mocked(getProjects).mockResolvedValue({
    items: [], page: 0, size: 100, totalElements: 0, totalPages: 0,
  })
})

describe('OverviewView', () => {
  it('loads dashboard metrics, health, todos, and activities', async () => {
    vi.mocked(getOverview).mockResolvedValue(overview)

    const wrapper = shallowMount(OverviewView, {
      global: {
        plugins: [createPinia()],
        stubs: {
          RouterLink: RouterLinkStub,
          ProjectLink: { template: '<a><slot /></a>' },
          DashboardPanel: {
            props: ['title'],
            template: '<section><h2>{{ title }}</h2><slot /></section>',
          },
          DashboardStatCard: {
            props: ['label', 'value', 'detail'],
            template: '<article class="dashboard-stat-card">{{ label }} {{ value }} {{ detail }}</article>',
          },
          StatusDistribution: true,
        },
      },
    })
    await flushPromises()

    expect(getOverview).toHaveBeenCalledWith()
    expect(
      wrapper.get('[data-testid="overview-content"]')
        .attributes('data-state'),
    ).toBe('success')
    expect(wrapper.get('el-select-stub').attributes('placeholder')).toBe('全部项目')
    expect(wrapper.text()).not.toContain('刷新')
    expect(wrapper.findAll('.dashboard-stat-card')).toHaveLength(5)
    expect(wrapper.findAll('.todo-list > a')).toHaveLength(3)
    expect(wrapper.text()).toContain('任务执行情况')
    expect(wrapper.text()).toContain('FlowSync')
    expect(wrapper.text()).toContain('Owner')
    expect(wrapper.text()).toContain('创建任务「接口设计」')
    const vm = wrapper.vm as unknown as {
      selectedProjectId: string
      loadOverview: () => Promise<void>
      taskLocation: (filters: Record<string, string>) => unknown
    }
    expect(vm.taskLocation({ status: 'BLOCKED' })).toEqual({
      name: 'tasks',
      query: { status: 'BLOCKED' },
    })
    vm.selectedProjectId = '101'
    await vm.loadOverview()
    expect(getOverview).toHaveBeenLastCalledWith({ projectId: '101' })
    expect(vm.taskLocation({ status: 'BLOCKED' })).toEqual({
      name: 'tasks',
      query: { projectId: '101', status: 'BLOCKED' },
    })
  })

  it('shows an error and allows retrying the overview request', async () => {
    vi.mocked(getOverview)
      .mockRejectedValueOnce(new Error('network failed'))
      .mockResolvedValueOnce(overview)

    const wrapper = shallowMount(OverviewView, {
      global: {
        plugins: [createPinia()],
        stubs: { RouterLink: RouterLinkStub },
      },
    })
    await flushPromises()

    expect(
      wrapper.get('[data-testid="overview-content"]')
        .attributes('data-state'),
    ).toBe('error')

    await wrapper.get('el-button-stub').trigger('click')
    await flushPromises()

    expect(getOverview).toHaveBeenCalledTimes(2)
    expect(
      wrapper.get('[data-testid="overview-content"]')
        .attributes('data-state'),
    ).toBe('success')
  })

  it('renders the dedicated admin dashboard without loading personal metrics', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().currentUser = {
      id: '1',
      username: 'admin',
      displayName: '管理员',
      phone: null,
      email: null,
      systemRole: 'ADMIN',
      active: true,
      createdAt: '2026-07-01T00:00:00Z',
      updatedAt: '2026-07-01T00:00:00Z',
    }

    const wrapper = shallowMount(OverviewView, {
      global: {
        plugins: [pinia],
        stubs: { RouterLink: RouterLinkStub },
      },
    })
    await flushPromises()

    expect(getOverview).not.toHaveBeenCalled()
    expect(getProjects).not.toHaveBeenCalled()
    expect(wrapper.findComponent(AdminOverviewDashboard).exists()).toBe(true)
    expect(wrapper.find('.stat-grid').exists()).toBe(false)
  })
})
