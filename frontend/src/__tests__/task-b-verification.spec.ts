import { setupServer } from 'msw/node'
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'

import { handlers } from '../mocks/handlers'
import { mockState, resetMockState, setMockCurrentUser } from '../mocks/store'

const server = setupServer(...handlers)
const csrfHeaders = {
  'Content-Type': 'application/json',
  'X-CSRF-TOKEN': 'mock-csrf-token',
}

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
beforeEach(() => resetMockState())
afterAll(() => server.close())

// ============================================================
// Mock data reference
// ============================================================
// Users:  1=admin(ADMIN), 2=张三(USER, owner of 101), 3=李四(USER, assignee of 501), 4=王五(USER)
// Project 101: owner=张三(id=2), members=[张三, 李四]
// Task 501: projectId=101, creator=张三, assignee=李四
// Task 502: projectId=101, creator=张三, assignee=null
// TaskLog 801: operator=李四(id=3), TaskLog 802: operator=张三(id=2)
// Summary 901: createdBy=张三(id=2)
// ============================================================

describe('P1.1 — Task permission matrix', () => {
  it('allows project owner (张三) to edit task 501', async () => {
    setMockCurrentUser('2')

    const response = await fetch('http://localhost:3000/api/tasks/501', {
      method: 'PUT',
      headers: csrfHeaders,
      body: JSON.stringify({
        parentId: null,
        title: '更新后的标题',
        description: null,
        assigneeId: '3',
        status: 'IN_PROGRESS',
        priority: 'HIGH',
        dueDate: null,
      }),
    })

    expect(response.status).toBe(200)
  })

  it('rejects non-owner assignee (李四) from editing task 501', async () => {
    setMockCurrentUser('3')

    const response = await fetch('http://localhost:3000/api/tasks/501', {
      method: 'PUT',
      headers: csrfHeaders,
      body: JSON.stringify({
        parentId: null,
        title: '李四想改标题',
        description: null,
        assigneeId: '3',
        status: 'IN_PROGRESS',
        priority: 'HIGH',
        dueDate: null,
      }),
    })

    expect(response.status).toBe(403)
  })

  it('allows assignee (李四) to update task 501 status', async () => {
    setMockCurrentUser('3')

    const response = await fetch('http://localhost:3000/api/tasks/501/status', {
      method: 'PUT',
      headers: csrfHeaders,
      body: JSON.stringify({ status: 'COMPLETED' }),
    })

    expect(response.status).toBe(200)
  })

  it('allows project owner (张三) to update task 501 status', async () => {
    setMockCurrentUser('2')

    const response = await fetch('http://localhost:3000/api/tasks/501/status', {
      method: 'PUT',
      headers: csrfHeaders,
      body: JSON.stringify({ status: 'BLOCKED' }),
    })

    expect(response.status).toBe(200)
  })

  it('rejects non-owner non-assignee (王五) from updating task 501 status', async () => {
    setMockCurrentUser('4')
    // 王五 needs to be a member of project 101
    mockState.members.push({
      projectId: '101', userId: '4', joinedAt: '2026-07-13T12:00:00Z',
    })

    const response = await fetch('http://localhost:3000/api/tasks/501/status', {
      method: 'PUT',
      headers: csrfHeaders,
      body: JSON.stringify({ status: 'COMPLETED' }),
    })

    expect(response.status).toBe(403)
  })

  it('rejects ADMIN from editing task', async () => {
    setMockCurrentUser('1')

    const response = await fetch('http://localhost:3000/api/tasks/501', {
      method: 'PUT',
      headers: csrfHeaders,
      body: JSON.stringify({
        parentId: null,
        title: '管理员改标题',
        description: null,
        assigneeId: '3',
        status: 'IN_PROGRESS',
        priority: 'HIGH',
        dueDate: null,
      }),
    })

    expect(response.status).toBe(403)
  })
})

describe('P1.1 — Task log permission matrix', () => {
  it('allows project owner (张三) to delete 李四\'s log 801', async () => {
    setMockCurrentUser('2')

    const response = await fetch('http://localhost:3000/api/tasks/501/logs/801', {
      method: 'DELETE',
      headers: csrfHeaders,
    })

    expect(response.status).toBe(204)
  })

  it('allows log creator (李四) to delete own log 801', async () => {
    setMockCurrentUser('3')

    const response = await fetch('http://localhost:3000/api/tasks/501/logs/801', {
      method: 'DELETE',
      headers: csrfHeaders,
    })

    expect(response.status).toBe(204)
  })

  it('rejects non-owner non-creator (王五) from deleting log 801', async () => {
    setMockCurrentUser('4')
    mockState.members.push({
      projectId: '101', userId: '4', joinedAt: '2026-07-13T12:00:00Z',
    })

    const response = await fetch('http://localhost:3000/api/tasks/501/logs/801', {
      method: 'DELETE',
      headers: csrfHeaders,
    })

    expect(response.status).toBe(403)
  })

  it('allows owner (张三) to create task log', async () => {
    setMockCurrentUser('2')

    const response = await fetch('http://localhost:3000/api/tasks/501/logs', {
      method: 'POST',
      headers: csrfHeaders,
      body: JSON.stringify({ progressPercent: 60, content: 'owner 更新的进度' }),
    })

    expect(response.status).toBe(201)
  })

  it('allows assignee (李四) to create task log', async () => {
    setMockCurrentUser('3')

    const response = await fetch('http://localhost:3000/api/tasks/501/logs', {
      method: 'POST',
      headers: csrfHeaders,
      body: JSON.stringify({ progressPercent: 80, content: 'assignee 更新的进度' }),
    })

    expect(response.status).toBe(201)
  })
})

