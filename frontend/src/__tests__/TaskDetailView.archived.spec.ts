import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import {
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest'

import MaterialIcon from '@/components/MaterialIcon.vue'
import { getTaskSuggestion } from '@/views/ai/api'
import { getProject } from '@/views/projects/api'
import {
  createTaskLog,
  getTask,
  getTaskLogs,
  getTasks,
  updateTaskStatus,
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
  vi.mocked(createTaskLog).mockReset()
  vi.mocked(updateTaskStatus).mockReset()
  vi.mocked(getTaskSuggestion).mockReset()
  authState.currentUser = {
    id: '2',
    username: 'zhangsan',
    displayName: '张三',
    systemRole: 'USER',
  }
  routeState.params = { taskId: '501' }
})

describe('TaskDetailView', () => {
  it('shows the task overview and progress records as a timeline', async () => {
    vi.mocked(getTask).mockResolvedValue({
      ...task,
      description: '完成任务详情界面重构',
      dueDate: '2026-07-30',
      progressPercent: 75,
    })
    vi.mocked(getTaskLogs).mockResolvedValue({
      items: [
        {
          id: '802',
          taskId: '501',
          operator: { id: '2', displayName: '测试用户 001' },
          progressPercent: 75,
          content: '第 3 次进度更新，当前完成度 75%',
          createdAt: '2026-07-23T07:54:00Z',
        },
        {
          id: '801',
          taskId: '501',
          operator: { id: '72', displayName: '测试用户 072' },
          progressPercent: 50,
          content: '第 2 次进度更新，当前完成度 50%',
          createdAt: '2026-07-22T07:54:00Z',
        },
      ],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
    })
    vi.mocked(getTasks).mockResolvedValue({
      items: [], page: 0, size: 100, totalElements: 0, totalPages: 0,
    })
    vi.mocked(getProject).mockResolvedValue({
      id: '103',
      owner: { id: '2', displayName: '张三' },
      name: 'Active Project',
      description: null,
      status: 'IN_PROGRESS',
      priority: 'MEDIUM',
      startDate: null,
      endDate: null,
      archivedAt: null,
      memberCount: 1,
      taskStats: { total: 1, completed: 0 },
      createdAt: '2026-07-13T08:10:00Z',
      updatedAt: '2026-07-20T08:00:00Z',
    })

    const wrapper = mount(TaskDetailView, {
      global: {
        plugins: [createPinia()],
        stubs: { RouterLink: { template: '<a><slot /></a>' } },
      },
    })
    await flushPromises()

    expect(wrapper.get('.latest-progress').text()).toContain('75%')
    expect(wrapper.get('.latest-progress').text())
      .toContain('第 3 次进度更新，当前完成度 75%')
    expect(wrapper.get('.task-information').text()).toContain('原截止日期')
    expect(wrapper.get('.task-information').text()).toContain('2026-07-30')
    expect(wrapper.get('.task-meta').text()).toContain('所属项目：Active Project')
    expect(wrapper.get('.task-meta').text()).toContain('负责人：张三')
    expect(wrapper.findAll('.progress-timeline-item')).toHaveLength(2)
    expect(wrapper.get('.progress-timeline-item').attributes('style'))
      .toContain('--timeline-color:')
    expect(wrapper.get('.progress-timeline').text()).toContain('测试用户 001')
    expect(wrapper.get('.progress-timeline').text()).toContain('删除记录')
    const addLogButton = wrapper.findAll('button')
      .find((button) => button.text().includes('新增日志'))
    expect(addLogButton).toBeDefined()
    expect(addLogButton!.classes()).not.toContain('el-button--small')
    expect(wrapper.find('.page-footer').exists()).toBe(false)
  })

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
      size: 100,
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
    expect(wrapper.text()).not.toContain('生成 AI 建议')
  })

  it('shows a retryable error when loading logs fails', async () => {
    vi.mocked(getTask).mockResolvedValue(task)
    vi.mocked(getTaskLogs)
      .mockRejectedValueOnce(new Error('logs failed'))
      .mockResolvedValueOnce({
        items: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      })
    vi.mocked(getTasks).mockResolvedValue({
      items: [],
      page: 0,
      size: 100,
      totalElements: 0,
      totalPages: 0,
    })
    vi.mocked(getProject).mockResolvedValue({
      id: '103',
      owner: { id: '2', displayName: '张三' },
      name: 'Active Project',
      description: null,
      status: 'IN_PROGRESS',
      priority: 'MEDIUM',
      startDate: null,
      endDate: null,
      archivedAt: null,
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

    expect(wrapper.text()).toContain('进度记录加载失败')
    expect(wrapper.text()).toContain('重新加载')
    expect(wrapper.text()).not.toContain('暂无进度记录')

    const retryButton = wrapper
      .findAll('button')
      .find((button) => button.text() === '重新加载')

    expect(retryButton).toBeDefined()

    await retryButton!.trigger('click')
    await flushPromises()

    expect(getTaskLogs).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).not.toContain('进度记录加载失败')
  })

  it('does not submit an AI suggestion longer than 1000 characters', async () => {
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
      size: 100,
      totalElements: 0,
      totalPages: 0,
    })
    vi.mocked(getProject).mockResolvedValue({
      id: '103',
      owner: { id: '2', displayName: '张三' },
      name: 'Active Project',
      description: null,
      status: 'IN_PROGRESS',
      priority: 'MEDIUM',
      startDate: null,
      endDate: null,
      archivedAt: null,
      memberCount: 1,
      taskStats: { total: 1, completed: 0 },
      createdAt: '2026-07-13T08:10:00Z',
      updatedAt: '2026-07-20T08:00:00Z',
    })
    let resolveSuggestion!: (value: Awaited<ReturnType<typeof getTaskSuggestion>>) => void
    vi.mocked(getTaskSuggestion).mockReturnValue(new Promise((resolve) => {
      resolveSuggestion = resolve
    }))

    const wrapper = mount(TaskDetailView, {
      global: {
        plugins: [createPinia()],
        stubs: {
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    })

    await flushPromises()

    const generateButton = wrapper.get('[data-testid="task-ai-suggestion-action"]')
    expect(generateButton.text()).toContain('生成 AI 建议')
    expect(generateButton.findComponent(MaterialIcon).props('name')).toBe('auto_awesome')
    expect(generateButton.element.closest('.header-actions')).not.toBeNull()

    await generateButton.trigger('click')
    const aiVm = wrapper.vm as unknown as {
      aiDialogVisible: boolean
      aiGenerating: boolean
    }
    expect(aiVm.aiDialogVisible).toBe(true)
    expect(aiVm.aiGenerating).toBe(true)
    expect(wrapper.find('.el-skeleton').exists()).toBe(true)

    resolveSuggestion({
      suggestion: 'x'.repeat(1001),
      generatedAt: '2026-07-22T12:00:00Z',
    })
    await flushPromises()

    const suggestionInput = wrapper.find('.ai-form textarea')
    expect(suggestionInput.exists()).toBe(true)
    expect(
      (suggestionInput.element as HTMLTextAreaElement).value,
    ).toHaveLength(1001)

    const submitButton = wrapper
      .findAll('button')
      .find((button) => button.text() === '提交为进度记录')

    expect(submitButton).toBeDefined()

    await submitButton!.trigger('click')
    await flushPromises()

    expect(createTaskLog).not.toHaveBeenCalled()
  })

  it('hides write actions when project context cannot be loaded', async () => {
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
      size: 100,
      totalElements: 0,
      totalPages: 0,
    })
    vi.mocked(getProject).mockRejectedValue(
      new Error('project context failed'),
    )

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
    expect(wrapper.text()).not.toContain('修改状态')
    expect(wrapper.text()).not.toContain('删除任务')
    expect(wrapper.text()).not.toContain('新增日志')
    expect(wrapper.text()).not.toContain('生成 AI 建议')
  })

  it('loads every page of child tasks', async () => {
    const child = {
      ...task,
      id: '552',
      parentId: '501',
      title: '第二页子任务',
    }
    vi.mocked(getTask).mockResolvedValue(task)
    vi.mocked(getTaskLogs).mockResolvedValue({
      items: [], page: 0, size: 20, totalElements: 0, totalPages: 0,
    })
    vi.mocked(getTasks)
      .mockResolvedValueOnce({
        items: [], page: 0, size: 100, totalElements: 1, totalPages: 2,
      })
      .mockResolvedValueOnce({
        items: [child], page: 1, size: 100, totalElements: 1, totalPages: 2,
      })
    vi.mocked(getProject).mockResolvedValue({
      id: '103',
      owner: { id: '2', displayName: '张三' },
      name: 'Active Project',
      description: null,
      status: 'IN_PROGRESS',
      priority: 'MEDIUM',
      startDate: null,
      endDate: null,
      archivedAt: null,
      memberCount: 1,
      taskStats: { total: 2, completed: 0 },
      createdAt: '2026-07-13T08:10:00Z',
      updatedAt: '2026-07-20T08:00:00Z',
    })

    const wrapper = mount(TaskDetailView, {
      global: {
        plugins: [createPinia()],
        stubs: { RouterLink: { template: '<a><slot /></a>' } },
      },
    })
    await flushPromises()

    expect(getTasks).toHaveBeenNthCalledWith(1, {
      parentId: '501', page: 0, size: 100,
    })
    expect(getTasks).toHaveBeenNthCalledWith(2, {
      parentId: '501', page: 1, size: 100,
    })
    expect(wrapper.text()).toContain('第二页子任务')
  })

  it('refreshes the task when its assignee changes concurrently', async () => {
    const refreshedTask = {
      ...task,
      assignee: { id: '3', displayName: '李四' },
      status: 'BLOCKED' as const,
    }
    vi.mocked(getTask)
      .mockResolvedValueOnce(task)
      .mockResolvedValueOnce(refreshedTask)
    vi.mocked(getTaskLogs).mockResolvedValue({
      items: [], page: 0, size: 20, totalElements: 0, totalPages: 0,
    })
    vi.mocked(getTasks).mockResolvedValue({
      items: [], page: 0, size: 100, totalElements: 0, totalPages: 0,
    })
    vi.mocked(getProject).mockResolvedValue({
      id: '103',
      owner: { id: '2', displayName: '张三' },
      name: 'Active Project',
      description: null,
      status: 'IN_PROGRESS',
      priority: 'MEDIUM',
      startDate: null,
      endDate: null,
      archivedAt: null,
      memberCount: 2,
      taskStats: { total: 1, completed: 0 },
      createdAt: '2026-07-13T08:10:00Z',
      updatedAt: '2026-07-20T08:00:00Z',
    })
    vi.mocked(updateTaskStatus)
      .mockRejectedValueOnce({
        isAxiosError: true,
        response: {
          status: 409,
          data: {
            type: 'about:blank',
            title: 'Conflict',
            status: 409,
            detail: 'The task assignee changed. Refresh and retry.',
            instance: '/api/tasks/501/status',
            code: 'TASK_ASSIGNEE_CHANGED',
            errors: [],
          },
        },
      })
      .mockResolvedValueOnce(refreshedTask)

    const wrapper = mount(TaskDetailView, {
      global: {
        plugins: [createPinia()],
        stubs: {
          ElOption: true,
          ElSelect: { template: '<select><slot /></select>' },
          RouterLink: { template: '<a><slot /></a>' },
          teleport: true,
        },
      },
    })
    await flushPromises()

    await wrapper.findAll('button')
      .find((button) => button.text().includes('修改状态'))!
      .trigger('click')
    await wrapper.findAll('button')
      .find((button) => button.text() === '保存')!
      .trigger('click')
    await flushPromises()

    expect(updateTaskStatus).toHaveBeenCalledOnce()
    expect(getTask).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('负责人：李四')

    await wrapper.findAll('button')
      .find((button) => button.text() === '保存')!
      .trigger('click')
    await flushPromises()

    expect(updateTaskStatus).toHaveBeenNthCalledWith(2, '501', {
      status: 'BLOCKED',
    })
  })
})
