import {
  flushPromises,
  mount,
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
import {
  getTask,
  getTaskLogs,
  getTasks,
} from '@/views/tasks/api'
import TaskDetailView from '@/views/tasks/TaskDetailView.vue'

const authState = vi.hoisted(() => ({
  currentUser: {
    id: '2',
    username: 'zhangsan',
    displayName: '张三',
    systemRole: 'USER' as 'USER' | 'ADMIN',
  },
}))

const routeState = vi.hoisted(() => ({
  params: { taskId: '501' },
}))

vi.mock('@/views/tasks/api', () => ({
  createTaskLog: vi.fn<() => Promise<unknown>>(),
  deleteTask: vi.fn<() => Promise<unknown>>(),
  deleteTaskLog: vi.fn<() => Promise<unknown>>(),
  getTask: vi.fn<typeof getTask>(),
  getTaskLogs: vi.fn<typeof getTaskLogs>(),
  getTasks: vi.fn<typeof getTasks>(),
  updateTask: vi.fn<() => Promise<unknown>>(),
  updateTaskStatus: vi.fn<() => Promise<unknown>>(),
}))

vi.mock('@/views/projects/api', () => ({
  getProject: vi.fn<typeof getProject>(),
}))

vi.mock('@/views/ai/api', () => ({
  getTaskSuggestion: vi.fn<() => Promise<unknown>>(),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authState,
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({
    push: vi.fn<() => Promise<void>>(),
  }),
}))

const task = {
  id: '501',
  projectId: '103',
  parentId: null,
  assignee: { id: '2', displayName: '张三' },
  creator: { id: '2', displayName: '张三' },
  title: '归档项目中的任务',
  description: null,
  status: 'IN_PROGRESS' as const,
  priority: 'MEDIUM' as const,
  progressPercent: 20,
  dueDate: null,
  createdAt: '2026-07-13T09:00:00Z',
  updatedAt: '2026-07-13T10:00:00Z',
}

beforeEach(() => {
  vi.mocked(getTask).mockReset()
  vi.mocked(getTaskLogs).mockReset()
  vi.mocked(getTasks).mockReset()
  vi.mocked(getProject).mockReset()
  authState.currentUser = {
    id: '2',
    username: 'zhangsan',
    displayName: '张三',
    systemRole: 'USER',
  }
  routeState.params = { taskId: '501' }
})

describe('TaskDetailView archived project write gates', () => {
  it('hides write actions when the project is archived', async () => {
    vi.mocked(getTask).mockResolvedValue(task)
    vi.mocked(getTaskLogs).mockResolvedValue({
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })
    vi.mocked(getTasks).mockResolvedValue({
      items: [],
      page: 0,
      size: 50,
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
      taskStats: { total: 1, completed: 0 },
      createdAt: '2026-07-13T08:10:00Z',
      updatedAt: '2026-07-20T08:00:00Z',
    })

    const wrapper = mount(TaskDetailView, {
      global: {
        plugins: [createPinia()],
        stubs: {
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    })

    await flushPromises()

    expect(wrapper.text()).not.toContain('编辑')
    expect(wrapper.text()).not.toContain('删除任务')
    expect(wrapper.text()).not.toContain('新增日志')
    expect(wrapper.text()).not.toContain('获取 AI 建议')
  })
})
