import {
  flushPromises,
  shallowMount,
} from '@vue/test-utils'
import {
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest'

import { getReceivedInvitations } from '@/views/invitations/api'
import InvitationListView from '@/views/invitations/InvitationListView.vue'

vi.mock('@/views/invitations/api', () => ({
  getReceivedInvitations: vi.fn<typeof getReceivedInvitations>(),
  respondToInvitation: vi.fn<
    typeof import('@/views/invitations/api').respondToInvitation
  >(),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()

  return {
    ...actual,
    ElMessage: {
      success: vi.fn<(message: string) => void>(),
      error: vi.fn<(message: string) => void>(),
    },
    ElMessageBox: {
      confirm: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
    },
  }
})

const invitation = {
  id: '301',
  project: { id: '101', name: 'FlowSync' },
  invitee: { id: '4', displayName: '王五' },
  invitedBy: { id: '2', displayName: '张三' },
  status: 'PENDING' as const,
  createdAt: '2026-07-13T08:20:00Z',
  respondedAt: null,
}

beforeEach(() => {
  vi.mocked(getReceivedInvitations).mockReset()
})

describe('InvitationListView', () => {
  it('loads pending invitations successfully', async () => {
    vi.mocked(getReceivedInvitations).mockResolvedValue([invitation])

    const wrapper = shallowMount(InvitationListView)

    await flushPromises()

    expect(getReceivedInvitations).toHaveBeenCalledWith({
      status: 'PENDING',
    })
    expect(
      wrapper.get('[data-testid="invitation-content"]')
        .attributes('data-state'),
    ).toBe('success')

    const vm = wrapper.vm as unknown as {
      statusFilter: '' | 'PENDING'
      handleSearch: () => Promise<void>
    }
    vm.statusFilter = ''
    await vm.handleSearch()

    expect(getReceivedInvitations).toHaveBeenLastCalledWith({
      status: undefined,
    })
    expect(wrapper.text()).not.toContain('查询')
  })

  it('shows empty after a successful empty response', async () => {
    vi.mocked(getReceivedInvitations).mockResolvedValue([])

    const wrapper = shallowMount(InvitationListView)

    await flushPromises()

    expect(
      wrapper.get('[data-testid="invitation-content"]')
        .attributes('data-state'),
    ).toBe('empty')
  })

  it('shows error when the request fails', async () => {
    vi.mocked(getReceivedInvitations).mockRejectedValue(
      new Error('network failed'),
    )

    const wrapper = shallowMount(InvitationListView)

    await flushPromises()

    expect(
      wrapper.get('[data-testid="invitation-content"]')
        .attributes('data-state'),
    ).toBe('error')
  })
})
