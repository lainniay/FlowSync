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

import { getSummaries } from '@/views/summaries/api'
import SummaryListView from '@/views/summaries/SummaryListView.vue'

vi.mock('@/views/summaries/api', () => ({
  createSummary: vi.fn<() => Promise<unknown>>(),
  getSummaries: vi.fn<typeof getSummaries>(),
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
    query: {},
  }),
  useRouter: () => ({
    push: vi.fn<() => Promise<void>>(),
  }),
}))

beforeEach(() => {
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
      global: { plugins: [createPinia()] },
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
})
