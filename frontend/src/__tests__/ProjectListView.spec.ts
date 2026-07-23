import {
  flushPromises,
  shallowMount,
} from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getProjects, getUserOptions } from '@/views/projects/api'
import ProjectListView from '@/views/projects/ProjectListView.vue'
import type { Project } from '@/views/projects/types'

const routeState = vi.hoisted(() => ({
  query: {} as Record<string, unknown>,
}))
const routerPush = vi.hoisted(() => vi.fn<(path: unknown) => Promise<void>>())
const scrollIntoView = vi.fn<() => void>()

vi.mock('@/views/projects/api', () => ({
  createProject: vi.fn<typeof import('@/views/projects/api').createProject>(),
  getProjects: vi.fn<typeof getProjects>(),
  getUserOptions: vi.fn<typeof getUserOptions>(),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    currentUser: {
      id: '2',
      username: 'zhangsan',
      displayName: '张三',
      systemRole: 'USER',
      active: true,
    },
  }),
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({
    push: routerPush,
  }),
}))

vi.mock('@/views/admin/api', () => ({
  getUsers: vi.fn<typeof import('@/views/admin/api').getUsers>(),
}))

const project: Project = {
  id: '101',
  owner: {
    id: '2',
    displayName: '张三',
  },
  name: 'FlowSync',
  description: '小组任务协同系统',
  status: 'IN_PROGRESS' as const,
  priority: 'HIGH' as const,
  startDate: '2026-07-13',
  endDate: '2026-08-01',
  archivedAt: null,
  memberCount: 2,
  taskStats: {
    total: 2,
    completed: 0,
  },
  createdAt: '2026-07-13T08:10:00Z',
  updatedAt: '2026-07-13T09:30:00Z',
}

beforeEach(() => {
  routeState.query = {}
  routerPush.mockReset()
  vi.mocked(getProjects).mockReset()
  vi.mocked(getUserOptions).mockReset()
  scrollIntoView.mockReset()
  HTMLElement.prototype.scrollIntoView = scrollIntoView
  vi.mocked(getUserOptions).mockResolvedValue([
    { id: '2', username: 'short-user' },
    { id: '3', username: 'very-long-username-for-ellipsis-check' },
  ])
})

