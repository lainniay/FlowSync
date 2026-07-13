import { http, HttpResponse } from 'msw'

import {
  canViewProject,
  currentUser,
  isMember,
  isProjectOwner,
  projectById,
  taskById,
  taskView,
  userById,
} from '../selectors'
import { mockState, removeRecord, replaceRecord } from '../store'
import type { Priority, TaskStatus } from '../types'
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
  validateDateQuery,
  validateEnumQuery,
} from '../utils'

type CreateTaskBody = {
  readonly projectId: string
  readonly parentId?: string | null
  readonly title: string
  readonly description?: string | null
  readonly assigneeId?: string | null
  readonly status: TaskStatus
  readonly priority: Priority
  readonly dueDate?: string | null
}
type UpdateTaskBody = {
  readonly parentId: string | null
  readonly title: string
  readonly description: string | null
  readonly assigneeId: string | null
  readonly status: TaskStatus
  readonly priority: Priority
  readonly dueDate: string | null
}
type TaskStatusBody = { readonly status: TaskStatus }

export const taskHandlers = [
  http.get('/api/tasks', ({ request }) => {
    const actor = currentUser()
    if (!actor) return unauthorized(request)
    const queryError = validateEnumQuery(
      request,
      'status',
      ['NOT_STARTED', 'IN_PROGRESS', 'BLOCKED', 'COMPLETED', 'CANCELLED'],
    ) ?? validateEnumQuery(request, 'priority', ['LOW', 'MEDIUM', 'HIGH'])
      ?? validateDateQuery(request, 'dueBefore')
      ?? validateDateQuery(request, 'dueAfter')
    if (queryError) return queryError
    const url = new URL(request.url)
    const projectId = url.searchParams.get('projectId')
    const assigneeId = url.searchParams.get('assigneeId')
    const status = url.searchParams.get('status')
    const priority = url.searchParams.get('priority')
    const parentId = url.searchParams.get('parentId')
    const dueBefore = url.searchParams.get('dueBefore')
    const dueAfter = url.searchParams.get('dueAfter')
    const query = url.searchParams.get('q')?.toLowerCase()
    const tasks = mockState.tasks
      .filter((task) => {
        const project = projectById(task.projectId)
        return project ? canViewProject(project, actor) : false
      })
      .filter((task) => !projectId || task.projectId === projectId)
      .filter((task) => !assigneeId || task.assigneeId === assigneeId)
      .filter((task) => !status || task.status === status)
      .filter((task) => !priority || task.priority === priority)
      .filter((task) => !parentId || task.parentId === parentId)
      .filter((task) => !dueBefore || (task.dueDate !== null && task.dueDate <= dueBefore))
      .filter((task) => !dueAfter || (task.dueDate !== null && task.dueDate >= dueAfter))
      .filter((task) => !query || `${task.title} ${task.description ?? ''}`.toLowerCase().includes(query))
      .map(taskView)
    return paginated(tasks, request, {
        createdAt: (task) => task.createdAt,
        updatedAt: (task) => task.updatedAt,
        title: (task) => task.title,
        dueDate: (task) => task.dueDate,
        priority: (task) => task.priority,
        status: (task) => task.status,
      })
  }),

  http.post<never, CreateTaskBody>('/api/tasks', async ({ request }) => {
    const actor = currentUser()
    if (!actor) return unauthorized(request)
    const body = await readJson<CreateTaskBody>(request)
    if (body instanceof Response) return body
    if (!validTaskFields(body)) return invalid(request, '任务字段不完整或不符合要求')
    const project = projectById(body.projectId)
    if (!project) return notFound(request)
    if (!isProjectOwner(project, actor)) return forbidden(request)
    if (project.archivedAt) return conflict(request, 'PROJECT_ARCHIVED', '归档项目不能创建任务')
    const validation = validateTaskRelations(
      body.projectId, body.parentId ?? null, body.assigneeId ?? null, body.dueDate ?? null,
    )
    if (validation) return invalid(request, validation)
    const timestamp = now()
    const task = {
      id: nextId('task'), projectId: body.projectId, parentId: body.parentId ?? null,
      assigneeId: body.assigneeId ?? null, creatorId: actor.id, title: body.title,
      description: body.description ?? null, status: body.status, priority: body.priority,
      dueDate: body.dueDate ?? null, createdAt: timestamp, updatedAt: timestamp,
    }
    mockState.tasks.push(task)
    return HttpResponse.json(taskView(task), { status: 201 })
  }),

  http.get<{ taskId: string }>('/api/tasks/:taskId', ({ params, request }) => {
    const actor = currentUser()
    if (!actor) return unauthorized(request)
    const task = taskById(params.taskId)
    const project = task ? projectById(task.projectId) : undefined
    if (!task || !project || !canViewProject(project, actor)) return notFound(request)
    return HttpResponse.json(taskView(task))
  }),

  http.put<{ taskId: string }, UpdateTaskBody>(
    '/api/tasks/:taskId',
    async ({ params, request }) => {
      const actor = currentUser()
      if (!actor) return unauthorized(request)
      const task = taskById(params.taskId)
      const project = task ? projectById(task.projectId) : undefined
      if (!task || !project) return notFound(request)
      if (!isProjectOwner(project, actor)) return forbidden(request)
      if (project.archivedAt) return conflict(request, 'PROJECT_ARCHIVED', '归档项目不能修改任务')
      const body = await readJson<UpdateTaskBody>(request)
      if (body instanceof Response) return body
      if (!hasFields(
        body, ['parentId', 'title', 'description', 'assigneeId', 'status', 'priority', 'dueDate'],
      )) return invalid(request, 'PUT 必须包含全部可编辑字段')
      if (!validTaskFields(body)) return invalid(request, '任务字段不完整或不符合要求')
      const validation = validateTaskRelations(
        task.projectId, body.parentId, body.assigneeId, body.dueDate,
      )
      if (validation) return invalid(request, validation)
      if (body.parentId === task.id) return invalid(request, '任务不能将自己设为父任务')
      const updated = {
        ...task, parentId: body.parentId, title: body.title, description: body.description,
        assigneeId: body.assigneeId, status: body.status, priority: body.priority,
        dueDate: body.dueDate, updatedAt: now(),
      }
      replaceRecord(mockState.tasks, updated)
      return HttpResponse.json(taskView(updated))
    },
  ),

  http.put<{ taskId: string }, TaskStatusBody>(
    '/api/tasks/:taskId/status',
    async ({ params, request }) => {
      const actor = currentUser()
      if (!actor) return unauthorized(request)
      const task = taskById(params.taskId)
      const project = task ? projectById(task.projectId) : undefined
      if (!task || !project) return notFound(request)
      const allowed = isProjectOwner(project, actor) || task.assigneeId === actor.id
      if (!allowed) return forbidden(request)
      if (project.archivedAt) return conflict(request, 'PROJECT_ARCHIVED', '归档项目不能修改任务')
      const body = await readJson<TaskStatusBody>(request)
      if (body instanceof Response) return body
      if (!['NOT_STARTED', 'IN_PROGRESS', 'BLOCKED', 'COMPLETED', 'CANCELLED'].includes(body.status)) {
        return invalid(request, '任务状态不符合要求')
      }
      const updated = { ...task, status: body.status, updatedAt: now() }
      replaceRecord(mockState.tasks, updated)
      return HttpResponse.json(taskView(updated))
    },
  ),

  http.delete<{ taskId: string }>('/api/tasks/:taskId', ({ params, request }) => {
    const actor = currentUser()
    if (!actor) return unauthorized(request)
    const task = taskById(params.taskId)
    const project = task ? projectById(task.projectId) : undefined
    if (!task || !project) return notFound(request)
    if (!isProjectOwner(project, actor)) return forbidden(request)
    if (project.archivedAt) return conflict(request, 'PROJECT_ARCHIVED', '归档项目不能删除任务')
    const inUse = mockState.tasks.some((candidate) => candidate.parentId === task.id)
      || mockState.taskLogs.some((log) => log.taskId === task.id)
      || mockState.summaries.some((summary) => summary.taskId === task.id)
    if (inUse) return conflict(request, 'RESOURCE_IN_USE', '任务仍有子任务、进度或总结引用')
    removeRecord(mockState.tasks, task.id)
    return new HttpResponse(null, { status: 204 })
  }),
]