describe('P1.2 — Summary permission: project owner manages member summaries', () => {
  it('allows summary creator (张三) to edit summary 901', async () => {
    setMockCurrentUser('2')

    const response = await fetch('http://localhost:3000/api/summaries/901', {
      method: 'PUT',
      headers: csrfHeaders,
      body: JSON.stringify({ type: 'FINAL', content: '张三更新了自己的总结' }),
    })

    expect(response.status).toBe(200)
  })

  it('rejects non-creator non-owner (李四) from editing summary 901', async () => {
    setMockCurrentUser('3')

    const response = await fetch('http://localhost:3000/api/summaries/901', {
      method: 'PUT',
      headers: csrfHeaders,
      body: JSON.stringify({ type: 'STAGE', content: '李四想改张三的总结' }),
    })

    expect(response.status).toBe(403)
  })

  it('allows summary creator (张三) to delete summary 901', async () => {
    setMockCurrentUser('2')

    const response = await fetch('http://localhost:3000/api/summaries/901', {
      method: 'DELETE',
      headers: csrfHeaders,
    })

    expect(response.status).toBe(204)
  })
})

describe('P1.3 — AI task plan import: parentDraftId normalization', () => {
  it('imports a single task with parentDraftId: null', async () => {
    setMockCurrentUser('2')

    const response = await fetch(
      'http://localhost:3000/api/projects/101/ai/task-plans/imports',
      {
        method: 'POST',
        headers: csrfHeaders,
        body: JSON.stringify({
          items: [
            {
              draftId: 'draft-1',
              parentDraftId: null,
              title: '单项任务',
              description: null,
              priority: 'MEDIUM',
              dueDate: null,
              assigneeId: null,
            },
          ],
        }),
      },
    )

    expect(response.status).toBe(201)
    const body: unknown = await response.json()
    expect(body).toEqual(
      expect.objectContaining({ importedCount: 1 }),
    )
  })

  it('imports multiple tasks with parent-child relationships', async () => {
    setMockCurrentUser('2')

    const response = await fetch(
      'http://localhost:3000/api/projects/101/ai/task-plans/imports',
      {
        method: 'POST',
        headers: csrfHeaders,
        body: JSON.stringify({
          items: [
            {
              draftId: 'draft-root',
              parentDraftId: null,
              title: '根任务',
              description: null,
              priority: 'HIGH',
              dueDate: null,
              assigneeId: null,
            },
            {
              draftId: 'draft-child',
              parentDraftId: 'draft-root',
              title: '子任务',
              description: null,
              priority: 'MEDIUM',
              dueDate: null,
              assigneeId: null,
            },
          ],
        }),
      },
    )

    expect(response.status).toBe(201)
    const body: unknown = await response.json()
    expect(body).toEqual(
      expect.objectContaining({ importedCount: 2 }),
    )
  })

  it('rejects import with invalid parentDraftId reference', async () => {
    setMockCurrentUser('2')

    const response = await fetch(
      'http://localhost:3000/api/projects/101/ai/task-plans/imports',
      {
        method: 'POST',
        headers: csrfHeaders,
        body: JSON.stringify({
          items: [
            {
              draftId: 'draft-orphan',
              parentDraftId: 'nonexistent-ref',
              title: '引用不存在的父任务',
              description: null,
              priority: 'MEDIUM',
              dueDate: null,
              assigneeId: null,
            },
          ],
        }),
      },
    )

    expect(response.status).toBe(422)
  })

  it('rejects non-owner (李四) from importing task plan', async () => {
    setMockCurrentUser('3')

    const response = await fetch(
      'http://localhost:3000/api/projects/101/ai/task-plans/imports',
      {
        method: 'POST',
        headers: csrfHeaders,
        body: JSON.stringify({
          items: [
            {
              draftId: 'draft-lisi',
              parentDraftId: null,
              title: '李四的导入',
              description: null,
              priority: 'MEDIUM',
              dueDate: null,
              assigneeId: null,
            },
          ],
        }),
      },
    )

    expect(response.status).toBe(403)
  })
})

