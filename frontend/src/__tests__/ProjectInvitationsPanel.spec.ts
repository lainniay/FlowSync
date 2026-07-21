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
  getProjectInvitations: vi.fn<typeof getProjectInvitations>(),
}))

beforeEach(() => {
  vi.mocked(getProjectInvitations).mockReset()
})

describe('ProjectInvitationsPanel', () => {
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
})
