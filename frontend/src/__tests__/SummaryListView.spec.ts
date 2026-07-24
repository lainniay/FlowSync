import {
  flushPromises,
  shallowMount,
} from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getSummaries } from '@/views/summaries/api'
import SummaryListView from '@/views/summaries/SummaryListView.vue'

vi.mock('@/views/summaries/api', () => ({
  getSummaries: vi.fn<typeof getSummaries>(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({
    query: {},
  }),
  useRouter: () => ({
    push: vi.fn<() => Promise<void>>(),
  }),
}))

const summary = {
  id: '901',
  projectId: '101',
  taskId: null,
  createdBy: {
    id: '2',
    displayName: '张三',
  },
  type: 'STAGE' as const,
  content: '第一阶段已经完成需求分析和接口设计。',
  createdAt: '2026-07-13T11:00:00Z',
  updatedAt: '2026-07-13T11:00:00Z',
}

beforeEach(() => {
  vi.mocked(getSummaries).mockReset()
})

describe('SummaryListView', () => {
  it('renders as a project-scoped panel when embedded', async () => {
    vi.mocked(getSummaries).mockResolvedValue({
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })

    const wrapper = shallowMount(SummaryListView, {
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
          taskStats: { total: 1, completed: 0 },
          createdAt: '2026-07-13T08:10:00Z',
          updatedAt: '2026-07-13T08:10:00Z',
        },
      },
      global: { plugins: [createPinia()] },
    })
    await flushPromises()

    expect(getSummaries).toHaveBeenCalledWith(expect.objectContaining({
      projectId: '101',
      size: 20,
    }))
    expect(wrapper.text()).toContain('项目总结')
    expect(wrapper.text()).not.toContain('返回项目')
    expect(wrapper.text()).not.toContain('全部项目')

    await wrapper.setProps({
      project: { ...wrapper.props('project')!, id: '102', name: 'Next Project' },
    })
    await flushPromises()

    expect(getSummaries).toHaveBeenCalledWith(expect.objectContaining({
      projectId: '102',
      size: 20,
    }))
  })

  it('loads the first summary page', async () => {
    vi.mocked(getSummaries).mockResolvedValue({
      items: [summary],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })

    const wrapper = shallowMount(SummaryListView, {
      global: { plugins: [createPinia()] },
    })

    await flushPromises()

    expect(getSummaries).toHaveBeenCalledWith({
      projectId: undefined,
      type: undefined,
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
    })

    expect(
      wrapper.get('[data-testid="summary-content"]')
        .attributes('data-state'),
    ).toBe('success')
    expect(wrapper.text()).not.toContain('创建者 ID')
    expect(wrapper.text()).not.toContain('任务 ID')
    expect(wrapper.text()).not.toContain('项目 ID')
    expect(wrapper.get('el-pagination-stub').attributes('layout'))
      .toBe('total, prev, pager, next')
    expect(wrapper.get('el-pagination-stub').attributes('page-sizes'))
      .toBeUndefined()
    expect(wrapper.text()).not.toContain('查询')
    expect(wrapper.text()).not.toContain('查看')

    const vm = wrapper.vm as unknown as {
      filters: { type: string }
      handleSearch: () => Promise<void>
    }
    vm.filters.type = 'FINAL'
    await vm.handleSearch()

    expect(getSummaries).toHaveBeenLastCalledWith(expect.objectContaining({
      type: 'FINAL',
      page: 0,
      size: 20,
    }))
  })

  it('shows empty after a successful empty response', async () => {
    vi.mocked(getSummaries).mockResolvedValue({
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })

    const wrapper = shallowMount(SummaryListView, {
      global: { plugins: [createPinia()] },
    })

    await flushPromises()

    expect(
      wrapper.get('[data-testid="summary-content"]')
        .attributes('data-state'),
    ).toBe('empty')
  })

  it('shows error when the request fails', async () => {
    vi.mocked(getSummaries).mockRejectedValue(
      new Error('network failed'),
    )

    const wrapper = shallowMount(SummaryListView, {
      global: { plugins: [createPinia()] },
    })

    await flushPromises()

    expect(
      wrapper.get('[data-testid="summary-content"]')
        .attributes('data-state'),
    ).toBe('error')
  })
})
