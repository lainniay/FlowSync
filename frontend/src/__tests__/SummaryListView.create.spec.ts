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
import { getSummaries } from '@/views/summaries/api'
import SummaryListView from '@/views/summaries/SummaryListView.vue'

const routeState = vi.hoisted(() => ({
  query: {} as Record<string, unknown>,
}))

vi.mock('@/views/summaries/api', () => ({
  createSummary: vi.fn<() => Promise<unknown>>(),
  getSummaries: vi.fn<typeof getSummaries>(),
}))

vi.mock('@/views/projects/api', () => ({
  getProject: vi.fn<typeof getProject>(),
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
  useRoute: () => routeState,
  useRouter: () => ({
    push: vi.fn<(location: unknown) => void>(),
  }),
}))

beforeEach(() => {
  routeState.query = {}

  vi.mocked(getProject).mockReset()
  vi.mocked(getProject).mockResolvedValue({
    id: '101',
    owner: { id: '2', displayName: '张三' },
    name: 'FlowSync',
    description: null,
    status: 'IN_PROGRESS',
    priority: 'MEDIUM',
    startDate: null,
    endDate: null,
    archivedAt: null,
    memberCount: 2,
    taskStats: { total: 0, completed: 0 },
    createdAt: '2026-07-13T08:10:00Z',
    updatedAt: '2026-07-13T08:10:00Z',
  })
  vi.mocked(getSummaries).mockReset()
  vi.mocked(getSummaries).mockResolvedValue({
    items: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  })
})

describe('SummaryListView create dialog defaults', () => {
  it('defaults create form projectId to empty without route context', async () => {
    const wrapper = shallowMount(SummaryListView, {
      global: {
        plugins: [createPinia()],
        stubs: { RouterLink: true },
      },
    })

    await flushPromises()

    const vm = wrapper.vm as unknown as {
      openCreateDialog: () => void
      createForm: { projectId: string }
    }

    vm.openCreateDialog()
    await flushPromises()

    expect(vm.createForm.projectId).toBe('')
  })

  it('prefills create form projectId from explicit route context', async () => {
    routeState.query = { projectId: '101' }

    const wrapper = shallowMount(SummaryListView, {
      global: {
        plugins: [createPinia()],
        stubs: { RouterLink: true },
      },
    })

    await flushPromises()

    const vm = wrapper.vm as unknown as {
      openCreateDialog: () => void
      createForm: { projectId: string }
    }

    vm.openCreateDialog()
    await flushPromises()

    expect(getProject).toHaveBeenCalledWith('101')
    expect(vm.createForm.projectId).toBe('101')

    expect(wrapper.find('[data-testid="back-to-project"]').exists()).toBe(false)
  })

  it('hides create action for an archived route project', async () => {
    routeState.query = { projectId: '101' }
    vi.mocked(getProject).mockResolvedValue({
      id: '101',
      owner: { id: '2', displayName: '张三' },
      name: 'Archived Project',
      description: null,
      status: 'COMPLETED',
      priority: 'MEDIUM',
      startDate: null,
      endDate: null,
      archivedAt: '2026-07-23T00:00:00Z',
      memberCount: 2,
      taskStats: { total: 1, completed: 0 },
      createdAt: '2026-07-13T08:10:00Z',
      updatedAt: '2026-07-23T00:00:00Z',
    })

    const wrapper = shallowMount(SummaryListView, {
      global: {
        plugins: [createPinia()],
        stubs: { RouterLink: true },
      },
    })

    await flushPromises()

    expect(getProject).toHaveBeenCalledWith('101')
    expect(wrapper.text()).not.toContain('创建总结')
  })
})
