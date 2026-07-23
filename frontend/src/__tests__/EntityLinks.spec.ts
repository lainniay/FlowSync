import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'

import ProjectLink from '@/components/ProjectLink.vue'
import TaskLink from '@/components/TaskLink.vue'
import UserLink from '@/components/UserLink.vue'

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/users/:userId/profile', name: 'user-profile', component: { template: '<div />' } },
    { path: '/projects/:projectId', name: 'project-detail', component: { template: '<div />' } },
    { path: '/tasks/:taskId', name: 'task-detail', component: { template: '<div />' } },
  ],
})

describe('entity links', () => {
  it('routes user, project, and task names to their detail pages', () => {
    const user = mount(UserLink, {
      props: { userId: '3' },
      slots: { default: '张三' },
      global: { plugins: [router] },
    })
    const project = mount(ProjectLink, {
      props: { projectId: '101' },
      slots: { default: 'FlowSync' },
      global: { plugins: [router] },
    })
    const task = mount(TaskLink, {
      props: { taskId: '501', projectId: '101' },
      slots: { default: '完成接口' },
      global: { plugins: [router] },
    })

    expect(user.get('a').attributes('href')).toBe('/users/3/profile')
    expect(project.get('a').attributes('href')).toBe('/projects/101')
    expect(task.get('a').attributes('href')).toBe('/tasks/501?projectId=101')
  })
})
