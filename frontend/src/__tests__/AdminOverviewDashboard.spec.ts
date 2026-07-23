import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getAdminOverview } from '@/views/overview/api'
import AdminOverviewDashboard from '@/views/overview/AdminOverviewDashboard.vue'

vi.mock('@/views/overview/api', () => ({
  getAdminOverview: vi.fn<typeof getAdminOverview>(),
}))

beforeEach(() => {
  vi.mocked(getAdminOverview).mockReset()
})

describe('AdminOverviewDashboard', () => {
  it('shows system metrics, status charts, focus projects, and activities', async () => {
    vi.mocked(getAdminOverview).mockResolvedValue({
      counts: {
        activeUsers: 124,
        inactiveUsers: 8,
        users: 129,
        admins: 3,
        projects: 32,
        inProgressProjects: 18,
        tasks: 860,
        completedTasks: 430,
        overdueTasks: 47,
        overdueProjects: 12,
      },
      projectsByStatus: [
        { status: 'NOT_STARTED', count: 5 },
        { status: 'IN_PROGRESS', count: 18 },
        { status: 'COMPLETED', count: 9 },
      ],
      tasksByStatus: [
        { status: 'NOT_STARTED', count: 140 },
        { status: 'IN_PROGRESS', count: 190 },
        { status: 'BLOCKED', count: 53 },
        { status: 'COMPLETED', count: 430 },
        { status: 'CANCELLED', count: 47 },
      ],
      focusProjects: [{
        id: '101',
        name: '重点项目',
        ownerId: '2',
        ownerName: '张三',
        endDate: '2026-08-31',
        tasks: 10,
        completedTasks: 5,
        overdueTasks: 2,
        blockedTasks: 1,
      }],
      recentActivities: [{
        type: 'TASK_CREATED',
        resourceId: '501',
        summary: '创建任务「接口设计」',
        occurredAt: new Date().toISOString(),
      }],
    })

    const wrapper = mount(AdminOverviewDashboard, {
      global: {
        stubs: {
          RouterLink: { template: '<a><slot /></a>' },
          ProjectLink: { template: '<a><slot /></a>' },
          UserLink: { template: '<a><slot /></a>' },
        },
      },
    })
    await flushPromises()

    expect(getAdminOverview).toHaveBeenCalledOnce()
    expect(wrapper.findAll('.dashboard-stat-card')).toHaveLength(4)
    expect(wrapper.text()).toContain('124')
    expect(wrapper.text()).toContain('任务完成率 430 / 860')
    expect(wrapper.text()).toContain('50%')
    expect((wrapper.vm as unknown as { projectPieStyle: { background: string } })
      .projectPieStyle.background).toContain('conic-gradient')
    expect(wrapper.findAll('.task-count-list > div')).toHaveLength(5)
    expect(wrapper.text()).toContain('创建任务「接口设计」')
  })

  it('shows an error state when system metrics cannot be loaded', async () => {
    vi.mocked(getAdminOverview).mockRejectedValue(new Error('offline'))

    const wrapper = mount(AdminOverviewDashboard, {
      global: { stubs: { RouterLink: true } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('管理员工作台加载失败')
    expect(wrapper.text()).toContain('重新加载')
    expect(wrapper.find('.admin-stat-grid').exists()).toBe(false)
  })
})
