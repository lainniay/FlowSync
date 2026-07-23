import {
  flushPromises,
  mount,
} from '@vue/test-utils'
import {
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest'

import {
  createProjectInvitations,
  getInvitationCandidates,
  getProjectInvitations,
} from '@/views/projects/api'
import ProjectInvitationsPanel from '@/views/projects/ProjectInvitationsPanel.vue'

const project = {
  id: '101',
  owner: { id: '2', displayName: '张三' },
  name: 'FlowSync',
  description: null,
  status: 'IN_PROGRESS' as const,
  priority: 'HIGH' as const,
  startDate: null,
  endDate: null,
  archivedAt: null,
  memberCount: 2,
  taskStats: { total: 0, completed: 0 },
  createdAt: '2026-07-13T08:10:00Z',
  updatedAt: '2026-07-13T09:30:00Z',
}

vi.mock('@/views/projects/api', () => ({
  cancelProjectInvitation: vi.fn<
    typeof import('@/views/projects/api').cancelProjectInvitation
  >(),
  createProjectInvitations: vi.fn<
    typeof import('@/views/projects/api').createProjectInvitations
  >(),
  getInvitationCandidates: vi.fn<typeof getInvitationCandidates>(),
  getProjectInvitations: vi.fn<typeof getProjectInvitations>(),
}))

beforeEach(() => {
  vi.mocked(getProjectInvitations).mockReset()
  vi.mocked(getInvitationCandidates).mockReset()
  vi.mocked(createProjectInvitations).mockReset()
})

describe('ProjectInvitationsPanel', () => {
  it('hides its create action when the members interface owns the entry', async () => {
    vi.mocked(getProjectInvitations).mockResolvedValue([])
    const wrapper = mount(ProjectInvitationsPanel, {
      props: {
        project,
        canCreateInvitations: true,
        canCancelInvitations: true,
        showCreateAction: false,
      },
    })
    await flushPromises()

    expect(wrapper.findAll('button').some(
      (button) => button.text().includes('发起邀请'),
    )).toBe(false)
  })

  it('loads invitations when mounted', async () => {
    vi.mocked(getProjectInvitations).mockResolvedValue([])

    mount(ProjectInvitationsPanel, {
      props: {
        project,
        canCreateInvitations: true,
        canCancelInvitations: true,
      },
    })

    await flushPromises()

    expect(getProjectInvitations).toHaveBeenCalledWith('101')
  })

  it('searches and selects invitation candidates without entering IDs', async () => {
    vi.mocked(getProjectInvitations).mockResolvedValue([])
    vi.mocked(getInvitationCandidates).mockResolvedValue([{
      id: '3',
      displayName: '李四',
      username: 'lisi',
    }])
    vi.mocked(createProjectInvitations).mockResolvedValue([])

    const wrapper = mount(ProjectInvitationsPanel, {
      props: {
        project,
        canCreateInvitations: true,
        canCancelInvitations: true,
      },
      global: {
        stubs: {
          ElOption: true,
          ElSelect: {
            name: 'ElSelect',
            props: ['modelValue', 'remoteMethod'],
            emits: ['update:modelValue'],
            template: '<div><slot /></div>',
          },
          teleport: true,
        },
      },
    })
    await flushPromises()

    await wrapper.findAll('button')
      .find((button) => button.text().includes('发起邀请'))!
      .trigger('click')
    const selector = wrapper.getComponent({ name: 'ElSelect' })
    await selector.props('remoteMethod')('李四')
    await flushPromises()
    selector.vm.$emit('update:modelValue', ['3'])
    await flushPromises()
    await wrapper.findAll('button')
      .find((button) => button.text() === '发送邀请')!
      .trigger('click')
    await flushPromises()

    expect(getInvitationCandidates).toHaveBeenCalledWith('101', '李四')
    expect(createProjectInvitations).toHaveBeenCalledWith('101', {
      userIds: ['3'],
    })
    expect(wrapper.text()).not.toContain('用户 ID')
  })
})
