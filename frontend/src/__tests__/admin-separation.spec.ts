import { setupServer } from 'msw/node'
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'

import { handlers } from '../mocks/handlers'
import { mockState, resetMockState } from '../mocks/store'

const server = setupServer(...handlers)
const headers = { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': 'mock-csrf-token' }

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
beforeEach(() => resetMockState())
afterAll(() => server.close())

describe('ADMIN account separation', () => {
  it('requires an ADMIN to assign a USER owner when creating a project', async () => {
    // Given
    const projectCount = mockState.projects.length

    // When
    const response = await fetch('http://localhost:3000/api/projects', {
      method: 'POST', headers,
      body: JSON.stringify({
        name: '缺少业务账号', description: null, status: 'NOT_STARTED', priority: 'LOW',
        startDate: null, endDate: null,
      }),
    })

    // Then
    expect(response.status).toBe(422)
    expect(mockState.projects).toHaveLength(projectCount)
  })

  it('creates a project for a USER without adding the ADMIN as a member', async () => {
    // When
    const response = await fetch('http://localhost:3000/api/projects', {
      method: 'POST', headers,
      body: JSON.stringify({
        name: '代建项目', description: null, status: 'NOT_STARTED', priority: 'LOW',
        startDate: null, endDate: null, ownerId: '2',
      }),
    })
    const project: unknown = await response.json()

    // Then
    expect(response.status).toBe(201)
    expect(project).toEqual(expect.objectContaining({
      owner: expect.objectContaining({ id: '2' }),
    }))
    expect(mockState.members).not.toContainEqual(expect.objectContaining({ userId: '1' }))
  })

  it('rejects an ADMIN as a direct project member', async () => {
    // When
    const response = await fetch('http://localhost:3000/api/projects/101/members', {
      method: 'POST', headers, body: JSON.stringify({ userIds: ['1'] }),
    })

    // Then
    expect(response.status).toBe(422)
    expect(mockState.members).not.toContainEqual(expect.objectContaining({
      projectId: '101', userId: '1',
    }))
  })

  it('rejects an ADMIN as a project owner', async () => {
    // When
    const response = await fetch('http://localhost:3000/api/projects/101/owner', {
      method: 'PUT', headers, body: JSON.stringify({ ownerId: '1' }),
    })

    // Then
    expect(response.status).toBe(422)
    expect(mockState.projects.find((project) => project.id === '101')?.ownerId).toBe('2')
  })

  it.each([
    ['/tasks', {
      projectId: '101', parentId: null, title: '管理员任务', description: null,
      assigneeId: '2', status: 'NOT_STARTED', priority: 'LOW', dueDate: null,
    }],
    ['/tasks/501/logs', { progressPercent: 50, content: '管理员进度' }],
    ['/summaries', { projectId: '101', taskId: null, type: 'STAGE', content: '管理员总结' }],
    ['/projects/101/ai/task-plans', { goal: '管理员生成计划' }],
  ] as const)('forbids an ADMIN from writing project content through %s', async (path, body) => {
    // When
    const response = await fetch(`http://localhost:3000/api${path}`, {
      method: 'POST', headers, body: JSON.stringify(body),
    })

    // Then
    expect(response.status).toBe(403)
  })
})
