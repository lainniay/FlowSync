import {
  flushPromises,
  shallowMount,
} from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getTasks } from '@/views/tasks/api'
import TaskListView from '@/views/tasks/TaskListView.vue'

const scrollIntoView = vi.fn<() => void>()
const routeQuery = vi.hoisted(() => ({ value: {} as Record<string, string> }))

vi.mock('@/views/tasks/api', () => ({
  getTasks: vi.fn<typeof getTasks>(),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    currentUser: {
      id: '3',
      username: 'lisi',
      displayName: '李四',
      systemRole: 'USER',
      active: true,
    },
  }),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({
    query: routeQuery.value,
  }),
  useRouter: () => ({
    push: vi.fn<() => Promise<void>>(),
  }),
}))

const task = {
  id: '501',
  projectId: '101',
  parentId: null,
  assignee: {
    id: '3',
    displayName: '李四',
  },
  creator: {
    id: '2',
    displayName: '张三',
  },
  title: '完成登录页面',
  description: '实现登录、退出和错误提示',
  status: 'IN_PROGRESS' as const,
  priority: 'HIGH' as const,
  progressPercent: 40,
  dueDate: '2026-07-20',
  createdAt: '2026-07-13T09:00:00Z',
  updatedAt: '2026-07-13T10:00:00Z',
}

beforeEach(() => {
  vi.mocked(getTasks).mockReset()
  routeQuery.value = {}
  scrollIntoView.mockReset()
  HTMLElement.prototype.scrollIntoView = scrollIntoView
})

