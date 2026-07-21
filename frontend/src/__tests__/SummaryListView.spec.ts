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
      taskId: undefined,
      type: undefined,
      createdBy: undefined,
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
    })

    expect(
      wrapper.get('[data-testid="summary-content"]')
        .attributes('data-state'),
    ).toBe('success')
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
