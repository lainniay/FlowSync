import { http, HttpResponse } from 'msw'

import {
  canViewProject,
  currentUser,
  isProjectOwner,
  projectById,
  taskById,
  taskLogView,
} from '../selectors'
import { mockState, removeRecord, replaceRecord } from '../store'
import {
  conflict, forbidden, invalid, nextId, notFound, now, paginated, readJson, unauthorized,
} from '../utils'

type TaskLogBody = { readonly progressPercent: number; readonly content: string }

export const taskLogHandlers = [
  http.get<{ taskId: string }>('/api/tasks/:taskId/logs', ({ params, request }) => {
    const actor = currentUser()
    if (!actor) return unauthorized(request)
    const task = taskById(params.taskId)
    const project = task ? projectById(task.projectId) : undefined
    if (!task || !project || !canViewProject(project, actor)) return notFound(request)
    const logs = mockState.taskLogs.filter((log) => log.taskId === task.id).map(taskLogView)
    return paginated(logs, request, {
        createdAt: (log) => log.createdAt,
        progressPercent: (log) => log.progressPercent,
      })
  }),

  http.post<{ taskId: string }, TaskLogBody>(
    '/api/tasks/:taskId/logs',
    async ({ params, request }) => {
      const actor = currentUser()
      if (!actor) return unauthorized(request)
      const task = taskById(params.taskId)
      const project = task ? projectById(task.projectId) : undefined
      if (!task || !project) return notFound(request)
      if (!isProjectOwner(project, actor) && task.assigneeId !== actor.id) return forbidden(request)
      if (project.archivedAt) return conflict(request, 'PROJECT_ARCHIVED', '归档项目不能添加进度')
      const body = await readJson<TaskLogBody>(request)
      if (body instanceof Response) return body
      if (!Number.isInteger(body.progressPercent) || body.progressPercent < 0
        || body.progressPercent > 100 || typeof body.content !== 'string'
        || !body.content.trim() || body.content.length > 1000) {
        return invalid(request, '进度必须为 0..100，且说明不能为空')
      }
      const createdAt = now()
      const log = {
        id: nextId('taskLog'), taskId: task.id, operatorId: actor.id,
        progressPercent: body.progressPercent, content: body.content, createdAt,
      }
      mockState.taskLogs.push(log)
      replaceRecord(mockState.tasks, { ...task, updatedAt: createdAt })
      return HttpResponse.json(taskLogView(log), { status: 201 })
    },
  ),

  http.delete<{ taskId: string; logId: string }>(
    '/api/tasks/:taskId/logs/:logId',
    ({ params, request }) => {
      const actor = currentUser()
      if (!actor) return unauthorized(request)
      const task = taskById(params.taskId)
      const project = task ? projectById(task.projectId) : undefined
      const log = mockState.taskLogs.find(
        (candidate) => candidate.id === params.logId && candidate.taskId === params.taskId,
      )
      if (!task || !project || !log) return notFound(request)
      if (!isProjectOwner(project, actor) && log.operatorId !== actor.id) return forbidden(request)
      if (project.archivedAt) return conflict(request, 'PROJECT_ARCHIVED', '归档项目不能删除进度')
      removeRecord(mockState.taskLogs, log.id)
      return new HttpResponse(null, { status: 204 })
    },
  ),
]
