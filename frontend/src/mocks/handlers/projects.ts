import { http, HttpResponse } from 'msw'

import {
  canManageProject,
  canViewProject,
  currentUser,
  isMember,
  projectById,
  projectView,
  userById,
} from '../selectors'
import { mockState, removeRecord, replaceRecord } from '../store'
import type { Priority, ProjectStatus } from '../types'
import {
  conflict,
  forbidden,
  hasFields,
  invalid,
  isDate,
  nextId,
  notFound,
  now,
  paginated,
  readJson,
  unauthorized,
  validateBooleanQuery,
  validateEnumQuery,
} from '../utils'

type CreateProjectBody = {
  readonly name: string
  readonly description?: string | null
  readonly status: ProjectStatus
  readonly priority: Priority
  readonly startDate?: string | null
  readonly endDate?: string | null
  readonly ownerId?: string
}
type UpdateProjectBody = {
  readonly name: string
  readonly description: string | null
  readonly status: ProjectStatus
  readonly priority: Priority
  readonly startDate: string | null
  readonly endDate: string | null
}
type TransferOwnerBody = { readonly ownerId: string }

export const projectHandlers = [
  http.get('/api/projects', ({ request }) => {
    const actor = currentUser()
    if (!actor) return unauthorized(request)
    const queryError = validateBooleanQuery(request, 'archived')
      ?? validateEnumQuery(request, 'status', ['NOT_STARTED', 'IN_PROGRESS', 'COMPLETED'])
    if (queryError) return queryError
    const url = new URL(request.url)
    const query = url.searchParams.get('q')?.toLowerCase()
    const status = url.searchParams.get('status')
    const ownerId = url.searchParams.get('ownerId')
    const archivedValue = url.searchParams.get('archived')
    const archived = archivedValue === null ? false : archivedValue === 'true'
    const projects = mockState.projects
      .filter((project) => canViewProject(project, actor))
      .filter((project) => (project.archivedAt !== null) === archived)
      .filter((project) => !status || project.status === status)
      .filter((project) => !ownerId || project.ownerId === ownerId)
      .filter((project) => !query || project.name.toLowerCase().includes(query))
      .map(projectView)
    return paginated(projects, request, {
        createdAt: (project) => project.createdAt,
        updatedAt: (project) => project.updatedAt,
        name: (project) => project.name,
        startDate: (project) => project.startDate,
        endDate: (project) => project.endDate,
        priority: (project) => project.priority,
      })
  }),

  http.post<never, CreateProjectBody>('/api/projects', async ({ request }) => {
    const actor = currentUser()
    if (!actor) return unauthorized(request)
    const body = await readJson<CreateProjectBody>(request)
    if (body instanceof Response) return body
    if (!validProjectFields(body)) return invalid(request, '项目字段不完整或不符合要求')
    if (body.startDate && body.endDate && body.endDate < body.startDate) {
      return invalid(request, '项目结束日期不能早于开始日期')
    }
    const ownerId = actor.systemRole === 'ADMIN' ? body.ownerId : actor.id
    if (!ownerId) return invalid(request, '管理员创建项目时必须指定 USER 作为负责人')
    const owner = userById(ownerId)
    if (!owner || !owner.active || owner.systemRole !== 'USER') {
      return invalid(request, '项目负责人必须是有效的 USER')
    }
    const timestamp = now()
    const project = {
      id: nextId('project'), ownerId, name: body.name, description: body.description ?? null,
      status: body.status, priority: body.priority, startDate: body.startDate ?? null,
      endDate: body.endDate ?? null, archivedAt: null, createdAt: timestamp, updatedAt: timestamp,
    }
    mockState.projects.push(project)
    mockState.members.push({ projectId: project.id, userId: ownerId, joinedAt: timestamp })
    return HttpResponse.json(projectView(project), { status: 201 })
  }),

  http.get<{ projectId: string }>('/api/projects/:projectId', ({ params, request }) => {
    const actor = currentUser()
    if (!actor) return unauthorized(request)
    const project = projectById(params.projectId)
    if (!project || !canViewProject(project, actor)) return notFound(request)
    return HttpResponse.json(projectView(project))
  }),

  http.put<{ projectId: string }, UpdateProjectBody>(
    '/api/projects/:projectId',
    async ({ params, request }) => {
      const actor = currentUser()
      if (!actor) return unauthorized(request)
      const project = projectById(params.projectId)
      if (!project) return notFound(request)
      if (!canManageProject(project, actor)) return forbidden(request)
      if (project.archivedAt) return conflict(request, 'PROJECT_ARCHIVED', '归档项目不能修改')
      const body = await readJson<UpdateProjectBody>(request)
      if (body instanceof Response) return body
      if (!hasFields(body, ['name', 'description', 'status', 'priority', 'startDate', 'endDate'])) {
        return invalid(request, 'PUT 必须包含全部可编辑字段')
      }
      if (!validProjectFields(body)) return invalid(request, '项目字段不完整或不符合要求')
      if (body.startDate && body.endDate && body.endDate < body.startDate) {
        return invalid(request, '项目结束日期不能早于开始日期')
      }
      const updated = {
        ...project, name: body.name, description: body.description, status: body.status,
        priority: body.priority, startDate: body.startDate, endDate: body.endDate, updatedAt: now(),
      }
      replaceRecord(mockState.projects, updated)
      return HttpResponse.json(projectView(updated))
    },
  ),

  http.put<{ projectId: string }, TransferOwnerBody>(
    '/api/projects/:projectId/owner',
    async ({ params, request }) => {
      const actor = currentUser()
      if (!actor) return unauthorized(request)
      const project = projectById(params.projectId)
      if (!project) return notFound(request)
      if (!canManageProject(project, actor)) return forbidden(request)
      if (project.archivedAt) return conflict(request, 'PROJECT_ARCHIVED', '归档项目不能修改')
      const body = await readJson<TransferOwnerBody>(request)
      if (body instanceof Response) return body
      const owner = userById(body.ownerId)
      if (!owner || !owner.active || owner.systemRole !== 'USER') {
        return invalid(request, '新负责人必须是有效的 USER')
      }
      if (!isMember(project.id, owner.id)) {
        mockState.members.push({ projectId: project.id, userId: owner.id, joinedAt: now() })
      }
      const updated = { ...project, ownerId: owner.id, updatedAt: now() }
      replaceRecord(mockState.projects, updated)
      return HttpResponse.json(projectView(updated))
    },
  ),

  http.put<{ projectId: string }>('/api/projects/:projectId/archive', ({ params, request }) => {
    const actor = currentUser()
    if (!actor) return unauthorized(request)
    const project = projectById(params.projectId)
    if (!project) return notFound(request)
    if (!canManageProject(project, actor)) return forbidden(request)
    const timestamp = now()
    const updated = { ...project, archivedAt: timestamp, updatedAt: timestamp }
    replaceRecord(mockState.projects, updated)
    return HttpResponse.json(projectView(updated))
  }),

  http.delete<{ projectId: string }>(
    '/api/projects/:projectId/archive',
    ({ params, request }) => {
      const actor = currentUser()
      if (!actor) return unauthorized(request)
      const project = projectById(params.projectId)
      if (!project) return notFound(request)
      if (!canManageProject(project, actor)) return forbidden(request)
      const updated = { ...project, archivedAt: null, updatedAt: now() }
      replaceRecord(mockState.projects, updated)
      return HttpResponse.json(projectView(updated))
    },
  ),

  http.delete<{ projectId: string }>('/api/projects/:projectId', ({ params, request }) => {
    const actor = currentUser()
    if (!actor) return unauthorized(request)
    const project = projectById(params.projectId)
    if (!project) return notFound(request)
    if (!canManageProject(project, actor)) return forbidden(request)
    const inUse = mockState.tasks.some((task) => task.projectId === project.id)
      || mockState.summaries.some((summary) => summary.projectId === project.id)
    if (inUse) return conflict(request, 'RESOURCE_IN_USE', '项目仍包含任务或总结')
    removeRecord(mockState.projects, project.id)
    mockState.members = mockState.members.filter((member) => member.projectId !== project.id)
    mockState.invitations = mockState.invitations.filter(
      (invitation) => invitation.projectId !== project.id,
    )
    return new HttpResponse(null, { status: 204 })
  }),
]

function validProjectFields(body: CreateProjectBody | UpdateProjectBody): boolean {
  return typeof body.name === 'string' && body.name.trim().length > 0 && body.name.length <= 100
    && (body.description == null
      || (typeof body.description === 'string' && body.description.length <= 2000))
    && ['NOT_STARTED', 'IN_PROGRESS', 'COMPLETED'].includes(body.status)
    && ['LOW', 'MEDIUM', 'HIGH'].includes(body.priority)
    && (body.startDate == null || isDate(body.startDate))
    && (body.endDate == null || isDate(body.endDate))
}
