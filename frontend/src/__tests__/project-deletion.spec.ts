import { setupServer } from 'msw/node'
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'

import { handlers } from '../mocks/handlers'
import { mockState, resetMockState, setMockCurrentUser } from '../mocks/store'

const server = setupServer(...handlers)
const headers = { 'X-CSRF-TOKEN': 'mock-csrf-token' }

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
beforeEach(() => resetMockState())
afterAll(() => server.close())

describe('project deletion', () => {
  it('rejects deleting an active project without changing its data', async () => {
    // Given
    setMockCurrentUser('2')
    const counts = {
      projects: mockState.projects.length, members: mockState.members.length,
      invitations: mockState.invitations.length, tasks: mockState.tasks.length,
      taskLogs: mockState.taskLogs.length, summaries: mockState.summaries.length,
    }

    // When
    const response = await fetch('http://localhost:3000/api/projects/101', {
      method: 'DELETE', headers,
    })
    const body: unknown = await response.json()

    // Then
    expect(response.status).toBe(409)
    expect(response.headers.get('content-type')).toContain('application/problem+json')
    expect(body).toEqual(expect.objectContaining({ code: 'PROJECT_NOT_ARCHIVED' }))
    expect({
      projects: mockState.projects.length, members: mockState.members.length,
      invitations: mockState.invitations.length, tasks: mockState.tasks.length,
      taskLogs: mockState.taskLogs.length, summaries: mockState.summaries.length,
    }).toEqual(counts)
  })

  it('deletes an archived project and all of its related records', async () => {
    // Given
    setMockCurrentUser('2')
    mockState.projects = mockState.projects.map((project) =>
      project.id === '101' ? { ...project, archivedAt: '2026-07-13T12:00:00Z' } : project,
    )
    const taskIds = new Set(
      mockState.tasks.filter((task) => task.projectId === '101').map((task) => task.id),
    )

    // When
    const response = await fetch('http://localhost:3000/api/projects/101', {
      method: 'DELETE', headers,
    })

    // Then
    expect({
      status: response.status,
      project: mockState.projects.some((project) => project.id === '101'),
      members: mockState.members.some((member) => member.projectId === '101'),
      invitations: mockState.invitations.some((invitation) => invitation.projectId === '101'),
      tasks: mockState.tasks.some((task) => task.projectId === '101'),
      taskLogs: mockState.taskLogs.some((log) => taskIds.has(log.taskId)),
      summaries: mockState.summaries.some((summary) => summary.projectId === '101'),
    }).toEqual({
      status: 204, project: false, members: false, invitations: false,
      tasks: false, taskLogs: false, summaries: false,
    })
  })
})
