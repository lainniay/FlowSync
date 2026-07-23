import {
  flushPromises,
  shallowMount,
} from '@vue/test-utils'
import { createPinia } from 'pinia'
import {
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest'

import { generateTaskPlan } from '@/views/ai/api'
import { getProject, getProjectMembers } from '@/views/projects/api'
import AiTaskPlanView from '@/views/ai/AiTaskPlanView.vue'

const routerPush = vi.hoisted(() => vi.fn<(location: unknown) => void>())

vi.mock('@/views/projects/api', () => ({
  getProject: vi.fn<typeof getProject>(),
  getProjectMembers: vi.fn<typeof getProjectMembers>(),
}))

vi.mock('@/views/ai/api', () => ({
  generateTaskPlan: vi.fn<typeof generateTaskPlan>(),
  importTaskPlan: vi.fn<() => Promise<unknown>>(),
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

vi.mock('vue-router', () => ({
  useRoute: () => ({
    params: { projectId: '103' },
  }),
  useRouter: () => ({
    push: routerPush,
  }),
}))

beforeEach(() => {
  vi.mocked(getProject).mockReset()
  vi.mocked(getProjectMembers).mockReset()
  vi.mocked(generateTaskPlan).mockReset()
  vi.mocked(getProjectMembers).mockResolvedValue([])
  routerPush.mockReset()
})

describe('AiTaskPlanView archived project gate', () => {
  it('blocks AI plan form when the project is archived', async () => {
    vi.mocked(getProject).mockResolvedValue({
      id: '103',
      owner: { id: '2', displayName: '张三' },
      name: 'Archived Project',
      description: null,
      status: 'COMPLETED',
      priority: 'LOW',
      startDate: null,
      endDate: null,
      archivedAt: '2026-07-20T08:00:00Z',
      memberCount: 1,
      taskStats: { total: 0, completed: 0 },
      createdAt: '2026-07-13T08:10:00Z',
      updatedAt: '2026-07-20T08:00:00Z',
    })

    const wrapper = shallowMount(AiTaskPlanView, {
      global: {
        plugins: [createPinia()],
        stubs: { RouterLink: true },
      },
    })

    await flushPromises()

    const alert = wrapper.find('el-alert-stub')
    expect(alert.exists()).toBe(true)
    expect(alert.attributes('title')).toContain('项目已归档')
    expect(wrapper.text()).not.toContain('生成初步计划')

    await wrapper.get('[data-testid="back-to-project"]').trigger('click')

    expect(routerPush).toHaveBeenCalledWith({
      name: 'project-detail',
      params: { projectId: '103' },
    })
  })

  it('opens dialog mode by generating immediately from project defaults', async () => {
    vi.mocked(getProject).mockResolvedValue({
      id: '103',
      owner: { id: '2', displayName: '张三' },
      name: 'Active Project',
      description: '交付第一阶段功能',
      status: 'IN_PROGRESS',
      priority: 'HIGH',
      startDate: '2026-07-01',
      endDate: '2026-08-31',
      archivedAt: null,
      memberCount: 1,
      taskStats: { total: 0, completed: 0 },
      createdAt: '2026-07-01T00:00:00Z',
      updatedAt: '2026-07-20T00:00:00Z',
    })
    let resolvePlan!: (value: Awaited<ReturnType<typeof generateTaskPlan>>) => void
    vi.mocked(generateTaskPlan).mockReturnValue(new Promise((resolve) => {
      resolvePlan = resolve
    }))

    const wrapper = shallowMount(AiTaskPlanView, {
      props: { projectId: '103', dialogMode: true, autoGenerate: true },
      global: {
        plugins: [createPinia()],
        stubs: { RouterLink: true },
      },
    })
    await flushPromises()

    expect(generateTaskPlan).toHaveBeenCalledWith('103', {
      goal: 'Active Project',
      description: '交付第一阶段功能',
      constraints: { maxItems: 10, targetEndDate: '2026-08-31' },
    })
    expect(wrapper.find('el-skeleton-stub').exists()).toBe(true)

    resolvePlan({ overview: '计划', items: [], generatedAt: '2026-07-23T00:00:00Z' })
    await flushPromises()
    expect(wrapper.find('el-table-stub').exists()).toBe(true)
  })
})
