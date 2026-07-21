import {
  flushPromises,
  shallowMount,
} from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getTasks } from '@/views/tasks/api'
import TaskListView from '@/views/tasks/TaskListView.vue'

vi.mock('@/views/tasks/api', () => ({
  getTasks: vi.fn<typeof getTasks>(),
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
})

describe('TaskListView', () => {
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
      assigneeId: undefined,
      status: undefined,
      priority: undefined,
      parentId: undefined,
      dueBefore: undefined,
      dueAfter: undefined,
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
    })

    expect(
      wrapper.get('[data-testid="task-content"]')
        .attributes('data-state'),
    ).toBe('success')
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