function validateTaskRelations(
  projectId: string,
  parentId: string | null,
  assigneeId: string | null,
  dueDate: string | null,
): string | null {
  const project = projectById(projectId)
  if (!project) return '项目不存在'
  if (parentId) {
    const parent = taskById(parentId)
    if (!parent || parent.projectId !== projectId) return '父任务必须属于同一项目'
  }
  if (assigneeId && (!isMember(projectId, assigneeId) || !userById(assigneeId)?.active)) {
    return '任务负责人必须是有效的项目成员'
  }
  if (dueDate && ((project.startDate && dueDate < project.startDate)
    || (project.endDate && dueDate > project.endDate))) return '任务截止日期必须在项目日期范围内'
  return null
}

function validTaskFields(body: UpdateTaskBody | CreateTaskBody): boolean {
  return typeof body.title === 'string' && body.title.trim().length > 0 && body.title.length <= 100
    && (body.description == null
      || (typeof body.description === 'string' && body.description.length <= 5000))
    && (body.parentId === undefined || body.parentId === null || typeof body.parentId === 'string')
    && (body.assigneeId === undefined
      || body.assigneeId === null || typeof body.assigneeId === 'string')
    && ['NOT_STARTED', 'IN_PROGRESS', 'BLOCKED', 'COMPLETED', 'CANCELLED'].includes(body.status)
    && ['LOW', 'MEDIUM', 'HIGH'].includes(body.priority)
    && (body.dueDate === undefined || body.dueDate === null || isDate(body.dueDate))
}
