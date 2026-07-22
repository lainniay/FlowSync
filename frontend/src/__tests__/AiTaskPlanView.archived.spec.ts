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

import { getProject } from '@/views/projects/api'
import AiTaskPlanView from '@/views/ai/AiTaskPlanView.vue'

vi.mock('@/views/projects/api', () => ({
  getProject: vi.fn<typeof getProject>(),
}))

vi.mock('@/views/ai/api', () => ({
  generateTaskPlan: vi.fn<() => Promise<unknown>>(),
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
    push: vi.fn<() => Promise<void>>(),
  }),
}))

beforeEach(() => {
  vi.mocked(getProject).mockReset()
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
      global: { plugins: [createPinia()] },
    })

    await flushPromises()

    const alert = wrapper.find('el-alert-stub')
    expect(alert.exists()).toBe(true)
    expect(alert.attributes('title')).toContain('项目已归档')
    expect(wrapper.text()).not.toContain('生成初步计划')
  })
})