describe('TaskListView', () => {
  it('shows project-wide tasks without assignment cards when embedded', async () => {
    vi.mocked(getTasks).mockImplementation(async (query) => ({
      items: [task, { ...task, id: '502', assignee: null }],
      page: 0,
      size: query.size ?? 20,
      totalElements: 2,
      totalPages: 1,
    }))

    const wrapper = shallowMount(TaskListView, {
      props: {
        embedded: true,
        project: {
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
          taskStats: { total: 2, completed: 0 },
          createdAt: '2026-07-13T08:10:00Z',
          updatedAt: '2026-07-13T08:10:00Z',
        },
      },
      global: { plugins: [createPinia()] },
    })
    await flushPromises()

    expect(getTasks).toHaveBeenCalledWith(expect.objectContaining({
      projectId: '101',
      assigneeId: undefined,
      page: 0,
      size: 20,
    }))
    expect(wrapper.find('.assignment-panel').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('任务总数')
    expect(wrapper.find('.task-summary-grid').exists()).toBe(false)

    await wrapper.setProps({
      project: { ...wrapper.props('project')!, id: '102', name: 'Next Project' },
    })
    await flushPromises()

    expect(getTasks).toHaveBeenCalledWith(expect.objectContaining({
      projectId: '102',
      assigneeId: undefined,
      page: 0,
      size: 20,
    }))
  })

  it('summarizes my tasks, warns about deadlines, and filters from a card', async () => {
    const soon = new Date()
    soon.setDate(soon.getDate() + 3)
    const dueDate = [
      soon.getFullYear(),
      String(soon.getMonth() + 1).padStart(2, '0'),
      String(soon.getDate()).padStart(2, '0'),
    ].join('-')
    const blockedTask = {
      ...task,
      id: '502',
      title: '等待接口联调',
      status: 'BLOCKED' as const,
      dueDate,
    }
    const completedTask = {
      ...task,
      id: '503',
      status: 'COMPLETED' as const,
      dueDate,
    }
    vi.mocked(getTasks).mockImplementation(async (query) => ({
      items: [task, blockedTask, completedTask],
      page: query.page ?? 0,
      size: query.size ?? 20,
      totalElements: 3,
      totalPages: 1,
    }))

    const wrapper = shallowMount(TaskListView, {
      global: { plugins: [createPinia()] },
    })
    await flushPromises()

    expect(wrapper.get('[data-testid="task-summary-IN_PROGRESS"]').text()).toContain('1')
    expect(wrapper.get('[data-testid="task-summary-COMPLETED"]').text()).toContain('1')
    expect(wrapper.get('[data-testid="task-summary-BLOCKED"]').text()).toContain('1')
    expect(wrapper.get('[data-testid="task-deadline-alert"]').text())
      .toContain('等待接口联调')

    await wrapper.get('[data-testid="task-summary-BLOCKED"]').trigger('click')
    await flushPromises()

    expect(getTasks).toHaveBeenLastCalledWith({
      q: undefined,
      projectId: undefined,
      assigneeId: '3',
      status: 'BLOCKED',
      priority: undefined,
      dueBefore: undefined,
      dueAfter: undefined,
      incomplete: undefined,
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
    })
    expect(scrollIntoView).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' })
  })

  it('loads the first task page', async () => {
    vi.mocked(getTasks).mockResolvedValue({
      items: [task],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })

    const wrapper = shallowMount(TaskListView, {
      global: { plugins: [createPinia()] },
    })

    await flushPromises()

    expect(getTasks).toHaveBeenCalledWith({
      q: undefined,
      projectId: undefined,
      assigneeId: '3',
      status: undefined,
      priority: undefined,
      dueBefore: undefined,
      dueAfter: undefined,
      incomplete: undefined,
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
    })

    expect(
      wrapper.get('[data-testid="task-content"]')
        .attributes('data-state'),
    ).toBe('success')
    expect(wrapper.text()).not.toContain('负责人 ID')
    expect(wrapper.text()).not.toContain('父任务 ID')
    expect(wrapper.text()).not.toContain('项目 ID')
    expect(wrapper.get('el-pagination-stub').attributes('layout'))
      .toBe('total, prev, pager, next')
    expect(wrapper.get('el-pagination-stub').attributes('page-sizes'))
      .toBeUndefined()

    const vm = wrapper.vm as unknown as {
      filters: { status: string }
      handleSearch: () => Promise<void>
    }
    vm.filters.status = 'COMPLETED'
    await vm.handleSearch()

    expect(getTasks).toHaveBeenLastCalledWith(expect.objectContaining({
      status: 'COMPLETED',
      page: 0,
      size: 20,
    }))
    expect(wrapper.text()).not.toContain('查询')
  })

  it('applies personal task filters passed from the dashboard route', async () => {
    routeQuery.value = {
      status: 'BLOCKED',
      dueAfter: '2026-07-23',
      dueBefore: '2026-07-23',
      incomplete: 'true',
    }
    vi.mocked(getTasks).mockResolvedValue({
      items: [task], page: 0, size: 20, totalElements: 1, totalPages: 1,
    })

    const wrapper = shallowMount(TaskListView, {
      global: { plugins: [createPinia()] },
    })
    await flushPromises()

    expect(getTasks).toHaveBeenCalledWith(expect.objectContaining({
      assigneeId: '3',
      status: 'BLOCKED',
      dueAfter: '2026-07-23',
      dueBefore: '2026-07-23',
      incomplete: true,
      page: 0,
    }))
    expect(wrapper.text()).toContain('截止日期：2026-07-23 至 2026-07-23')
  })

  it('shows empty after a successful empty response', async () => {
    vi.mocked(getTasks).mockResolvedValue({
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })

    const wrapper = shallowMount(TaskListView, {
      global: { plugins: [createPinia()] },
    })

    await flushPromises()

    expect(
      wrapper.get('[data-testid="task-content"]')
        .attributes('data-state'),
    ).toBe('empty')
  })

  it('shows error when the request fails', async () => {
    vi.mocked(getTasks).mockRejectedValue(
      new Error('network failed'),
    )

    const wrapper = shallowMount(TaskListView, {
      global: { plugins: [createPinia()] },
    })

    await flushPromises()

    expect(
      wrapper.get('[data-testid="task-content"]')
        .attributes('data-state'),
    ).toBe('error')
  })
})
