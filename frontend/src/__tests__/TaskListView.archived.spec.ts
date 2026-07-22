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
import { getTasks } from '@/views/tasks/api'
import TaskListView from '@/views/tasks/TaskListView.vue'

const authState = vi.hoisted(() => ({
  currentUser: {
    id: '2',
    username: 'zhangsan',
    displayName: '张三',
    systemRole: 'USER' as 'USER' | 'ADMIN',
  },
}))

vi.mock('@/views/tasks/api', () => ({
  createTask: vi.fn<() => Promise<unknown>>(),
  getTasks: vi.fn<typeof getTasks>(),
}))

vi.mock('@/views/projects/api', () => ({
  getProject: vi.fn<typeof getProject>(),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authState,
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({
    query: { projectId: '103' },
  }),
  useRouter: () => ({
    push: vi.fn<() => Promise<void>>(),
  }),
}))

beforeEach(() => {
  vi.mocked(getTasks).mockReset()
  vi.mocked(getProject).mockReset()
  authState.currentUser = {
    id: '2',
    username: 'zhangsan',
    displayName: '张三',
    systemRole: 'USER',
  }
})

describe('TaskListView archived project create permission', () => {
  it('hides create button when the filtered project is archived', async () => {
    vi.mocked(getTasks).mockResolvedValue({
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })
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

    const wrapper = shallowMount(TaskListView, {
      global: { plugins: [createPinia()] },
    })

    await flushPromises()

    expect(getProject).toHaveBeenCalledWith('103')
    expect(wrapper.text()).not.toContain('创建任务')
  })
})