describe('ProjectListView', () => {
  it('loads the first active-project page', async () => {
    vi.mocked(getProjects).mockResolvedValue({
      items: [project],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })

    const wrapper = shallowMount(ProjectListView)

    await flushPromises()

    expect(getProjects).toHaveBeenCalledWith({
      q: undefined,
      status: undefined,
      userId: undefined,
      myRole: undefined,
      archived: false,
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
    })

    expect(
      wrapper.get('[data-testid="project-content"]')
        .attributes('data-state'),
    ).toBe('success')
    expect(wrapper.get('el-pagination-stub').attributes('layout'))
      .toBe('total, prev, pager, next')
    expect(wrapper.getComponent({ name: 'ElPagination' }).props('pageSize'))
      .toBe(20)
    expect(wrapper.get('el-pagination-stub').attributes('page-sizes'))
      .toBeUndefined()
    const vm = wrapper.vm as unknown as {
      projectRole: (current: Project) => string
      projectStatusLabel: (current: Project) => string
    }
    expect(vm.projectRole(project)).toBe('Owner')
    expect(vm.projectRole({
      ...project,
      owner: { id: '3', displayName: '李四' },
    })).toBe('成员')
    expect(vm.projectStatusLabel({
      ...project,
      archivedAt: '2026-07-23T00:00:00Z',
    })).toBe('已归档')
    expect(getUserOptions).toHaveBeenCalledWith()
  })

  it('opens archived projects through a dedicated entry', async () => {
    vi.mocked(getProjects).mockResolvedValue({
      items: [], page: 0, size: 20, totalElements: 0, totalPages: 0,
    })
    const wrapper = shallowMount(ProjectListView)
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      toggleArchivedView: () => void
    }
    vm.toggleArchivedView()

    expect(routerPush).toHaveBeenCalledWith({
      name: 'projects',
      query: { archived: 'true' },
    })
  })

  it('filters projects by the selected username option', async () => {
    vi.mocked(getProjects).mockResolvedValue({
      items: [], page: 0, size: 20, totalElements: 0, totalPages: 0,
    })
    const wrapper = shallowMount(ProjectListView)
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      filters: { userId: string }
      handleSearch: () => Promise<void>
    }
    vm.filters.userId = '3'
    await vm.handleSearch()

    expect(getProjects).toHaveBeenLastCalledWith(expect.objectContaining({
      userId: '3',
      page: 0,
      size: 20,
    }))
    expect(wrapper.text()).not.toContain('查询')
  })

  it('debounces project-name filter changes before loading', async () => {
    vi.mocked(getProjects).mockResolvedValue({
      items: [], page: 0, size: 20, totalElements: 0, totalPages: 0,
    })
    const wrapper = shallowMount(ProjectListView)
    await flushPromises()
    vi.useFakeTimers()

    try {
      const vm = wrapper.vm as unknown as {
        filters: { q: string }
        scheduleSearch: () => void
      }
      vm.filters.q = 'FlowSync'
      vm.scheduleSearch()
      await vi.advanceTimersByTimeAsync(300)
      await flushPromises()

      expect(getProjects).toHaveBeenLastCalledWith(expect.objectContaining({
        q: 'FlowSync',
        page: 0,
      }))
    } finally {
      wrapper.unmount()
      vi.useRealTimers()
    }
  })

  it('loads archived projects in the archive view', async () => {
    routeState.query = { archived: 'true' }
    vi.mocked(getProjects).mockResolvedValue({
      items: [], page: 0, size: 20, totalElements: 0, totalPages: 0,
    })

    const wrapper = shallowMount(ProjectListView)
    await flushPromises()

    expect(getProjects).toHaveBeenCalledWith(expect.objectContaining({
      archived: true,
      page: 0,
      size: 20,
    }))
    expect((wrapper.vm as unknown as { hasActiveFilters: boolean }).hasActiveFilters)
      .toBe(false)
  })

  it('requests the selected page with the fixed page size', async () => {
    vi.mocked(getProjects).mockImplementation(async (query) => {
      if (query.size === 100) {
        return {
          items: [project], page: 0, size: 100, totalElements: 1, totalPages: 1,
        }
      }
      if (query.page === 1) {
        return {
          items: [project], page: 1, size: 20, totalElements: 21, totalPages: 2,
        }
      }
      return {
        items: [project],
        page: 0,
        size: 20,
        totalElements: 21,
        totalPages: 2,
      }
    })

    const wrapper = shallowMount(ProjectListView)
    await flushPromises()

    wrapper.getComponent({ name: 'ElPagination' })
      .vm.$emit('current-change', 2)
    await flushPromises()

    expect(getProjects).toHaveBeenCalledWith({
      q: undefined,
      status: undefined,
      userId: undefined,
      myRole: undefined,
      archived: false,
      page: 1,
      size: 20,
      sort: 'createdAt,desc',
    })
  })

  it('summarizes active projects and filters by a selected participation role', async () => {
    const soon = new Date()
    soon.setDate(soon.getDate() + 3)
    const endDate = [
      soon.getFullYear(),
      String(soon.getMonth() + 1).padStart(2, '0'),
      String(soon.getDate()).padStart(2, '0'),
    ].join('-')
    const memberProject: Project = {
      ...project,
      id: '102',
      owner: { id: '3', displayName: '李四' },
      name: '成员项目',
      status: 'NOT_STARTED',
      endDate,
    }
    vi.mocked(getProjects).mockResolvedValue({
      items: [project, memberProject],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
    })

    const wrapper = shallowMount(ProjectListView)
    await flushPromises()

    expect(wrapper.get('[data-testid="project-summary-IN_PROGRESS"]').text()).toContain('1')
    expect(wrapper.get('[data-testid="project-summary-OWNER"]').text()).toContain('1')
    expect(wrapper.get('[data-testid="project-summary-MEMBER"]').text()).toContain('1')
    expect(wrapper.get('[data-testid="deadline-alert"]').text()).toContain('成员项目')

    await wrapper.get('[data-testid="project-summary-MEMBER"]').trigger('click')
    await flushPromises()

    expect(getProjects).toHaveBeenLastCalledWith(expect.objectContaining({
      myRole: 'MEMBER',
      status: undefined,
      page: 0,
      size: 20,
    }))
    expect(scrollIntoView).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' })
  })

  it('shows empty after a successful empty response', async () => {
    vi.mocked(getProjects).mockResolvedValue({
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })

    const wrapper = shallowMount(ProjectListView)

    await flushPromises()

    expect(
      wrapper.get('[data-testid="project-content"]')
        .attributes('data-state'),
    ).toBe('empty')
  })

  it('shows error when the request fails', async () => {
    vi.mocked(getProjects).mockRejectedValue(
      new Error('network failed'),
    )

    const wrapper = shallowMount(ProjectListView)

    await flushPromises()

    expect(
      wrapper.get('[data-testid="project-content"]')
        .attributes('data-state'),
    ).toBe('error')
  })
})
