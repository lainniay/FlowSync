import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getProject } from '@/views/projects/api'
import { getSummary } from '@/views/summaries/api'
import SummaryDetailView from '@/views/summaries/SummaryDetailView.vue'

const authState = vi.hoisted(() => ({
  currentUser: {
    id: '2',
    username: 'zhangsan',
    displayName: '张三',
    systemRole: 'USER' as 'USER' | 'ADMIN',
  },
}))

vi.mock('@/views/summaries/api', () => ({
  deleteSummary: vi.fn<() => Promise<void>>(),
  getSummary: vi.fn<typeof getSummary>(),
  updateSummary: vi.fn<() => Promise<unknown>>(),
}))

vi.mock('@/views/projects/api', () => ({
  getProject: vi.fn<typeof getProject>(),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authState,
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({
    params: { summaryId: '901' },
  }),
  useRouter: () => ({
    push: vi.fn<() => Promise<void>>(),
  }),
}))

const summary = {
  id: '901',
  projectId: '103',
  taskId: null,
  createdBy: { id: '2', displayName: '张三' },
  type: 'STAGE' as const,
  content: '阶段工作已经完成。',
  createdAt: '2026-07-13T09:00:00Z',
  updatedAt: '2026-07-13T10:00:00Z',
}

const archivedProject = {
  id: '103',
  owner: { id: '2', displayName: '张三' },
  name: 'Archived Project',
  description: null,
  status: 'COMPLETED' as const,
  priority: 'LOW' as const,
  startDate: null,
  endDate: null,
  archivedAt: '2026-07-20T08:00:00Z',
  memberCount: 1,
  taskStats: { total: 1, completed: 0 },
  createdAt: '2026-07-13T08:10:00Z',
  updatedAt: '2026-07-20T08:00:00Z',
}

function mountView() {
  return mount(SummaryDetailView, {
    global: {
      plugins: [createPinia()],
      stubs: {
        RouterLink: { template: '<a><slot /></a>' },
      },
    },
  })
}

beforeEach(() => {
  vi.mocked(getSummary).mockReset()
  vi.mocked(getProject).mockReset()
})

describe('SummaryDetailView archived project write gates', () => {
  it('hides edit and delete actions when the project is archived', async () => {
    vi.mocked(getSummary).mockResolvedValue(summary)
    vi.mocked(getProject).mockResolvedValue(archivedProject)

    const wrapper = mountView()

    await flushPromises()

    expect(getProject).toHaveBeenCalledWith('103')
    expect(wrapper.find('.header-actions').exists()).toBe(false)
  })

  it('hides edit and delete actions when project context fails', async () => {
    vi.mocked(getSummary).mockResolvedValue(summary)
    vi.mocked(getProject).mockRejectedValue(
      new Error('project context failed'),
    )

    const wrapper = mountView()

    await flushPromises()

    expect(getProject).toHaveBeenCalledWith('103')
    expect(wrapper.find('.header-actions').exists()).toBe(false)
  })
})
