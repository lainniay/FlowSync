import { http, HttpResponse } from 'msw'

import {
  canViewProject,
  currentUser,
  isMember,
  isProjectOwner,
  projectById,
  summaryView,
  taskById,
} from '../selectors'
import { mockState, removeRecord, replaceRecord } from '../store'
import type { SummaryType } from '../types'
import {
  conflict, forbidden, invalid, nextId, notFound, now, paginated, readJson, unauthorized,
  validateEnumQuery,
} from '../utils'

type CreateSummaryBody = {
  readonly projectId: string
  readonly taskId?: string | null
  readonly type: SummaryType
  readonly content: string
}
type UpdateSummaryBody = { readonly type: SummaryType; readonly content: string }

export const summaryHandlers = [
  http.get('/api/summaries', ({ request }) => {
    const actor = currentUser()
    if (!actor) return unauthorized(request)
    const queryError = validateEnumQuery(request, 'type', ['STAGE', 'FINAL'])
    if (queryError) return queryError
    const url = new URL(request.url)
    const projectId = url.searchParams.get('projectId')
    const taskId = url.searchParams.get('taskId')
    const type = url.searchParams.get('type')
    const createdBy = url.searchParams.get('createdBy')
    const summaries = mockState.summaries
      .filter((summary) => {
        const project = projectById(summary.projectId)
        return project ? canViewProject(project, actor) : false
      })
      .filter((summary) => !projectId || summary.projectId === projectId)
      .filter((summary) => taskId === null || (taskId === 'none' ? summary.taskId === null : summary.taskId === taskId))
      .filter((summary) => !type || summary.type === type)
      .filter((summary) => !createdBy || summary.createdById === createdBy)
      .map(summaryView)
    return paginated(summaries, request, {
        createdAt: (summary) => summary.createdAt,
        updatedAt: (summary) => summary.updatedAt,
        type: (summary) => summary.type,
      })
  }),

  http.post<never, CreateSummaryBody>('/api/summaries', async ({ request }) => {
    const actor = currentUser()
    if (!actor) return unauthorized(request)
    const body = await readJson<CreateSummaryBody>(request)
    if (body instanceof Response) return body
    const project = projectById(body.projectId)
    if (!project) return notFound(request)
    if (!isMember(project.id, actor.id)) return forbidden(request)
    if (project.archivedAt) return conflict(request, 'PROJECT_ARCHIVED', '归档项目不能创建总结')
    if (typeof body.content !== 'string' || !body.content.trim() || body.content.length > 10_000
      || !['STAGE', 'FINAL'].includes(body.type)) return invalid(request, '总结字段不符合要求')
    const taskId = body.taskId ?? null
    if (taskId) {
      const task = taskById(taskId)
      if (!task || task.projectId !== project.id) return invalid(request, '任务不属于当前项目')
    }
    const timestamp = now()
    const summary = {
      id: nextId('summary'), projectId: project.id, taskId, createdById: actor.id,
      type: body.type, content: body.content, createdAt: timestamp, updatedAt: timestamp,
    }
    mockState.summaries.push(summary)
    return HttpResponse.json(summaryView(summary), { status: 201 })
  }),

  http.get<{ summaryId: string }>('/api/summaries/:summaryId', ({ params, request }) => {
    const actor = currentUser()
    if (!actor) return unauthorized(request)
    const summary = mockState.summaries.find((candidate) => candidate.id === params.summaryId)
    const project = summary ? projectById(summary.projectId) : undefined
    if (!summary || !project || !canViewProject(project, actor)) return notFound(request)
    return HttpResponse.json(summaryView(summary))
  }),

  http.put<{ summaryId: string }, UpdateSummaryBody>(
    '/api/summaries/:summaryId',
    async ({ params, request }) => {
      const actor = currentUser()
      if (!actor) return unauthorized(request)
      const summary = mockState.summaries.find((candidate) => candidate.id === params.summaryId)
      const project = summary ? projectById(summary.projectId) : undefined
      if (!summary || !project) return notFound(request)
      if (summary.createdById !== actor.id && !isProjectOwner(project, actor)) return forbidden(request)
      if (project.archivedAt) return conflict(request, 'PROJECT_ARCHIVED', '归档项目不能修改总结')
      const body = await readJson<UpdateSummaryBody>(request)
      if (body instanceof Response) return body
      if (typeof body.content !== 'string' || !body.content.trim() || body.content.length > 10_000
        || !['STAGE', 'FINAL'].includes(body.type)) return invalid(request, '总结字段不符合要求')
      const updated = { ...summary, type: body.type, content: body.content, updatedAt: now() }
      replaceRecord(mockState.summaries, updated)
      return HttpResponse.json(summaryView(updated))
    },
  ),

  http.delete<{ summaryId: string }>('/api/summaries/:summaryId', ({ params, request }) => {
    const actor = currentUser()
    if (!actor) return unauthorized(request)
    const summary = mockState.summaries.find((candidate) => candidate.id === params.summaryId)
    const project = summary ? projectById(summary.projectId) : undefined
    if (!summary || !project) return notFound(request)
    if (summary.createdById !== actor.id && !isProjectOwner(project, actor)) return forbidden(request)
    if (project.archivedAt) return conflict(request, 'PROJECT_ARCHIVED', '归档项目不能删除总结')
    removeRecord(mockState.summaries, summary.id)
    return new HttpResponse(null, { status: 204 })
  }),
]