describe('P2.2 — AI plan generation: project owner check', () => {
  it('allows project owner (张三) to generate AI plan', async () => {
    setMockCurrentUser('2')

    const response = await fetch(
      'http://localhost:3000/api/projects/101/ai/task-plans',
      {
        method: 'POST',
        headers: csrfHeaders,
        body: JSON.stringify({ goal: '完成前端所有页面开发' }),
      },
    )

    expect(response.status).toBe(200)
  })

  it('rejects non-owner member (李四) from generating AI plan', async () => {
    setMockCurrentUser('3')

    const response = await fetch(
      'http://localhost:3000/api/projects/101/ai/task-plans',
      {
        method: 'POST',
        headers: csrfHeaders,
        body: JSON.stringify({ goal: '完成前端所有页面开发' }),
      },
    )

    expect(response.status).toBe(403)
  })

  it('rejects ADMIN from generating AI plan', async () => {
    setMockCurrentUser('1')

    const response = await fetch(
      'http://localhost:3000/api/projects/101/ai/task-plans',
      {
        method: 'POST',
        headers: csrfHeaders,
        body: JSON.stringify({ goal: '管理员生成计划' }),
      },
    )

    expect(response.status).toBe(403)
  })
})

describe('P0 — Task detail data integrity', () => {
  it('returns task 501 with correct project owner', async () => {
    setMockCurrentUser('2')

    const taskResponse = await fetch('http://localhost:3000/api/tasks/501')
    const task: unknown = await taskResponse.json()

    expect(taskResponse.status).toBe(200)
    expect(task).toMatchObject({ id: '501', projectId: '101' })

    const projectResponse = await fetch('http://localhost:3000/api/projects/101')
    const project: unknown = await projectResponse.json()

    expect(projectResponse.status).toBe(200)
    expect(project).toMatchObject({
      owner: expect.objectContaining({ id: '2' }),
    })
  })

  it('returns 404 for nonexistent task', async () => {
    setMockCurrentUser('2')

    const response = await fetch('http://localhost:3000/api/tasks/99999', {
      headers: csrfHeaders,
    })

    expect(response.status).toBe(404)
  })

  it('returns task 502 with null assignee correctly', async () => {
    setMockCurrentUser('2')

    const response = await fetch('http://localhost:3000/api/tasks/502')
    const task: unknown = await response.json()

    expect(response.status).toBe(200)
    expect(task).toMatchObject({
      id: '502',
      assignee: null,
      creator: expect.objectContaining({ id: '2' }),
    })
  })
})

describe('Task B — Create task permission', () => {
  it('allows project owner (张三) to create task in project 101', async () => {
    setMockCurrentUser('2')

    const response = await fetch('http://localhost:3000/api/tasks', {
      method: 'POST',
      headers: csrfHeaders,
      body: JSON.stringify({
        projectId: '101',
        parentId: null,
        title: '新任务',
        description: null,
        assigneeId: null,
        status: 'NOT_STARTED',
        priority: 'MEDIUM',
        dueDate: null,
      }),
    })

    expect(response.status).toBe(201)
  })

  it('rejects non-owner member (李四) from creating task in project 101', async () => {
    setMockCurrentUser('3')

    const response = await fetch('http://localhost:3000/api/tasks', {
      method: 'POST',
      headers: csrfHeaders,
      body: JSON.stringify({
        projectId: '101',
        parentId: null,
        title: '李四的任务',
        description: null,
        assigneeId: null,
        status: 'NOT_STARTED',
        priority: 'MEDIUM',
        dueDate: null,
      }),
    })

    expect(response.status).toBe(403)
  })
})

describe('Task B — Task status update and log sync', () => {
  it('updates task status and reflects immediately', async () => {
    setMockCurrentUser('2')

    const response = await fetch('http://localhost:3000/api/tasks/501/status', {
      method: 'PUT',
      headers: csrfHeaders,
      body: JSON.stringify({ status: 'COMPLETED' }),
    })
    const updated: unknown = await response.json()

    expect(response.status).toBe(200)
    expect(updated).toMatchObject({ id: '501', status: 'COMPLETED' })

    // Verify task is updated in state
    const getResponse = await fetch('http://localhost:3000/api/tasks/501')
    const task: unknown = await getResponse.json()
    expect(task).toMatchObject({ status: 'COMPLETED' })
  })

  it('creates log and syncs immediately', async () => {
    setMockCurrentUser('2')
    const logCount = mockState.taskLogs.length

    const response = await fetch('http://localhost:3000/api/tasks/501/logs', {
      method: 'POST',
      headers: csrfHeaders,
      body: JSON.stringify({ progressPercent: 100, content: '任务完成' }),
    })

    expect(response.status).toBe(201)
    expect(mockState.taskLogs).toHaveLength(logCount + 1)
  })

  it('deletes log and syncs immediately', async () => {
    setMockCurrentUser('3')
    const logCount = mockState.taskLogs.length

    const response = await fetch('http://localhost:3000/api/tasks/501/logs/801', {
      method: 'DELETE',
      headers: csrfHeaders,
    })

    expect(response.status).toBe(204)
    expect(mockState.taskLogs).toHaveLength(logCount - 1)
  })
})
