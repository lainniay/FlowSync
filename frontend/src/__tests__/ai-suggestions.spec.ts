import { setupServer } from 'msw/node'
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'

import { handlers } from '../mocks/handlers'
import { mockState, resetMockState, setMockCurrentUser } from '../mocks/store'

const server = setupServer(...handlers)
const headers = { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': 'mock-csrf-token' }

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
beforeEach(() => resetMockState())
afterAll(() => server.close())

describe('AI task suggestions', () => {
  it('lets the current assignee request a TaskLog draft without saving it', async () => {
    // Given
    setMockCurrentUser('3')
    const logCount = mockState.taskLogs.length

    // When
    const response = await fetch('http://localhost:3000/api/ai/task-suggestions', {
      method: 'POST', headers,
      body: JSON.stringify({ taskId: '501', focus: '根据实际进展生成 TaskLog 草稿' }),
    })
    const body: unknown = await response.json()

    // Then
    expect(response.status).toBe(200)
    expect(response.headers.get('content-type')).toContain('application/json')
    expect(body).toEqual(expect.objectContaining({
      suggestion: expect.any(String), generatedAt: expect.any(String),
    }))
    expect(mockState.taskLogs).toHaveLength(logCount)
  })

  it('forbids an unassigned member from requesting a task suggestion', async () => {
    // Given
    setMockCurrentUser('4')
    mockState.members.push({
      projectId: '101', userId: '4', joinedAt: '2026-07-13T12:00:00Z',
    })

    // When
    const response = await fetch('http://localhost:3000/api/ai/task-suggestions', {
      method: 'POST', headers, body: JSON.stringify({ taskId: '501', focus: '生成日志草稿' }),
    })
    const body: unknown = await response.json()

    // Then
    expect(response.status).toBe(403)
    expect(response.headers.get('content-type')).toContain('application/problem+json')
    expect(body).toEqual(expect.objectContaining({ code: 'FORBIDDEN' }))
  })
})
