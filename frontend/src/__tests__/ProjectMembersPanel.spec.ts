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

import { getUsers } from '@/views/admin/api'
import {
  getProjectMembers,
} from '@/views/projects/api'
import ProjectMembersPanel from '@/views/projects/ProjectMembersPanel.vue'

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

vi.mock('@/views/admin/api', () => ({
  getUsers: vi.fn<typeof getUsers>(),
}))

vi.mock('@/views/projects/api', () => ({
  addProjectMembers: vi.fn<
    typeof import('@/views/projects/api').addProjectMembers
  >(),
  getProjectMembers: vi.fn<typeof getProjectMembers>(),
  removeProjectMember: vi.fn<
    typeof import('@/views/projects/api').removeProjectMember
  >(),
}))

beforeEach(() => {
  vi.mocked(getUsers).mockReset()
  vi.mocked(getProjectMembers).mockReset()
  vi.mocked(getProjectMembers).mockResolvedValue([])

  vi.stubGlobal('ResizeObserver', class {
    observe(): void {}

    disconnect(): void {}
  })
})

describe('ProjectMembersPanel', () => {
  it('loads all user option pages when adding members', async () => {
    vi.mocked(getUsers)
      .mockResolvedValueOnce({
        items: Array.from({ length: 100 }, (_, index) => ({
          id: String(index + 1),
          username: `user${index + 1}`,
          displayName: `用户 ${index + 1}`,
          phone: null,
          email: null,
          systemRole: 'USER' as const,
          active: true,
          createdAt: '2026-07-13T08:01:00Z',
          updatedAt: '2026-07-13T08:01:00Z',
        })),
        page: 0,
        size: 100,
        totalElements: 120,
        totalPages: 2,
      })
      .mockResolvedValueOnce({
        items: Array.from({ length: 20 }, (_, index) => ({
          id: String(index + 101),
          username: `user${index + 101}`,
          displayName: `用户 ${index + 101}`,
          phone: null,
          email: null,
          systemRole: 'USER' as const,
          active: true,
          createdAt: '2026-07-13T08:01:00Z',
          updatedAt: '2026-07-13T08:01:00Z',
        })),
        page: 1,
        size: 100,
        totalElements: 120,
        totalPages: 2,
      })

    const wrapper = mount(ProjectMembersPanel, {
      props: {
        project,
        canAddMembers: true,
        canRemoveMembers: true,
      },
    })

    await flushPromises()

    const addButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('添加成员'))

    expect(addButton).toBeDefined()
    await addButton!.trigger('click')
    await flushPromises()

    expect(getUsers).toHaveBeenCalledTimes(2)
    expect(getUsers).toHaveBeenNthCalledWith(1, {
      systemRole: 'USER',
      active: true,
      sort: 'username,asc',
      page: 0,
      size: 100,
    })
    expect(getUsers).toHaveBeenNthCalledWith(2, {
      systemRole: 'USER',
      active: true,
      sort: 'username,asc',
      page: 1,
      size: 100,
    })
  })
})
