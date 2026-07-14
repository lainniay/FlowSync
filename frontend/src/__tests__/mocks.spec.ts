import { setupServer } from 'msw/node'
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'

import { apiHandlers, handlers } from '../mocks/handlers'
import { resetMockState, setMockCurrentUser } from '../mocks/store'

type ContractCase = {
  readonly name: string
  readonly method: 'GET' | 'POST' | 'PUT' | 'DELETE'
  readonly path: string
  readonly status: number
  readonly userId?: string | null
  readonly body?: Readonly<Record<string, unknown>>
}

const writeHeaders = { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': 'mock-csrf-token' }

const contractCases: readonly ContractCase[] = [
  { name: 'GET auth csrf', method: 'GET', path: '/auth/csrf', status: 200 },
  {
    name: 'POST auth login', method: 'POST', path: '/auth/login', status: 200, userId: null,
    body: { username: 'zhangsan', password: 'user1234' },
  },
  { name: 'POST auth logout', method: 'POST', path: '/auth/logout', status: 204 },
  { name: 'GET current user', method: 'GET', path: '/users/me', status: 200 },
  {
    name: 'PUT current user', method: 'PUT', path: '/users/me', status: 200,
    body: { displayName: '管理员', phone: null, email: 'admin@flowsync.local' },
  },
  {
    name: 'PUT current password', method: 'PUT', path: '/users/me/password', status: 204,
    body: { currentPassword: 'admin1234', newPassword: 'new-admin-password' },
  },
  { name: 'GET users', method: 'GET', path: '/users', status: 200 },
  {
    name: 'POST user', method: 'POST', path: '/users', status: 201,
    body: {
      username: 'new-user', initialPassword: 'password123', displayName: '新用户',
      systemRole: 'USER', phone: null, email: null,
    },
  },
  { name: 'GET user', method: 'GET', path: '/users/2', status: 200 },
  {
    name: 'PUT user', method: 'PUT', path: '/users/4', status: 200,
    body: {
      displayName: '王五', phone: null, email: 'wangwu@example.com', systemRole: 'USER', active: true,
    },
  },
  {
    name: 'PUT user password', method: 'PUT', path: '/users/4/password', status: 204,
    body: { newPassword: 'temporary-password' },
  },
  { name: 'GET projects', method: 'GET', path: '/projects', status: 200 },
  {
    name: 'POST project', method: 'POST', path: '/projects', status: 201,
    body: {
      name: '新项目', description: null, status: 'NOT_STARTED', priority: 'MEDIUM',
      startDate: null, endDate: null, ownerId: '2',
    },
  },
  { name: 'GET project', method: 'GET', path: '/projects/101', status: 200 },
  {
    name: 'PUT project', method: 'PUT', path: '/projects/101', status: 200,
    body: {
      name: 'FlowSync 2', description: null, status: 'IN_PROGRESS', priority: 'HIGH',
      startDate: '2026-07-13', endDate: null,
    },
  },
  {
    name: 'PUT project owner', method: 'PUT', path: '/projects/101/owner', status: 200,
    body: { ownerId: '3' },
  },
  { name: 'PUT project archive', method: 'PUT', path: '/projects/101/archive', status: 200 },
  { name: 'DELETE project archive', method: 'DELETE', path: '/projects/103/archive', status: 200 },
  { name: 'DELETE project', method: 'DELETE', path: '/projects/103', status: 204 },
  { name: 'GET project members', method: 'GET', path: '/projects/101/members', status: 200 },
  {
    name: 'POST project members', method: 'POST', path: '/projects/101/members', status: 201,
    body: { userIds: ['4'] },
  },
  {
    name: 'DELETE project member', method: 'DELETE', path: '/projects/102/members/4', status: 204,
  },
  {
    name: 'project member cannot update project', method: 'PUT', path: '/projects/101',
    status: 403, userId: '3',
    body: {
      name: '越权修改', description: null, status: 'IN_PROGRESS', priority: 'HIGH',
      startDate: null, endDate: null,
    },
  },
  {
    name: 'GET project invitations', method: 'GET', path: '/projects/101/invitations', status: 200,
  },
  {
    name: 'POST project invitations', method: 'POST', path: '/projects/102/invitations', status: 201,
    userId: '2', body: { userIds: ['3'] },
  },
  {
    name: 'DELETE project invitation', method: 'DELETE',
    path: '/projects/101/invitations/1001', status: 204,
  },
  {
    name: 'GET received invitations', method: 'GET', path: '/project-invitations', status: 200,
    userId: '4',
  },
  {
    name: 'PUT received invitation', method: 'PUT', path: '/project-invitations/1001', status: 200,
    userId: '4', body: { status: 'ACCEPTED' },
  },
  { name: 'GET tasks', method: 'GET', path: '/tasks', status: 200 },
  {
    name: 'POST task', method: 'POST', path: '/tasks', status: 201,
    userId: '2',
    body: {
      projectId: '101', parentId: null, title: '新任务', description: null, assigneeId: '3',
      status: 'NOT_STARTED', priority: 'MEDIUM', dueDate: null,
    },
  },
  { name: 'GET task', method: 'GET', path: '/tasks/501', status: 200 },
  {
    name: 'PUT task', method: 'PUT', path: '/tasks/502', status: 200,
    userId: '2',
    body: {
      parentId: null, title: '更新任务', description: null, assigneeId: '3',
      status: 'IN_PROGRESS', priority: 'HIGH', dueDate: null,
    },
  },
  {
    name: 'PUT task status', method: 'PUT', path: '/tasks/501/status', status: 200,
    userId: '3', body: { status: 'COMPLETED' },
  },
  { name: 'DELETE task', method: 'DELETE', path: '/tasks/502', status: 204, userId: '2' },
  {
    name: 'archived project rejects task creation', method: 'POST', path: '/tasks', status: 409,
    userId: '4',
    body: {
      projectId: '103', parentId: null, title: '归档任务', description: null, assigneeId: '1',
      status: 'NOT_STARTED', priority: 'LOW', dueDate: null,
    },
  },
  { name: 'GET task logs', method: 'GET', path: '/tasks/501/logs', status: 200 },
  {
    name: 'POST task log', method: 'POST', path: '/tasks/501/logs', status: 201,
    userId: '3', body: { progressPercent: 60, content: '完成接口联调' },
  },
  {
    name: 'DELETE task log', method: 'DELETE', path: '/tasks/501/logs/802', status: 204,
    userId: '2',
  },
  { name: 'GET summaries', method: 'GET', path: '/summaries', status: 200 },
  {
    name: 'POST summary', method: 'POST', path: '/summaries', status: 201,
    userId: '2',
    body: { projectId: '101', taskId: null, type: 'STAGE', content: '新增阶段总结' },
  },
  { name: 'GET summary', method: 'GET', path: '/summaries/901', status: 200 },
  {
    name: 'PUT summary', method: 'PUT', path: '/summaries/902', status: 200,
    userId: '2',
    body: { type: 'FINAL', content: '任务总结完成' },
  },
  {
    name: 'DELETE summary', method: 'DELETE', path: '/summaries/902', status: 204, userId: '2',
  },
  { name: 'GET overview', method: 'GET', path: '/overview', status: 200 },
  {
    name: 'POST AI suggestion', method: 'POST', path: '/ai/task-suggestions', status: 200,
    userId: '2',
    body: { taskId: '501', focus: '依赖和风险' },
  },
  {
    name: 'POST AI task plan', method: 'POST', path: '/projects/101/ai/task-plans', status: 200,
    userId: '2',
    body: { goal: '完成项目管理模块', constraints: { maxItems: 3, targetEndDate: '2026-07-30' } },
  },
  {
    name: 'POST AI task import', method: 'POST', path: '/projects/101/ai/task-plans/imports',
    status: 201, userId: '2',
    body: {
      items: [{
        draftId: 'd1', parentDraftId: null, title: '导入任务', description: null,
        priority: 'HIGH', dueDate: null, assigneeId: '2',
      }],
    },
  },
]

const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
beforeEach(() => resetMockState())
afterAll(() => server.close())

describe('mock API contract', () => {
  it('registers every endpoint in the API contract', () => {
    expect(apiHandlers).toHaveLength(45)
  })

  it('rejects an unknown API route instead of passing it through', async () => {
    await expect(fetch('http://localhost:3000/api/not-configured')).rejects.toThrow('Failed to fetch')
  })

  it.each(contractCases)('$name', async (testCase) => {
    // Given
    setMockCurrentUser(testCase.userId === undefined ? '1' : testCase.userId)

    // When
    const response = await fetch(`http://localhost:3000/api${testCase.path}`, {
      method: testCase.method,
      headers: testCase.method === 'GET' ? undefined : writeHeaders,
      body: testCase.body ? JSON.stringify(testCase.body) : undefined,
    })

    // Then
    expect(response.status).toBe(testCase.status)
  })

})
