import { setupServer } from 'msw/node'
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'

import { handlers } from '../mocks/handlers'
import { mockState, resetMockState, setMockCurrentUser } from '../mocks/store'

const server = setupServer(...handlers)
const headers = { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': 'mock-csrf-token' }

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
beforeEach(() => resetMockState())
afterAll(() => server.close())

describe('mock API behavior', () => {
  it('adds the invitee as a member when an invitation is accepted', async () => {
    setMockCurrentUser('4')
    const response = await fetch('http://localhost:3000/api/project-invitations/1001', {
      method: 'PUT', headers, body: JSON.stringify({ status: 'ACCEPTED' }),
    })
    expect(response.status).toBe(200)
    expect(mockState.members).toContainEqual(
      expect.objectContaining({ projectId: '101', userId: '4' }),
    )
  })

  it('stores task progress when the assignee adds a log', async () => {
    setMockCurrentUser('3')
    const response = await fetch('http://localhost:3000/api/tasks/501/logs', {
      method: 'POST', headers,
      body: JSON.stringify({ progressPercent: 75, content: '联调完成' }),
    })
    expect(response.status).toBe(201)
    expect(mockState.taskLogs).toContainEqual(
      expect.objectContaining({ taskId: '501', progressPercent: 75 }),
    )
  })

  it('maps draft parent IDs when an AI plan is imported', async () => {
    setMockCurrentUser('2')
    const body = { items: [
      {
        draftId: 'parent', parentDraftId: null, title: '父任务', description: null,
        priority: 'HIGH', dueDate: null, assigneeId: '2',
      },
      {
        draftId: 'child', parentDraftId: 'parent', title: '子任务', description: null,
        priority: 'MEDIUM', dueDate: null, assigneeId: null,
      },
    ] }
    const response = await fetch('http://localhost:3000/api/projects/101/ai/task-plans/imports', {
      method: 'POST', headers, body: JSON.stringify(body),
    })
    expect(response.status).toBe(201)
    const imported = mockState.tasks.slice(-2)
    expect(imported[1]?.parentId).toBe(imported[0]?.id)
  })

  it('returns Problem Details for invalid credentials and missing CSRF', async () => {
    setMockCurrentUser(null)
    const invalidLogin = await fetch('http://localhost:3000/api/auth/login', {
      method: 'POST', headers,
      body: JSON.stringify({ username: 'admin', password: 'wrong-password' }),
    })
    const missingCsrf = await fetch('http://localhost:3000/api/auth/login', { method: 'POST' })
    expect(invalidLogin.status).toBe(401)
    expect(missingCsrf.status).toBe(403)
  })

  it('forbids a user from responding to another user\'s invitation', async () => {
    const response = await fetch('http://localhost:3000/api/project-invitations/1001', {
      method: 'PUT', headers, body: JSON.stringify({ status: 'ACCEPTED' }),
    })
    expect(response.status).toBe(403)
    expect(await response.json()).toEqual(expect.objectContaining({ code: 'INVITATION_NOT_OWNED' }))
  })

  it('rejects unknown sorts and writes to archived projects', async () => {
    setMockCurrentUser('2')
    const invalidSort = await fetch('http://localhost:3000/api/tasks?sort=password,asc')
    const project = mockState.projects.find((item) => item.id === '101')
    if (!project) throw new Error('seed project missing')
    mockState.projects = mockState.projects.map((item) =>
      item.id === project.id ? { ...item, archivedAt: '2026-07-13T00:00:00.000Z' } : item,
    )
    const archivedWrite = await fetch('http://localhost:3000/api/tasks/501/logs', {
      method: 'POST', headers,
      body: JSON.stringify({ progressPercent: 50, content: '不应写入' }),
    })
    expect(invalidSort.status).toBe(422)
    expect(archivedWrite.status).toBe(409)
  })

  it('sorts paginated resources by createdAt descending by default', async () => {
    const response = await fetch('http://localhost:3000/api/projects?archived=false')
    const body = await response.json() as { items: Array<{ createdAt: string }> }
    expect(body.items.map((item) => item.createdAt)).toEqual(
      [...body.items].map((item) => item.createdAt).sort().reverse(),
    )
  })

  it.each([
    ['/projects/101/members', 'POST', { userIds: ['4', '4'] }, undefined],
    ['/projects/101/invitations', 'POST', { userIds: ['4', '4'] }, '2'],
    ['/tasks/501/status', 'PUT', { status: 'INVALID' }, '3'],
    ['/projects/101', 'PUT', {
      name: '缺少字段', status: 'IN_PROGRESS', priority: 'HIGH', startDate: null, endDate: null,
    }, undefined],
    ['/users', 'POST', {
      username: 'bad-role', initialPassword: 'password123', displayName: '错误角色',
      systemRole: 'OWNER', phone: null, email: null,
    }, undefined],
  ] as const)('rejects invalid boundary input for %s', async (path, method, body, userId) => {
    setMockCurrentUser(userId ?? '1')
    const options = { headers, body: JSON.stringify(body) }
    const url = `http://localhost:3000/api${path}`
    const response = method === 'POST'
      ? await fetch(url, { method: 'POST', ...options })
      : await fetch(url, { method: 'PUT', ...options })
    expect(response.status).toBe(422)
  })

  it('returns 400 Problem Details when a JSON body is malformed', async () => {
    // Given
    const malformedJson = '{"username":'

    // When
    const response = await fetch('http://localhost:3000/api/users', {
      method: 'POST', headers, body: malformedJson,
    })
    const body: unknown = await response.json()

    // Then
    expect(response.status).toBe(400)
    expect(response.headers.get('content-type')).toContain('application/problem+json')
    expect(body).toEqual(expect.objectContaining({
      status: 400, code: 'BAD_REQUEST', instance: '/api/users', errors: [],
    }))
  })

  it.each([
    '/users?active=banana',
    '/users?systemRole=OWNER',
    '/projects?archived=banana',
    '/projects?status=BLOCKED',
    '/tasks?status=UNKNOWN',
    '/tasks?priority=URGENT',
    '/tasks?dueBefore=2026-07-99',
    '/project-invitations?status=UNKNOWN',
    '/summaries?type=UNKNOWN',
  ])('rejects an invalid query parameter for %s', async (path) => {
    // When
    const response = await fetch(`http://localhost:3000/api${path}`)

    // Then
    expect(response.status).toBe(422)
  })

  it('rejects an invalid calendar date without creating a task', async () => {
    // Given
    const taskCount = mockState.tasks.length

    // When
    const response = await fetch('http://localhost:3000/api/tasks', {
      method: 'POST', headers,
      body: JSON.stringify({
        projectId: '101', parentId: null, title: '非法日期', description: null,
        assigneeId: '2', status: 'NOT_STARTED', priority: 'MEDIUM', dueDate: '2026-07-99',
      }),
    })

    // Then
    expect(response.status).toBe(422)
    expect(mockState.tasks).toHaveLength(taskCount)
  })

  it('rejects invalid user contact fields', async () => {
    // When
    const response = await fetch('http://localhost:3000/api/users', {
      method: 'POST', headers,
      body: JSON.stringify({
        username: 'invalid-contact', initialPassword: 'password123', displayName: '错误联系方式',
        systemRole: 'USER', phone: { value: '13800000000' }, email: 'a'.repeat(101),
      }),
    })

    // Then
    expect(response.status).toBe(422)
  })

  it('rejects invalid AI parameters', async () => {
    setMockCurrentUser('2')
    // When
    const response = await fetch('http://localhost:3000/api/projects/101/ai/task-plans', {
      method: 'POST', headers,
      body: JSON.stringify({
        goal: '生成任务', description: 42,
        constraints: { maxItems: 3, targetEndDate: '2026-02-30' },
      }),
    })

    // Then
    expect(response.status).toBe(422)
  })

  it('returns an indexed batch error and rolls back member writes', async () => {
    // Given
    const memberCount = mockState.members.length

    // When
    const response = await fetch('http://localhost:3000/api/projects/101/members', {
      method: 'POST', headers, body: JSON.stringify({ userIds: ['4', '4'] }),
    })
    const body: unknown = await response.json()

    // Then
    expect(response.status).toBe(422)
    expect(body).toEqual(expect.objectContaining({
      errors: [expect.objectContaining({ field: 'userIds[1]' })],
    }))
    expect(mockState.members).toHaveLength(memberCount)
  })

  it('returns an indexed AI item error and rolls back task writes', async () => {
    // Given
    setMockCurrentUser('2')
    const taskCount = mockState.tasks.length
    const items = [
      {
        draftId: 'd1', parentDraftId: null, title: '有效任务', description: null,
        priority: 'HIGH', dueDate: null, assigneeId: '2',
      },
      {
        draftId: 'd2', parentDraftId: 'd1', title: '', description: null,
        priority: 'MEDIUM', dueDate: null, assigneeId: null,
      },
    ]

    // When
    const response = await fetch('http://localhost:3000/api/projects/101/ai/task-plans/imports', {
      method: 'POST', headers, body: JSON.stringify({ items }),
    })
    const body: unknown = await response.json()

    // Then
    expect(response.status).toBe(422)
    expect(body).toEqual(expect.objectContaining({
      errors: [expect.objectContaining({ field: 'items[1].title' })],
    }))
    expect(mockState.tasks).toHaveLength(taskCount)
  })

  it('returns the documented task response fields', async () => {
    // When
    const response = await fetch('http://localhost:3000/api/tasks/501')
    const body: unknown = await response.json()

    // Then
    expect(response.status).toBe(200)
    expect(response.headers.get('content-type')).toContain('application/json')
    expect(body).toEqual(expect.objectContaining({
      id: expect.any(String),
      projectId: expect.any(String),
      parentId: null,
      assignee: expect.objectContaining({ id: expect.any(String), displayName: expect.any(String) }),
      creator: expect.objectContaining({ id: expect.any(String), displayName: expect.any(String) }),
      title: expect.any(String),
      status: expect.any(String),
      priority: expect.any(String),
      progressPercent: expect.any(Number),
      createdAt: expect.any(String),
      updatedAt: expect.any(String),
    }))
  })
})
